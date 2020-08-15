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
import org.apache.maven.model.io.ModelWriter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "other-branch-version")
public class OtherBranchVersionExtension extends AbstractBranchDetectingExtension {
    
    @Requirement(role = ModelWriter.class)
    ModelWriter modelWriter;

    @Requirement(role = Maven.class)
    Maven maven;
    
    private static final int ORIGINAL_VERSION_IDX = 0;
    private static final int ADJUSTED_VERSION_IDX = 1;
    
    @Override
    public void afterProjectsRead(final MavenSession session) throws MavenExecutionException {
        super.afterProjectsRead(session);

        if (session.getUserProperties().containsKey("gitflow.skip.extension")) {
            return;
        }

        if (pluginFound) {
            logger.debug("other-branch-version extension active.");
            if (branchInfo != null) {
                logger.debug("other-branch-version extension on git branch: " + branchInfo.toString());
                if(branchInfo.getType().equals(GitBranchType.OTHER)) {
                    String otherBranchesToDeploy = PropertyResolver.resolveValue(otherDeployBranchPattern, session.getTopLevelProject().getProperties(), systemEnvVars);
                    logger.debug("OTHER branch detected. Testing against pattern: `" + otherBranchesToDeploy + "`");
                    if (!"".equals(otherBranchesToDeploy) && branchInfo.getName().matches(otherBranchesToDeploy)) {
                        // Build a cross-walk map of projects to versions.
                        HashMap<MavenProject, String[]> adjustedVersions = new HashMap<>();
                        
                        // Update the versions on all the project models (in-memory, effective models).
                        for (MavenProject project : session.getProjects()) {
                            // Resolve the originalVersion, in case it has properties.
                            String originalVersion = PropertyResolver.resolveValue(project.getVersion(), project.getProperties(), systemEnvVars);
                            String newVersion = getAsBranchSnapshotVersion(originalVersion, branchInfo.getName());
                            adjustedVersions.put(project, new String[] {originalVersion, newVersion});
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

                        final MavenProject topLevelProjectInsideReactor = session.getTopLevelProject();
                        final MavenProject topLevelProject = findTopLevelProject(topLevelProjectInsideReactor);
                        logger.info("Top level project: " + topLevelProjectInsideReactor.getGroupId() + ":" + topLevelProjectInsideReactor.getArtifactId());
                        if (!topLevelProjectInsideReactor.equals(topLevelProject)) {
                            // When only the partial tree of modules is being built, not all modules could be indexed
                            // into the cross-walk map. In order to deal with dependencies to modules outside the reactor,
                            // Another Maven execution is performed on the 'actual' top level project. This allows the
                            // cross-walk map to be completed with the modules that *are* part of the multi-module project,
                            // but are currently not part of the reactor.

                            logger.info("Found top level project, but outside reactor: " + topLevelProject.getGroupId() + ":" + topLevelProject.getArtifactId() + " (" + topLevelProject.getFile() + ")");

                            // Initialization of the nested Maven execution
                            final MavenExecutionRequest request = new DefaultMavenExecutionRequest()
                                            .setLocalRepository(session.getLocalRepository())
                                            .setPom(topLevelProject.getFile())
                                            .setBaseDirectory(topLevelProject.getBasedir())
                                            .setReactorFailureBehavior(MavenExecutionRequest.REACTOR_FAIL_NEVER)
                                            .setUserProperties(session.getUserProperties())
                                    ;
                            // The following user property on the nested execution prevents this extension to activate
                            // in the nested execution. This is needed, as the extension is not reentrant.
                            request.getUserProperties().put("gitflow.skip.extension", true);

                            // Perform the nested Maven execution, and grab the list of *all* projects (ie modules of the
                            // multi-module build).
                            final MavenExecutionResult mavenExecutionResult = maven.execute(request);
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

                        // Once we have that populated, refilter the adjusted projects models
                        // updating dependencies on both the in-memory effective model, and the original pom.xml model.
                        for (Map.Entry<MavenProject, String[]> adjustedProject : adjustedVersions.entrySet()) {
                            
                            
                            // If the parent artifact is one of the projects with an adjusted version..
                            if (adjustedProject.getKey().getParentArtifact() != null) {
                                for (Map.Entry<MavenProject, String[]> adjustedProjects : adjustedVersions.entrySet()) {
                                    if (isProjectOfReplacedArtifactVersion(adjustedProjects.getKey(),  adjustedProjects.getValue()[ORIGINAL_VERSION_IDX], adjustedProject.getKey().getParentArtifact())) {
                                        logger.info("Updating project: " + adjustedProject.getKey() + " parent artifact: " + adjustedProject.getKey().getParentArtifact());
                                        adjustedProject.getKey().getParentArtifact().setVersion(adjustedProjects.getValue()[ADJUSTED_VERSION_IDX]);
                                        adjustedProject.getKey().getParentArtifact().setVersionRange(VersionRange.createFromVersion(adjustedProjects.getValue()[ADJUSTED_VERSION_IDX]));
                                        logger.info("    Now: " + adjustedProject.getKey().getParentArtifact());
                                    }
                                }
                            }
                            
                            // Update the in-reactor active and effective model.
                            updateProjectModel(adjustedProject.getKey(), adjustedProject.getKey().getModel(), adjustedProject.getValue(), adjustedVersions);
                            
                            // The original model is an un-effective Model of the original pom.xml
                            updateProjectModel(adjustedProject.getKey(), adjustedProject.getKey().getOriginalModel(), adjustedProject.getValue(), adjustedVersions);
                            
                            createMassagedPom(adjustedProject.getKey());
                        }
                    }
                }
            }
        }
        
        logger.info("Continuing execution....");
    }

    private MavenProject findTopLevelProject(MavenProject mavenProject) {
        MavenProject parent = mavenProject;

        while (parent.getParentFile() != null) {
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
                    model.getParent().setVersion(originalParentVersion.replace(versions[ORIGINAL_VERSION_IDX], versions[ADJUSTED_VERSION_IDX]));
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
            File massagedModelFile = new File(project.getFile().getParentFile(), "pom-gitflow-massaged.xml");
            modelWriter.write(massagedModelFile, null, project.getOriginalModel());
            project.setPomFile(massagedModelFile);
        } catch (IOException ioe) {
            throw new MavenExecutionException("Failed to massage pom file to update versioning for deployment branch.", ioe);
        }
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
     * version version string mangled to include a `+normalized-branch-name-SNAPSHOT format version.
     *
     * @param version The base version (ie, 1.0.2-SNAPSHOT)
     * @param branchName to be normalized
     * @return A mangled version string with the branchname and -SNAPSHOT.
     */
    private String getAsBranchSnapshotVersion(final String version, final String branchName) {
        return version.replace("-SNAPSHOT", "") + otherBranchVersionDelimiter + branchName.replaceAll("[^0-9A-Za-z-.]", "-") + "-SNAPSHOT";
    }
    
}
