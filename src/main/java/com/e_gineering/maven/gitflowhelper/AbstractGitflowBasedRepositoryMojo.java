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

import java.io.File;
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

    @Parameter(defaultValue = "${repositorySystemSession}")
    private RepositorySystemSession session;

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


    /**
     * Resolves artifacts from the given sourceRepository, and attaches them to the current
     * build future downloading.
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


        // Build up a set of ArtifactRequests, for the pom, the current packaging layout, the -sources.jar and the -javadoc.jar and the
        List<ArtifactRequest> requiredArtifacts = new ArrayList<ArtifactRequest>();


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

        // Adjust for archetypes...
        String packaging = project.getPackaging();
        if (project.getPackaging().equalsIgnoreCase("maven-archetype")) {
            packaging = "jar";
        }

        // This artifact is required!
        requiredArtifacts.add(new ArtifactRequest(new DefaultArtifact(project.getGroupId(), project.getArtifactId(), packaging, project.getVersion()), remoteRepositories, null));
        try {
            resolvedArtifacts.addAll(artifactResolver.resolveArtifacts(session, requiredArtifacts));
        } catch (ArtifactResolutionException are) {
            throw new MojoExecutionException("Failed to resolve the required project files from: " + sourceRepository, are);
        }

        // Optional Artifacts... We do these one at a time so we don't fail the build....
        List<ArtifactRequest> optionalArtifacts = new ArrayList<ArtifactRequest>();
        optionalArtifacts.add(new ArtifactRequest(new DefaultArtifact(project.getGroupId(), project.getArtifactId(), "javadoc", "jar", project.getVersion()), remoteRepositories, null));
        optionalArtifacts.add(new ArtifactRequest(new DefaultArtifact(project.getGroupId(), project.getArtifactId(), "sources", "jar", project.getVersion()), remoteRepositories, null));
        optionalArtifacts.add(new ArtifactRequest(new DefaultArtifact(project.getGroupId(), project.getArtifactId(), "tests", "jar", project.getVersion()), remoteRepositories, null));

        for (int i = 0; i < optionalArtifacts.size(); i++) {
            try {
                resolvedArtifacts.add(artifactResolver.resolveArtifact(session, optionalArtifacts.get(i)));
            } catch (ArtifactResolutionException are) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug("Optional Artifact not found: " + optionalArtifacts.get(i).getArtifact());
                }
            }
        }

        getLog().info("Attached " + resolvedArtifacts.size() + " previously built artifacts.");

        for (int i = 0; i < resolvedArtifacts.size(); i++) {
            Artifact artifact = resolvedArtifacts.get(i).getArtifact();
            if (i == 0) {
                project.getArtifact().setFile(artifact.getFile());
            } else {
                projectHelper.attachArtifact(project, artifact.getFile(), artifact.getClassifier());
            }
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
