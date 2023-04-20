package com.e_gineering.maven.gitflowhelper;

import com.e_gineering.maven.gitflowhelper.properties.PropertyResolver;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.Maven;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.model.io.ModelWriter;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "other-branch-version")
public class OtherBranchVersionExtension extends AbstractBranchDetectingExtension {

    private static final String POM_GITFLOW_MASSAGED_XML = "pom-gitflow-massaged.xml";
    private static final String POM_XML = "pom.xml";
    private static final String GITFLOW_SKIP_EXTENSION = "gitflow.skip.extension";

    @Requirement(role = ModelWriter.class)
    ModelWriter modelWriter;

    @Requirement(role = ModelReader.class)
    ModelReader modelReader;

    @Requirement(role = Maven.class)
    Maven maven;

    @Requirement(role = LegacySupport.class)
    LegacySupport legacySupport;
    
    private static final int ORIGINAL_VERSION_IDX = 0;
    private static final int ADJUSTED_VERSION_IDX = 1;
    
    @Override
    public void afterProjectsRead(final MavenSession session) throws MavenExecutionException {
        super.afterProjectsRead(session);

        if (session.getUserProperties().containsKey(GITFLOW_SKIP_EXTENSION)) {
            return;
        }

        if (shouldUpdateProjectModels(session)) {
            logger.debug("Updating projects " + session.getProjects());
            updateProjectModels(session);
        }

        logger.info("Continuing execution....");
    }

    /**
     * Determines if the the project modules need to be updated. This requires all the following conditions to be met:
     *
     * <ul>
     *     <li>Plugin is configured for this project</li>
     *     <li>The git branch could be determined</li>
     *     <li>The git branch is of type {@link GitBranchType#OTHER}</li>
     *     <li>The git branch name matches the configured {@link #otherDeployBranchPattern}</li>
     *     <li>The top-level project is not already updated (identified by checking the name of the pom file)</li>
     * </ul>
     * @param session
     * @return <tt>true</tt> if all documented conditions are met, <tt>false otherwise</tt>
     */
    private boolean shouldUpdateProjectModels(MavenSession session) {
        boolean rewrite = false;
        if (pluginFound) {
            logger.debug("other-branch-version extension active.");
            if (branchInfo != null) {
                logger.debug("other-branch-version extension on git branch: " + branchInfo);
                if (branchInfo.getType().equals(GitBranchType.OTHER)) {
                    String otherBranchesToDeploy = PropertyResolver.resolveValue(otherDeployBranchPattern, session.getTopLevelProject().getProperties(), systemEnvVars);
                    logger.debug("OTHER branch detected. Testing against pattern: `" + otherBranchesToDeploy + "`");
                    if (!"".equals(otherBranchesToDeploy) && branchInfo.getName().matches(otherBranchesToDeploy)) {
                        final MavenProject topLevelProjectInsideReactor = session.getTopLevelProject();
                        if (!POM_GITFLOW_MASSAGED_XML.equals(topLevelProjectInsideReactor.getFile().getName())) {
                            rewrite = true;
                        } else {
                            logger.debug("Project " + topLevelProjectInsideReactor + " already rewritten, skipping update.");
                        }
                    }
                }
            }
        }
        return rewrite;
    }

    /**
     * Updates all projects contained within the supplied {@link MavenSession}, and update:
     *
     * <ul>
     *     <li>The version of each module to a rewritten version</li>
     *     <li>The version of a dependency towards another module inside the same multi-module project towards the rewritten version</li>
     *     <li>Write a massaged pom file that is uploaded to repositories instead of the original pom.xml</li>
     * </ul>
     * @param session References all {@link MavenProject}s that need to be rewritten.
     * @throws MavenExecutionException thrown when the massaged pom file could not be written
     */
    private void updateProjectModels(MavenSession session) throws MavenExecutionException {
        // Build a cross-walk map of projects to versions.
        final Map<MavenProject, String[]> adjustedVersions = calculateVersionMap(session);

        // Once we have that populated, refilter the adjusted projects models
        // updating dependencies on both the in-memory effective model, and the original pom.xml model.
        for (MavenProject project : session.getProjects()) {
            final String[] adjusted = adjustedVersions.get(project);

            // If the parent artifact is one of the projects with an adjusted version..
            final Artifact parentArtifact = project.getParentArtifact();
            if (parentArtifact != null) {
                for (Map.Entry<MavenProject, String[]> adjustedProjects : adjustedVersions.entrySet()) {
                    if (isProjectOfReplacedArtifactVersion(adjustedProjects.getKey(), adjustedProjects.getValue()[ORIGINAL_VERSION_IDX], parentArtifact)) {
                        logger.info("Updating project: " + project + " parent artifact: " + parentArtifact);
                        parentArtifact.setVersion(adjustedProjects.getValue()[ADJUSTED_VERSION_IDX]);
                        parentArtifact.setVersionRange(VersionRange.createFromVersion(adjustedProjects.getValue()[ADJUSTED_VERSION_IDX]));
                        logger.info("    Now: " + parentArtifact);
                    }
                }
            }

            // Update the in-reactor active and effective model.
            logger.debug("Updating in-reactor model");
            updateProjectModel(project, project.getModel(), adjusted, adjustedVersions);

            // The original model is an un-effective Model of the original pom.xml
            logger.debug("Updating original model");
            updateProjectModel(project, project.getOriginalModel(), adjusted, adjustedVersions);

            // Finally ensure that the massaged pom file is written and attached to the project.
            createMassagedPom(project);
        }
    }

    /**
     * Calculates a version map that holds an original and rewritten version for <strong>all</strong> {@link MavenProject}s related to
     * the multi-module project inside the {@link MavenSession}. Note that if the {@link MavenSession} only references a subtree of the
     * multi-module project, it will still produce a map referencing the versions of all modules involved.
     *
     * @param session The active {@link MavenSession}
     * @return A map of MavenProject towards two versions: current and rewritten.
     */
    private Map<MavenProject, String[]> calculateVersionMap(MavenSession session) {
        final MavenProject topLevelProjectInsideReactor = session.getTopLevelProject();
        final Map<MavenProject, String[]> adjustedVersions = new HashMap<>();
        // Update the versions on all the project models (in-memory, effective models).
        for (MavenProject project : session.getProjects()) {
            // Resolve the originalVersion, in case it has properties.
            String originalVersion = PropertyResolver.resolveValue(project.getVersion(), project.getProperties(), systemEnvVars);
            String newVersion = getAsBranchSnapshotVersion(originalVersion, branchInfo.getName());
            adjustedVersions.put(project, new String[]{originalVersion, newVersion});
            logger.info("Updating project " + project.getGroupId() + ":" + project.getArtifactId() + ":" + originalVersion + " to: " + newVersion);

            // Update the default artifact information for the project in the reactor.
            // This is a Non-Model type, so we'll update these here.
            // This is used to generate all the other artifacts in the project.
            if (project.getArtifact() != null) {
                project.getArtifact().setBaseVersion(newVersion);
                project.getArtifact().setVersion(newVersion);
                project.getArtifact().setVersionRange(VersionRange.createFromVersion(newVersion));
            }
        }

        final MavenProject topLevelProject = findTopLevelProject(topLevelProjectInsideReactor);
        logger.info("Top level project: " + topLevelProjectInsideReactor.getGroupId() + ":" + topLevelProjectInsideReactor.getArtifactId());
        if (!topLevelProjectInsideReactor.equals(topLevelProject)) {
            // When only the partial tree of modules is being built, not all modules could be indexed
            // into the cross-walk map. In order to deal with dependencies to modules outside the reactor,
            // Another Maven execution is performed on the 'actual' top level project. This allows the
            // cross-walk map to be completed with the modules that *are* part of the multi-module project,
            // but are currently not part of the reactor.

            logger.info("Found top level project, but outside reactor: " + topLevelProject.getGroupId() + ":" + topLevelProject.getArtifactId() + " (" + topLevelProject.getFile() + ")");

            // Initialization of the nested Maven execution, based on the current session's request
            final MavenExecutionRequest request = DefaultMavenExecutionRequest.copy(session.getRequest())
                    .setExecutionListener(null) /* Disable the observer of the outer maven session */
                    .setTransferListener(null) /* Disable the observer of the outer maven session */
                    .setGoals(null) /* Disable the goals used to execute the outer maven session */
                    .setReactorFailureBehavior(MavenExecutionRequest.REACTOR_FAIL_NEVER)
                    .setPom(new File(topLevelProject.getFile().getParentFile(), POM_XML)) /* Use the pom file of the top-level project */
                    .setBaseDirectory(topLevelProject.getBasedir()) /* Use the basedir of the top-level project */
                    .setRecursive(true)
                    .setExcludedProjects(Collections.emptyList())
                    .setSelectedProjects(Collections.emptyList())
                    .setResumeFrom(null)
                    ;
            // The following user property on the nested execution prevents this extension to activate
            // in the nested execution. This is needed, as the extension is not reentrant.
            request.getUserProperties().put(GITFLOW_SKIP_EXTENSION, true);

            // Perform the nested Maven execution, and grab the list of *all* projects (ie modules of the
            // multi-module build).
            final MavenExecutionResult mavenExecutionResult;
            try {
                mavenExecutionResult = maven.execute(request);
            } finally {
                // The additional Maven execution uses a new session, and at the end of the execution
                // clears the Session object in LegacySupport. This may break other plugins/uses of
                // LegacySupport; therefore always restore the session *after* the additional Maven
                // execution.
                legacySupport.setSession(session);
            }
            final List<MavenProject> topologicallySortedProjects = mavenExecutionResult.getTopologicallySortedProjects();

            // Iterate over these modules and process the 'new' ones just as the modules that are part
            // of the reactor.
            for (MavenProject parsedProject : topologicallySortedProjects) {
                if (adjustedVersions.containsKey(parsedProject)) {
                    logger.info("Skipping " + parsedProject.getGroupId() + ":" + parsedProject.getArtifactId() + ": already part of reactor");
                } else {
                    String originalVersion = PropertyResolver.resolveValue(parsedProject.getVersion(), parsedProject.getProperties(), systemEnvVars);
                    String newVersion = getAsBranchSnapshotVersion(originalVersion, branchInfo.getName());
                    adjustedVersions.put(parsedProject, new String[]{originalVersion, newVersion});
                    logger.info("Updating outside-reactor project " + parsedProject.getGroupId() + ":" + parsedProject.getArtifactId() + ":" + originalVersion + " to: " + newVersion);
                }
            }
        }

        return adjustedVersions;
    }

    private MavenProject findTopLevelProject(MavenProject mavenProject) {
        MavenProject parent = mavenProject;

        // Not using .getParentFile() as this is null when using -pl
        while (parent.getParent() != null && parent.getParent().getFile() != null) {
            parent = parent.getParent();
        }

        return parent;
    }

    private void updateProjectModel(final MavenProject projectContext, final Model model, final String[] versions, final Map<MavenProject, String[]> adjustedVersions) {
        model.setVersion(versions[ADJUSTED_VERSION_IDX]);
        
        // Parent
        if (model.getParent() != null) {
            for (Map.Entry<MavenProject, String[]> adjustedProjects : adjustedVersions.entrySet()) {
                if (isProjectOfReplacedParentVersion(adjustedProjects.getKey(), adjustedProjects.getValue()[ORIGINAL_VERSION_IDX], model.getParent())) {
                    String originalParentVersion = PropertyResolver.resolveValue(model.getParent().getVersion(), projectContext.getProperties(), systemEnvVars);
                    model.getParent().setVersion(originalParentVersion.replace(adjustedProjects.getValue()[ORIGINAL_VERSION_IDX], adjustedProjects.getValue()[ADJUSTED_VERSION_IDX]));
                }
            }
        }
        
        // Dependency Management
        if (model.getDependencyManagement() != null) {
            for (Dependency dep : model.getDependencyManagement().getDependencies()) {
                for (Map.Entry<MavenProject, String[]> adjustedProjects : adjustedVersions.entrySet()) {
                    if (isProjectOfReplacedDependencyVersion(adjustedProjects.getKey(),  adjustedProjects.getValue()[ORIGINAL_VERSION_IDX], dep)) {
                        logger.debug("Updating model: " + model + " managed dependency: " + dep);
                        dep.setVersion(adjustedProjects.getValue()[ADJUSTED_VERSION_IDX]);
                    }
                }
            }
        }
    
        // Standard Dependencies
        for (Dependency dep : model.getDependencies()) {
            for (Map.Entry<MavenProject, String[]> adjustedProjects : adjustedVersions.entrySet()) {
                if (isProjectOfReplacedDependencyVersion(adjustedProjects.getKey(), adjustedProjects.getValue()[ORIGINAL_VERSION_IDX], dep)) {
                    logger.debug("Updating model: " + model + " dependency: " + dep);
                    dep.setVersion(adjustedProjects.getValue()[ADJUSTED_VERSION_IDX]);
                }
            }
        }
    
        // Plugin Management
        if (model.getBuild() != null) {
            // Update / massage the build final name, in case it contains the version string.
            if (model.getBuild().getFinalName() != null) {
                String originalFinalName = PropertyResolver.resolveValue(model.getBuild().getFinalName(), projectContext.getProperties(), systemEnvVars);
                model.getBuild().setFinalName(originalFinalName.replace(versions[ORIGINAL_VERSION_IDX], versions[ADJUSTED_VERSION_IDX]));
            }
    
            if (model.getBuild().getPluginManagement() != null) {
                for (Plugin plugin : model.getBuild().getPluginManagement().getPlugins()) {
                    for (Map.Entry<MavenProject, String[]> adjustedProjects : adjustedVersions.entrySet()) {
                        if (isProjectOfReplacedPluginVersion(adjustedProjects.getKey(), adjustedProjects.getValue()[ORIGINAL_VERSION_IDX], plugin)) {
                            logger.debug("Updating model: " + model + " managed plugin: " + plugin);
                            plugin.setVersion(adjustedProjects.getValue()[ADJUSTED_VERSION_IDX]);
                        }
                    }
                }
            }
    
            // Build Plugins
            for (Plugin plugin : model.getBuild().getPlugins()) {
                for (Map.Entry<MavenProject, String[]> adjustedProjects : adjustedVersions.entrySet()) {
                    if (isProjectOfReplacedPluginVersion(adjustedProjects.getKey(), adjustedProjects.getValue()[ORIGINAL_VERSION_IDX], plugin)) {
                        logger.debug("Updating model: " + model + " plugin: " + plugin);
                        plugin.setVersion(adjustedProjects.getValue()[ADJUSTED_VERSION_IDX]);
                    }
                }
            }
        }
        
        // Reporting Plugins
        if (model.getReporting() != null) {
            for (ReportPlugin plugin : model.getReporting().getPlugins()) {
                for (Map.Entry<MavenProject, String[]> adjustedProjects : adjustedVersions.entrySet()) {
                    if (isProjectOfReplacedReportPluginVersion(adjustedProjects.getKey(), adjustedProjects.getValue()[ORIGINAL_VERSION_IDX], plugin)) {
                        logger.debug("Updating model: " + model + " report plugin: " + plugin);
                        plugin.setVersion(adjustedProjects.getValue()[ADJUSTED_VERSION_IDX]);
                    }
                }
            }
        }
    }
    
    private void createMassagedPom(final MavenProject project) throws MavenExecutionException {
        try {
            // Write the massaged original model
            final File massagedModelFile = new File(project.getFile().getParentFile(), POM_GITFLOW_MASSAGED_XML);
            final byte[] inMemoryData = serializeModel(project.getOriginalModel());
            final boolean writeMassagedPomFile;
            if (massagedModelFile.exists()) {
                // Obtain the existing massaged model, by reading the model and outputting it with the same writer as the rewritten
                // original model; this should prevent (subtle) changes
                final Model existingMassagedModel;
                try (InputStream inputStream = Files.newInputStream(massagedModelFile.toPath())) {
                    existingMassagedModel = modelReader.read(inputStream, null);
                }
                final byte[] existingFileData = serializeModel(existingMassagedModel);

                // If the massaged pom file exists, check if we are about to change it, by comparing the checksum for the
                // in-memory model and what is effectively in the massaged pom file at the moment
                final long inMemoryChecksum = calculateChecksum(inMemoryData);
                final long fileChecksum = calculateChecksum(existingFileData);

                // If the in-memory checksum and the file's checksum differs, the massaged pom file should be written
                writeMassagedPomFile = inMemoryChecksum != fileChecksum;
            } else {
                // If there is no massaged pom file yet, then always write it
                writeMassagedPomFile = true;
            }
            if (writeMassagedPomFile) {
                logger.debug("Writing rewritten model to " + massagedModelFile);
                try (OutputStream outputStream = Files.newOutputStream(massagedModelFile.toPath())) {
                    outputStream.write(inMemoryData);
                }
            } else {
                logger.debug("Existing massaged pom file " + massagedModelFile + " already correct, using it as is.");
            }
            project.setPomFile(massagedModelFile);
        } catch (IOException ioe) {
            throw new MavenExecutionException("Failed to massage pom file to update versioning for deployment branch.", ioe);
        }
    }

    /**
     * Serializes the supplied model into a byte array.
     *
     * @param model Model to serialize
     * @return Byte array containing the model
     * @throws IOException
     */
    private byte[] serializeModel(Model model) throws IOException {
        byte[] bytes;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            modelWriter.write(outputStream, null, model);
            bytes = outputStream.toByteArray();
        }
        return bytes;
    }

    /**
     * Calculates a checksum for the supplied byte array using the CRC32 algorithm.
     * @param bytes Input data
     * @return Checksum value
     */
    private long calculateChecksum(byte[] bytes) {
        final Checksum checksum = new CRC32();
        checksum.update(bytes, 0, bytes.length);
        return  checksum.getValue();
    }

    private boolean isProjectOfReplacedParentVersion(final MavenProject project, final String replacedVersion, final Parent parent) {
        return project.getGroupId().equals(parent.getGroupId()) &&
                project.getArtifactId().equals(parent.getArtifactId()) &&
                replacedVersion.equals(PropertyResolver.resolveValue(parent.getVersion(), project.getProperties(), systemEnvVars));
    }
    
    private boolean isProjectOfReplacedReportPluginVersion(final MavenProject project, final String replacedVersion, final ReportPlugin plugin) {
        return project.getGroupId().equals(plugin.getGroupId()) &&
                project.getArtifactId().equals(plugin.getArtifactId()) &&
                replacedVersion.equals(PropertyResolver.resolveValue(plugin.getVersion(), project.getProperties(), systemEnvVars));
    }
    
    private boolean isProjectOfReplacedPluginVersion(final MavenProject project, final String replacedVersion, final Plugin plugin) {
        return project.getGroupId().equals(plugin.getGroupId()) &&
                project.getArtifactId().equals(plugin.getArtifactId()) &&
                replacedVersion.equals(PropertyResolver.resolveValue(plugin.getVersion(), project.getProperties(), systemEnvVars));
    }
    
    private boolean isProjectOfReplacedDependencyVersion(final MavenProject project, final String replacedVersion, final  Dependency dependency) {
        return project.getGroupId().equals(dependency.getGroupId()) &&
                project.getArtifactId().equals(dependency.getArtifactId()) &&
                replacedVersion.equals(PropertyResolver.resolveValue(dependency.getVersion(), project.getProperties(), systemEnvVars));
    }
    
    private boolean isProjectOfReplacedArtifactVersion(final MavenProject project, final String replacedVersion, final Artifact artifact) {
        return project.getGroupId().equals(artifact.getGroupId()) &&
                project.getArtifactId().equals(artifact.getArtifactId()) &&
                replacedVersion.equals(PropertyResolver.resolveValue(artifact.getVersion(), project.getProperties(), systemEnvVars));
    }
    
    
    /**
     * Given a String version (which may be a final or -SNAPSHOT version) return a
     * version string mangled to include a `+normalized-branch-name-SNAPSHOT format version.
     *
     * @param version The base version (ie, 1.0.2-SNAPSHOT)
     * @param branchName to be normalized
     * @return A mangled version string with the branchname and -SNAPSHOT.
     */
    public String getAsBranchSnapshotVersion(final String version, final String branchName) {
        String branchNameSanitized = otherBranchVersionDelimiter + branchName.replaceAll("[^0-9A-Za-z-.]", "-") + "-SNAPSHOT";
        if(version.endsWith(branchNameSanitized)) {
            return version;
        }
        return version.replace("-SNAPSHOT", "") + branchNameSanitized;
    }
    
}
