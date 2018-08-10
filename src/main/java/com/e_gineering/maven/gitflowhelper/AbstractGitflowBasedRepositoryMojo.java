package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.artifact.Artifact;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Common configuration and plumbing (support methods) for Repository operations on Gitflow Mojo.
 */
abstract class AbstractGitflowBasedRepositoryMojo extends AbstractGitflowBranchMojo {

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+)::(.+)::(.+)::(.+)");
    private static final String CATALOG_HEADER = "[artifacts]";

    /**
     * Returns the policy or a default policy if {@code policy} is null.
     *
     * @param policy the policy to check
     * @return the {@link org.apache.maven.model.RepositoryPolicy}
     */
    private static org.apache.maven.model.RepositoryPolicy ensureRepositoryPolicy(
            @Nullable org.apache.maven.model.RepositoryPolicy policy) {
        if (policy == null) {
            return new org.apache.maven.model.RepositoryPolicy();
        }
        return policy;
    }

    private static PrintWriter newPrintWriter(File catalog) throws FileNotFoundException {
        checkNotNull(catalog, "catalog must not be null");
        return new PrintWriter(new OutputStreamWriter(new FileOutputStream(catalog), UTF_8));
    }

    @Parameter(property = "releaseDeploymentRepository", required = true)
    String releaseDeploymentRepository;

    @Parameter(property = "stageDeploymentRepository", required = true)
    String stageDeploymentRepository;

    @Parameter(property = "snapshotDeploymentRepository", required = true)
    String snapshotDeploymentRepository;

    @Parameter(defaultValue = "${repositorySystemSession}", required = true)
    RepositorySystemSession session;

    @Component
    private EnhancedLocalRepositoryManagerFactory localRepositoryManagerFactory;

    @Parameter(defaultValue = "${project.build.directory}", required = true)
    private File buildDirectory;

    @Component
    private ArtifactRepositoryFactory repositoryFactory;

    @Component
    private ArtifactResolver artifactResolver;

    @Component
    private MavenProjectHelper projectHelper;

    @Component(role = ArtifactRepositoryLayout.class)
    private Map<String, ArtifactRepositoryLayout> repositoryLayouts;

    @Component
    private GavCoordinateHelperFactory gavCoordinateFactory;

    private GavCoordinateHelper gavCoordinateHelper;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        gavCoordinateHelper = gavCoordinateFactory.using(session);

        super.execute();
    }

    /**
     * Builds an ArtifactRepository for targeting deployments.
     *
     * @param altRepository the repository identifier or alt-syntax specification
     * @return the resolved repository
     * @throws MojoExecutionException if the provided repository specification defines an invalid repository layout
     * @throws MojoFailureException if the provided repository specification is invalid
     */
    ArtifactRepository getDeploymentRepository(final String altRepository)
        throws MojoExecutionException, MojoFailureException {
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
                        "Invalid syntax for repository. Use \"id::layout::url::unique\" or only specify the \"id\"." +
                        " For the \"id\", make sure that the corresponding <repository> element has been defined (e.g. in the ~/.m2/settings.xml file)");
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
            return repositoryFactory.createDeploymentArtifactRepository(
                    candidate.getId(),
                    candidate.getUrl(),
                    getLayout(candidate.getLayout()),
                    ensureRepositoryPolicy(candidate.getSnapshots()).isEnabled()
            );
        }
    }

    /**
     * Builds a RemoteRepository for resolving artifacts.
     *
     * @param altRepository the repository identifier or alt-syntax specification
     * @return the resolve remote repository
     * @throws MojoExecutionException if the provided repository specification defines an invalid repository layout
     * @throws MojoFailureException if the provided repository specification is invalid
     */
    RemoteRepository getRepository(final String altRepository) throws MojoExecutionException, MojoFailureException {
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
        String coords = gavCoordinateHelper.getCoordinates(artifact);
        getLog().info("Cataloging: " + coords);
        writer.println(coords);
    }

    /**
     * Resolves artifacts from the given sourceRepository by first resolving and processing the artifact catalog
     * created by the promote-master mojo.
     *
     * @param sourceRepository the repository identifier or alt-syntax specification
     * @param disableLocal if the staged artifacts should be downloaded to an isolated repository
     * @throws MojoExecutionException for any unhandled maven exception
     * @throws MojoFailureException if the provided repository specification is invalid
     */
    void attachExistingArtifacts(final String sourceRepository, final boolean disableLocal)
        throws MojoExecutionException, MojoFailureException {
        List<RemoteRepository> remoteRepositories = new ArrayList<>();

        if (sourceRepository == null) {
            if (disableLocal) {
                throw new MojoExecutionException("Cannot resolve artifacts from 'null' repository if the local repository is also disabled.");
            }
            getLog().debug("Resolving existing artifacts from local repository only.");
        } else {
            // Add the remote repository.
            remoteRepositories.addAll(Collections.singletonList(getRepository(sourceRepository)));
        }

        // A place to store our resolved files...
        List<ArtifactResult> resolvedArtifacts = new ArrayList<>();


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

        List<ArtifactRequest> requiredArtifacts = new ArrayList<>();

        // Locate our text catalog classifier file. :-)
        BufferedReader reader = null;
        try {
            DefaultArtifact artifact = new DefaultArtifact(
                    project.getGroupId(), project.getArtifactId(), "catalog", "txt", project.getVersion()
            );
            ArtifactRequest request = new ArtifactRequest(artifact, remoteRepositories, null);
            ArtifactResult catalogResult = artifactResolver.resolveArtifact(tempSession, request);
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
            resolvedArtifacts.addAll(artifactResolver.resolveArtifacts(tempSession, requiredArtifacts));
        } catch (ArtifactResolutionException are) {
            throw new MojoExecutionException("Failed to resolve the required project files from: " +
                    sourceRepository, are);
        }

        // Get the current build artifact coordinates, so that we replace rather than re-attach.
        String projectArtifactCoordinates = gavCoordinateHelper.getCoordinates(project.getArtifact());
        getLog().debug("Current Project Coordinates: " + projectArtifactCoordinates);

        // For each artifactResult, copy it to the build directory,
        // update the resolved artifact data to point to the new file.
        // Then either set the project artifact to point to the file in the build directory, or attach the artifact.
        for (ArtifactResult artifactResult : resolvedArtifacts) {
            try {
                FileUtils.copyFileToDirectory(artifactResult.getArtifact().getFile(), buildDirectory);
                artifactResult.setArtifact(artifactResult.getArtifact().setFile(new File(buildDirectory, artifactResult.getArtifact().getFile().getName())));

                if (gavCoordinateHelper.getCoordinates(artifactResult).equals(projectArtifactCoordinates)) {
                    getLog().debug("    Setting primary artifact: " + artifactResult.getArtifact().getFile());
                    project.getArtifact().setFile(artifactResult.getArtifact().getFile());
                } else {
                    getLog().debug(
                            "    Attaching artifact: " + gavCoordinateHelper.getCoordinates(artifactResult) + " "
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

    private ArtifactRepositoryLayout getLayout(final String id) throws MojoExecutionException {
        ArtifactRepositoryLayout layout = repositoryLayouts.get(id);
        if (layout == null) {
            throw new MojoExecutionException("Invalid repository layout: " + id);
        }

        return layout;
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
}
