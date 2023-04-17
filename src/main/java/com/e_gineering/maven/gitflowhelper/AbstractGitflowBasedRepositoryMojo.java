package com.e_gineering.maven.gitflowhelper;

import static java.nio.charset.StandardCharsets.UTF_8;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.utils.StringUtils;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.impl.EnhancedLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Common configuration and plumbing (support methods) for Repository operations on Gitflow Mojo.
 */
abstract class AbstractGitflowBasedRepositoryMojo extends AbstractGitflowBranchMojo {
    private static final String CATALOG_HEADER = "[artifacts]";

    private static PrintWriter newPrintWriter(File catalog) throws FileNotFoundException {
        Objects.requireNonNull(catalog, "catalog must not be null");
        return new PrintWriter(new OutputStreamWriter(new FileOutputStream(catalog), UTF_8));
    }
    
    @Parameter(property = "releaseDeploymentRepositoryId", required = true)
    String releaseDeploymentRepository;

    @Parameter(property = "stageDeploymentRepositoryId", required = true)
    String stageDeploymentRepository;

    @Parameter(property = "snapshotDeploymentRepositoryId", required = true)
    String snapshotDeploymentRepository;
    
    @Parameter(property = "otherDeployBranchPattern", required = false)
    String otherDeployBranchPattern;

    @Parameter(defaultValue = "+", required = true)
    String otherBranchVersionDelimiter;
    
    @Parameter(defaultValue = "${repositorySystemSession}", required = true)
    RepositorySystemSession repositorySystemSession;
    
    @Parameter(defaultValue = "${project.build.directory}", required = true)
    File buildDirectory;
    
    @Component
    private RepositorySystem repositorySystem;
    
    @Component
    private EnhancedLocalRepositoryManagerFactory localRepositoryManagerFactory;
    
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * Creates a Maven ArtifactRepository for targeting deployments.
     *
     * @param id the repository identifier
     * @return the resolved repository
     *
     * @throws MojoFailureException if the repository id is not defined.
     */
    ArtifactRepository getDeploymentRepository(final String id) throws MojoFailureException {
        Objects.requireNonNull(id, "A repository id must be specified.");

        Optional<ArtifactRepository> repo = project.getRemoteArtifactRepositories().stream().filter(r -> r.getId().equals(id)).findFirst();
        if (repo.isPresent()) {
            return repo.get();
        }

        Optional<ArtifactRepository> mirroredRepo = project.getRemoteArtifactRepositories().stream()
                .flatMap(r -> r.getMirroredRepositories().stream()).filter(r -> r.getId().equals(id)).findFirst();
        if(mirroredRepo.isPresent()) {
            return mirroredRepo.get();
        }
        throw new MojoFailureException("No Repository with id `" + id + "` is defined.");
    }

    /**
     * Creates and attaches an artifact containing a list of attached artifacts, each line in the file contains
     * group:artifact:type:classifier:version
     */
    void attachArtifactCatalog() throws MojoExecutionException {
        getLog().info("Cataloging Artifacts for promotion & reattachment: " + project.getBuild().getDirectory());

        File catalog = new File(buildDirectory, project.getArtifact().getArtifactId() + ".txt");

        if (!catalog.delete()) {
            getLog().debug("Failed to remove catalog file: " + catalog);
        }

        if (!buildDirectory.mkdirs()) {
            getLog().debug("Failed to create build directory: " + buildDirectory);
        }

        PrintWriter writer = null;
        try {
            writer = newPrintWriter(catalog);

            // add catalog header, ensuring that no zero-byte catalog is created
            writer.println(CATALOG_HEADER);

            if (hasCataloguableArtifacts()) {
                if (hasFile(project.getArtifact())) {
                    catalogArtifact(writer, project.getArtifact());
                } else {
                    getLog().info("No primary artifact to catalog, cataloging attached artifacts instead.");
                }

                // Iterate the attached artifacts.
                for (Artifact artifact : project.getAttachedArtifacts()) {
                    catalogArtifact(writer, artifact);
                }
            } else {
                getLog().info(
                        "No artifacts were catalogued."
                );
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

    private void catalogArtifact(PrintWriter writer, Artifact artifact) {
        String coords = getCoordinates(artifact);
        getLog().info("Cataloging: " + coords);
        writer.println(coords);
    }

    /**
     * Resolves artifacts from the given sourceRepository by first resolving and processing the artifact catalog
     * created by the promote-master mojo.
     *
     * @param sourceRepository An ArtifactRepository to use as a RemoteRepository when supplied. Otherwise, only the local repository will be used.
     * @param disableLocal if artifacts should be downloaded from a remote to an isolated repository, bypassing the 'standard' maven local repo.
     *
     * @throws MojoExecutionException for any unhandled maven exception
     */
    void attachExistingArtifacts(@Nullable final String sourceRepository, final boolean disableLocal)
        throws MojoExecutionException, MojoFailureException {
        
        List<ArtifactRepository> remoteArtifactRepositories = new ArrayList<>();
        Optional<ArtifactRepository> repo = project.getRemoteArtifactRepositories().stream().filter(r -> r.getId().equals(sourceRepository)).findFirst();
        if (repo.isPresent()) {
            remoteArtifactRepositories.add(repo.get());
        } else {
            if (disableLocal) {
                throw new MojoExecutionException("Cannot resolve artifacts from 'null' repository if the local repository is also disabled.");
            }
            getLog().debug("Resolving existing artifacts from local repository only.");
        }
        List<RemoteRepository> remoteRepositories = RepositoryUtils.toRepos(remoteArtifactRepositories);
        
        // A place to store our resolved files...
        List<ArtifactResult> resolvedArtifacts = new ArrayList<>();

        // Use a customized repository session, setup to force a few behaviors we like.
        DefaultRepositorySystemSession tempSession = new DefaultRepositorySystemSession(repositorySystemSession);
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
        tempSession.setReadOnly();

        List<ArtifactRequest> requiredArtifacts = new ArrayList<>();

        // Locate our text catalog classifier file. :-)
        BufferedReader reader = null;
        try {
            DefaultArtifact artifact = new DefaultArtifact(
                    project.getGroupId(), project.getArtifactId(), "catalog", "txt", project.getVersion()
            );
            ArtifactRequest request = new ArtifactRequest(artifact, remoteRepositories, null);
            ArtifactResult catalogResult = repositorySystem.resolveArtifact(tempSession, request);
            resolvedArtifacts.add(catalogResult);

            if (catalogResult.isResolved()) {
                // Read the file line by line...
                FileInputStream fis = new FileInputStream(catalogResult.getArtifact().getFile());
                InputStreamReader isr = new InputStreamReader(fis, UTF_8);
                reader = new BufferedReader(isr);

                String coords;
                boolean firstLine = true;
                while ((coords = reader.readLine()) != null) {
                    coords = coords.trim();

                    // test for catalog header for bacvkwards compatibility
                    if (!coords.isEmpty() && !(firstLine && CATALOG_HEADER.equals(coords))) {
                        // should be a reifiable GAV coordinate therefore add a new ArtifactRequest
                        requiredArtifacts.add(
                                new ArtifactRequest(new DefaultArtifact(coords), remoteRepositories, null)
                        );
                    }
                    firstLine = false;
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
                } catch (IOException ignored) {
                }
            }
        }

        // Resolve the artifacts from the catalog (if there are any)
        try {
            resolvedArtifacts.addAll(repositorySystem.resolveArtifacts(tempSession, requiredArtifacts));
        } catch (ArtifactResolutionException are) {
            throw new MojoExecutionException("Failed to resolve the required project files from repository: " + sourceRepository, are);
        }

        // Get the current build artifact coordinates, so that we replace rather than re-attach.
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
                    getLog().debug(
                            "    Attaching artifact: " + getCoordinates(artifactResult) + " "
                                    + artifactResult.getArtifact().getFile());
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

    /**
     * Returns true if the project has any artifacts to be catalogued.
     *
     * @return true if the primary artifact has a real file part as defined by {@link #hasFile(Artifact)}, or if any
     * attached artifacts are present.
     */
    private boolean hasCataloguableArtifacts() {
        return hasFile(project.getArtifact()) || !project.getAttachedArtifacts().isEmpty();
    }

    /**
     * Returns true if the artifact describes a real file.
     *
     * @param artifact the possibly null artifact
     * @return true if the artifact describes a real file, false otherwise
     */
    private boolean hasFile(@Nullable Artifact artifact) {
        return artifact != null
               && project.getArtifact().getFile() != null
               && project.getArtifact().getFile().exists()
               && project.getArtifact().getFile().isFile();
    }
    
    
    private String getCoordinates(ArtifactResult artifactResult) {
        return getCoordinates(
                emptyToNull(artifactResult.getArtifact().getGroupId()),
                emptyToNull(artifactResult.getArtifact().getArtifactId()),
                emptyToNull(artifactResult.getArtifact().getBaseVersion()),
                emptyToNull(artifactResult.getArtifact().getExtension()),
                emptyToNull(artifactResult.getArtifact().getClassifier())
        );
    }
    
    private static String emptyToNull(final String s) {
        return StringUtils.isBlank(s) ? null : s;
    }
    
    private String getCoordinates(Artifact artifact) {
        getLog().debug("   Encoding Coordinates For: " + artifact);
        
        // Get the extension according to the artifact type.
        String extension = artifact.getArtifactHandler().getExtension();
        
        return getCoordinates(
                artifact.getGroupId(),
                artifact.getArtifactId(),
                project.getVersion(),
                extension, artifact.hasClassifier() ? artifact.getClassifier() : null
        );
    }
    
    private String getCoordinates(String groupId,
                                  String artifactId,
                                  String version,
                                  @Nullable String extension,
                                  @Nullable String classifier) {
        Objects.requireNonNull(groupId, "groupId must not be null");
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(version, "version must not be null");
        
        StringBuilder result = new StringBuilder();
        for (String s : new String[]{groupId, artifactId, extension, classifier, version}) {
            if (s != null) {
                if (result.length() > 0) {
                    result.append(":");
                }
                result.append(s);
            }
        }
        return result.toString();
    }
}
