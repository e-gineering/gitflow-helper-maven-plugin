package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.internal.impl.EnhancedLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Common configuration and plumbing (support methods) for Repository operations on Gitflow Mojo.
 */
public abstract class AbstractGitflowBasedRepositoryMojo extends AbstractGitflowBranchMojo {

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+)::(.+)::(.+)::(.+)");

    @Parameter(property = "releaseDeploymentRepository", required = true)
    protected String releaseDeploymentRepository;

    @Parameter(property = "stageDeploymentRepository", required = true)
    protected String stageDeploymentRepository;

    @Parameter(property = "snapshotDeploymentRepository", required = true)
    protected String snapshotDeploymentRepository;

    @Parameter(defaultValue = "${repositorySystemSession}", required = true)
    protected RepositorySystemSession session;

    @Component
    protected EnhancedLocalRepositoryManagerFactory localRepositoryManagerFactory;

    @Parameter(defaultValue = "${project.build.directory}", required = true)
    protected File buildDirectory;

    @Component
    protected ArtifactRepositoryFactory repositoryFactory;

    @Component
    private ArtifactResolver artifactResolver;

    @Component
    private MavenProjectHelper projectHelper;

    @Component(role = ArtifactRepositoryLayout.class)
    private Map<String, ArtifactRepositoryLayout> repositoryLayouts;

    /**
     * Builds an ArtifactRepository for targeting deployments
     * .
     *
     * @param altRepository
     * @return
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    protected ArtifactRepository getDeploymentRepository(final String altRepository) throws MojoExecutionException, MojoFailureException {
        Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher(altRepository);
        Repository candidate = null;
        if (!matcher.matches()) {
            for (int i = 0; i < project.getRepositories().size(); i++) {
                candidate = project.getRepositories().get(i);
                getLog().debug("Checking defined repository ID: " + candidate.getId().trim() + " against: " + altRepository.trim());
                if (candidate.getId().trim().equals(altRepository.trim())) {
                    break;
                }
                candidate = null;
            }

            if (candidate == null) {
                throw new MojoFailureException(altRepository, "Invalid syntax for repository or repository id not resolved..",
                        "Invalid syntax for repository. Use \"id::layout::url::unique\" or only specify the \"id\".");
            }
        }

        if (getLog().isDebugEnabled()) {
            getLog().debug("Getting maven deployment repository (to target artifacts) for: " + altRepository);
        }

        if (candidate == null) {
            String id = matcher.group(1).trim();
            String layout = matcher.group(2).trim();
            String url = matcher.group(3).trim();
            boolean unique = Boolean.parseBoolean(matcher.group(4).trim());

            ArtifactRepositoryLayout repoLayout = getLayout(layout);

            return repositoryFactory.createDeploymentArtifactRepository(id, url, repoLayout, unique);
        } else {
            return repositoryFactory.createDeploymentArtifactRepository(candidate.getId(), candidate.getUrl(), getLayout(candidate.getLayout()), candidate.getSnapshots().isEnabled());
        }
    }

    /**
     * Builds a RemoteRepository for resolving artifacts.
     *
     * @param altRepository
     * @return
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    protected RemoteRepository getRepository(final String altRepository) throws MojoExecutionException, MojoFailureException {
        if (getLog().isDebugEnabled()) {
            getLog().debug("Creating remote Aether repository (to resolve remote artifacts) for: " + altRepository);
        }
        // Get an appropriate injected ArtifactRepository. (This resolves authentication in the 'normal' manner from Maven)
        ArtifactRepository remoteArtifactRepo = getDeploymentRepository(altRepository);

        if (getLog().isDebugEnabled()) {
            getLog().debug("Resolved maven deployment repository. Transcribing to Aether Repository...");
        }

        RemoteRepository.Builder remoteRepoBuilder = new RemoteRepository.Builder(remoteArtifactRepo.getId(), remoteArtifactRepo.getLayout().getId(), remoteArtifactRepo.getUrl());

        // Add authentication.
        if (remoteArtifactRepo.getAuthentication() != null) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("Maven deployment repsoitory has Authentication. Transcribing to Aether Authentication...");
            }
            remoteRepoBuilder.setAuthentication(new AuthenticationBuilder().addUsername(remoteArtifactRepo.getAuthentication().getUsername())
                    .addPassword(remoteArtifactRepo.getAuthentication().getPassword())
                    .addPrivateKey(remoteArtifactRepo.getAuthentication().getPrivateKey(), remoteArtifactRepo.getAuthentication().getPassphrase())
                    .build());
        }

        return remoteRepoBuilder.build();
    }

    private String getCoordinates(ArtifactResult result) {
        StringBuilder buffer = new StringBuilder(128);
        buffer.append(result.getArtifact().getGroupId());
        buffer.append(':').append(result.getArtifact().getArtifactId());
        buffer.append(':').append(result.getArtifact().getExtension());
        if (result.getArtifact().getClassifier().length() > 0) {
            buffer.append(':').append(result.getArtifact().getClassifier());
        }
        buffer.append(':').append(result.getArtifact().getBaseVersion());
        return buffer.toString();
    }

    private String getCoordinates(org.apache.maven.artifact.Artifact artifact) {
        StringBuilder result = new StringBuilder();

        getLog().debug("   Encoding Coordinates For: " + artifact);

        // Get the extension according to the artifact type.
        String extension = session.getArtifactTypeRegistry().get(artifact.getType()).getExtension();

        // assert that the file extension matches the artifact packaging extension type, if there is an artifact file.
        if (artifact.getFile() != null && !artifact.getFile().getName().toLowerCase().endsWith(extension.toLowerCase())) {
            String fileExtension = artifact.getFile().getName().substring(artifact.getFile().getName().lastIndexOf('.') + 1);
            getLog().warn("    Artifact file name: " + artifact.getFile().getName() + " of type " +
                    artifact.getType() + " does not match the extension for the ArtifactType: " + extension + ". " +
                    "This is likely an issue with the packaging definition for '" + artifact.getType() + "' artifacts, which may be missing an extension definition. " +
                    "The gitflow helper catalog will use the actual file extension: " + fileExtension);
            extension = fileExtension;
        }

        // group:artifact:extension
        result.append(project.getGroupId()).append(":").append(project.getArtifactId()).append(":").append(extension);
        if (artifact.hasClassifier()) {
            // :classifier
            result.append(":").append(artifact.getClassifier());
        }
        result.append(":").append(project.getVersion());

        return result.toString().trim();
    }

    /**
     * Creates and attaches an artifact containing a list of attached artifacts, each line in the file contains
     * group:artifact:type:classifier:version
     */
    protected void attachArtifactCatalog() throws MojoExecutionException {
        getLog().info("Cataloging Artifacts for promotion & reattachment: " + project.getBuild().getDirectory());

        File catalog = new File(buildDirectory, project.getArtifact().getArtifactId() + ".txt");

        PrintWriter writer = null;

        try {
            catalog.delete();
            buildDirectory.mkdirs();
            writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(catalog), Charset.forName("UTF-8")));

            if (project.getArtifact() != null && project.getArtifact().getFile() != null &&
                    project.getArtifact().getFile().exists() && !project.getArtifact().getFile().isDirectory()) {
                String coords = getCoordinates(project.getArtifact());
                if (!coords.isEmpty()) {
                    getLog().info("Cataloging: " + coords);
                    writer.println(coords);
                }
            } else {
                getLog().info("No primary artifact to catalog, cataloging attached artifacts instead.");
            }

            // Iterate the attached artifacts.
            for (org.apache.maven.artifact.Artifact artifact : project.getAttachedArtifacts()) {
                String coords = getCoordinates(artifact);
                if (!coords.isEmpty()) {
                    getLog().info("Cataloging: " + coords);
                    writer.println(coords);
                }
            }

            getLog().info("Attaching catalog artifact: " + catalog);
            projectHelper.attachArtifact(project, "txt", "catalog", catalog);
        } catch (IOException ioe) {
            throw new MojoExecutionException("Failed to create catalog of artifacts", ioe);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    /**
     * Resolves artifacts from the given sourceRepository by first resolving and processing the artifact catalog
     * created by the promote-master mojo.
     *
     * @param sourceRepository
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    protected void attachExistingArtifacts(final String sourceRepository, final boolean disableLocal) throws MojoExecutionException, MojoFailureException {
        List<RemoteRepository> remoteRepositories = new ArrayList<RemoteRepository>();

        if (sourceRepository == null) {
            if (disableLocal == true) {
                throw new MojoExecutionException("Cannot resolve artifacts from 'null' repository if the local repository is also disabled.");
            }
            getLog().debug("Resolving existing artifacts from local repository only.");
        } else {
            // Add the remote repository.
            remoteRepositories.addAll(Arrays.asList(getRepository(sourceRepository)));
        }

        // A place to store our resolved files...
        List<ArtifactResult> resolvedArtifacts = new ArrayList<ArtifactResult>();


        // Use a custom repository session, setup to force a few behaviors we like.
        DefaultRepositorySystemSession tempSession = new DefaultRepositorySystemSession(session);
        tempSession.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);

        File tempRepo = null;
        if (disableLocal) {
            getLog().info("Disabling local repository @ " + tempSession.getLocalRepository().getBasedir());
            try {
                tempRepo = Files.createTempDirectory("gitflow-helper-maven-plugin-repo").toFile();

                getLog().info("Using temporary local repository @ " + tempRepo.getAbsolutePath());
                tempSession.setLocalRepositoryManager(localRepositoryManagerFactory.newInstance(tempSession, new LocalRepository(tempRepo)));
            } catch (Exception ex) {
                getLog().warn("Failed to disable local repository path.", ex);
            }
        }

        List<ArtifactRequest> requiredArtifacts = new ArrayList<ArtifactRequest>();

        // Locate our text catalog classifier file. :-)
        BufferedReader reader = null;
        try {
            ArtifactResult catalogResult = artifactResolver.resolveArtifact(tempSession, new ArtifactRequest(new DefaultArtifact(project.getGroupId(), project.getArtifactId(), "catalog", "txt", project.getVersion()), remoteRepositories, null));
            resolvedArtifacts.add(catalogResult);

            if (catalogResult.isResolved()) {
                // Read the file line by line...
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(catalogResult.getArtifact().getFile()), Charset.forName("UTF-8")));

                String coords = null;
                while ((coords = reader.readLine()) != null) {
                    if (!coords.trim().isEmpty()) {
                        // And add a new ArtifactRequest
                        requiredArtifacts.add(new ArtifactRequest(new DefaultArtifact(coords.trim()), remoteRepositories, null));
                    }
                }
            }
        } catch (ArtifactResolutionException are) {
            throw new MojoExecutionException("Could not locate artifact catalog in remote repository.", are);
        } catch (IOException ioe) {
            throw new MojoExecutionException("Could not read artifact catalog", ioe);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ioe) {
                }
            }
        }


        // Resolve the artifacts from the catalog (if there are any)
        try {
            resolvedArtifacts.addAll(artifactResolver.resolveArtifacts(tempSession, requiredArtifacts));
        } catch (ArtifactResolutionException are) {
            throw new MojoExecutionException("Failed to resolve the required project files from: " + sourceRepository, are);
        }

        // Get the current build artifact coordindates, so that we replace rather than re-attach.
        String projectArtifactCoordinates = getCoordinates(project.getArtifact());
        getLog().debug("Current Project Coordinates: " + projectArtifactCoordinates);

        // For each artifactResult, copy it to the build directory,
        // update the resolved artifact data to point to the new file.
        // Then either set the project artifact to point to the file in the build directory, or attach the artifact.
        for (ArtifactResult artifactResult : resolvedArtifacts) {
            try {
                FileUtils.copyFileToDirectory(artifactResult.getArtifact().getFile(), buildDirectory);
                artifactResult.setArtifact(artifactResult.getArtifact().setFile(new File(buildDirectory, artifactResult.getArtifact().getFile().getName())));

                if (getCoordinates(artifactResult).equals(projectArtifactCoordinates)) {
                    getLog().debug("    Setting primary artifact: " + artifactResult.getArtifact().getFile());
                    project.getArtifact().setFile(artifactResult.getArtifact().getFile());
                } else {
                    getLog().debug("    Attaching artifact: " + getCoordinates(artifactResult) + " " + artifactResult.getArtifact().getFile());
                    projectHelper.attachArtifact(project, artifactResult.getArtifact().getExtension(), artifactResult.getArtifact().getClassifier(), artifactResult.getArtifact().getFile());
                }
            } catch (IOException ioe) {
                throw new MojoExecutionException("Failed to copy resolved artifact to target directory.", ioe);
            }
        }

        // Restore the local repository, again using reflection.
        if (disableLocal) {
            if (tempRepo != null) {
                try {
                    FileUtils.deleteDirectory(tempRepo);
                } catch (IOException e) {
                    getLog().warn("Failed to cleanup temporary repository directory: " + tempRepo);
                }
            }
        }
    }

    private ArtifactRepositoryLayout getLayout(final String id) throws MojoExecutionException {
        ArtifactRepositoryLayout layout = repositoryLayouts.get(id);
        if (layout == null) {
            throw new MojoExecutionException("Invalid repository layout: " + id);
        }

        return layout;
    }

}
