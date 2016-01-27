package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
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
    private RepositorySystemSession session;

    @Parameter(property = "primaryArtifactType", defaultValue = "jar", required = true)
    private String primaryArtifactType;

    @Parameter(property = "overwritePrimaryArtifact", defaultValue = "true", required = true)
    private Boolean overwritePrimaryArtifact;

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
        if (!matcher.matches()) {
            throw new MojoFailureException(altRepository, "Invalid syntax for repository.",
                    "Invalid syntax for repository. Use \"id::layout::url::unique\".");
        }

        String id = matcher.group(1).trim();
        String layout = matcher.group(2).trim();
        String url = matcher.group(3).trim();
        boolean unique = Boolean.parseBoolean(matcher.group(4).trim());

        ArtifactRepositoryLayout repoLayout = getLayout(layout);

        return repositoryFactory.createDeploymentArtifactRepository(id, url, repoLayout, unique);
    }

    /**
     * Builds a RemoteRepository for resolving artifacts.
     *
     * @param altRepository
     * @return
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    private RemoteRepository getRepository(final String altRepository) throws MojoExecutionException, MojoFailureException {
        Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher(altRepository);
        if (!matcher.matches()) {
            throw new MojoFailureException(altRepository, "Invalid syntax for repository.",
                    "Invalid syntax for repository. Use \"id::layout::url::unique\".");
        }

        String id = matcher.group(1).trim();
        String layout = matcher.group(2).trim();
        String url = matcher.group(3).trim();
        boolean unique = Boolean.parseBoolean(matcher.group(4).trim());

        ArtifactRepositoryLayout repoLayout = getLayout(layout);

        return new RemoteRepository.Builder(id, layout, url).build();
    }

    private String toCoordinates(org.apache.maven.artifact.Artifact artifact) {
        StringBuilder result = new StringBuilder();

        if (artifact.getFile() != null && artifact.getFile().exists() && !artifact.getFile().isDirectory()) {
            getLog().debug("   Encoding Coordinates For: " + artifact.getFile().getAbsolutePath());
            // group:artifact:extension
            result.append(project.getGroupId()).append(":").append(project.getArtifactId()).append(":").append(artifact.getType());
            if (artifact.hasClassifier()) {
                // :classifier
                result.append(":").append(artifact.getClassifier());
            }
            result.append(":").append(project.getVersion());
        }

        return result.toString().trim();
    }

    /**
     * Creates and attaches an artifact containing a list of attached artifacts, each line in the file contains
     * group:artifact:type:classifier:version
     */
    protected void attachArtifactCatalog() throws MojoExecutionException {
        getLog().info("Attaching artifact catalog...");
        File catalog = new File(project.getBuild().getDirectory(), project.getArtifact().getArtifactId() + ".txt");

        PrintWriter writer = null;

        try {
            catalog.delete();
            writer = new PrintWriter(new FileOutputStream(catalog));

            String coords = toCoordinates(project.getArtifact());
            if (!coords.isEmpty()) {
                getLog().debug("   Primary Artifact: " + coords);
                writer.println(coords);
            }

            // Iterate the attached artifacts.
            for (org.apache.maven.artifact.Artifact artifact : project.getAttachedArtifacts()) {
                coords = toCoordinates(artifact);
                getLog().debug("   Artifact: " + coords);
                if (!coords.isEmpty()) {
                    writer.println(coords);
                    getLog().info(coords);
                }
            }

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
            getLog().debug("Attaching existing artifacts from local repository only.");
        } else {
            // Add the remote repository.
            remoteRepositories.addAll(Arrays.asList(getRepository(sourceRepository)));
        }

        // A place to store our resolved files...
        List<ArtifactResult> resolvedArtifacts = new ArrayList<ArtifactResult>();

        // Keep track of the original base directory.
        Field localBaseDir = null;
        File originalBaseDir = session.getLocalRepositoryManager().getRepository().getBasedir();

        // Disable the local repository.
        if (disableLocal) {
            getLog().info("Disabling local repository @ " + session.getLocalRepository().getBasedir());
            try {
                localBaseDir = LocalRepository.class.getDeclaredField("basedir");
                localBaseDir.setAccessible(true);

                // Generate a new temp directory.
                File tempRepo = Files.createTempDirectory("gitflow-helper-maven-plugin-repo").toFile();
                tempRepo.deleteOnExit();

                getLog().info("Using temporary local repository @ " + tempRepo.getAbsolutePath());
                localBaseDir.set(session.getLocalRepositoryManager().getRepository(), tempRepo);
            } catch (Exception ex) {
                getLog().warn("Failed to disable local repository path.", ex);
            }
        }


        List<ArtifactRequest> requiredArtifacts = new ArrayList<ArtifactRequest>();

        // Locate our text catalog classifier file. :-)
        BufferedReader reader = null;
        try {
            ArtifactResult catalogResult = artifactResolver.resolveArtifact(session, new ArtifactRequest(new DefaultArtifact(project.getGroupId(), project.getArtifactId(), "catalog", "txt", project.getVersion()), remoteRepositories, null));
            resolvedArtifacts.add(catalogResult);

            if (catalogResult.isResolved()) {
                // Read the file line by line...
                reader = new BufferedReader(new FileReader(catalogResult.getArtifact().getFile()));

                String coords = null;

                while((coords = reader.readLine()) != null) {
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
                } catch (IOException ioe) {};
            }
        }


        try {
            resolvedArtifacts.addAll(artifactResolver.resolveArtifacts(session, requiredArtifacts));
        } catch (ArtifactResolutionException are) {
            throw new MojoExecutionException("Failed to resolve the required project files from: " + sourceRepository, are);
        }

        for (ArtifactResult artifactResult : resolvedArtifacts) {
            Artifact artifact = artifactResult.getArtifact();
            projectHelper.attachArtifact(project, artifact.getExtension(), artifact.getClassifier(), artifact.getFile());
            artifact.getFile().deleteOnExit();
        }

        // Restore the local repository.
        if (disableLocal) {
            try {
                localBaseDir.set(session.getLocalRepositoryManager().getRepository(), originalBaseDir);
                localBaseDir.setAccessible(false);
            } catch (Exception ex) {
                getLog().warn("Failed to restore original local repository path.", ex);
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
