package com.e_gineering;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sets the project release repository and snapshot repository to the proper locations given the current git branch.
 */
@Mojo(name = "determine-deploy-target", defaultPhase = LifecyclePhase.VALIDATE)
public class GitBasedDeployMojo extends AbstractGitEnforcerMojo {

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+)::(.+)::(.+)");

    @Parameter(property = "releaseDeploymentRepository", required = true)
    private String releaseDeploymentRepository;

    @Parameter(property = "testDeploymentRepository", required = true)
    private String testDeploymentRepository;

    @Parameter(property = "snapshotDeploymentRepository", required = true)
    private String snapshotDeploymentRepository;

    @Component
    ArtifactRepositoryFactory repositoryFactory;

    @Component(role = ArtifactRepositoryLayout.class)
    private Map<String, ArtifactRepositoryLayout> repositoryLayouts;

    public void execute() throws MojoExecutionException, MojoFailureException {
        String gitBranch = System.getenv("GIT_BRANCH");
        if (gitBranch != null) {
            getLog().debug("Detected GIT_BRANCH: '" + gitBranch + "' in build environment.");

            /*
             * /origin/master goes to the maven 'release' repo.
             * /origin/release/.* , /origin/hotfix/.* , and /origin/bugfix/.* go to the maven 'test' repo.
             * /origin/development goes to the 'snapshot' repo.
             * All other builds will use the default semantics for 'deploy'.
             */
            if (gitBranch.matches(masterBranch)) {
                // Deploy to the normal release repository.
                getLog().info("Building from master branch. Setting release artifact repository to: [" + releaseDeploymentRepository + "]");
                project.setSnapshotArtifactRepository(null);
                project.setReleaseArtifactRepository(getDeploymentRepository(releaseDeploymentRepository));
            } else if (gitBranch.matches(releaseBranchPattern)) {
                getLog().info("Building from release branch. Setting release artifact repository to: [" + testDeploymentRepository + "]");
                project.setSnapshotArtifactRepository(null);
                project.setReleaseArtifactRepository(getDeploymentRepository(testDeploymentRepository));
            } else if (gitBranch.matches(hotfixBranchPattern)) {
                getLog().info("Building from hotfix branch. Setting release artifact repository to: [" + testDeploymentRepository + "]");
                project.setSnapshotArtifactRepository(null);
                project.setReleaseArtifactRepository(getDeploymentRepository(testDeploymentRepository));
            } else if (gitBranch.matches(bugfixBranchPattern)) {
                getLog().info("Building from bugfix branch. Un-Setting artifact repositories");
                project.setSnapshotArtifactRepository(null);
                project.setReleaseArtifactRepository(null);
            } else if (gitBranch.matches(developmentBranchPattern)) {
                getLog().info("Building from development branch. Setting snapshot artifact repository to: [" + snapshotDeploymentRepository + "]");
                project.setSnapshotArtifactRepository(getDeploymentRepository(snapshotDeploymentRepository));
                project.setReleaseArtifactRepository(null);
            } else {
                getLog().info("Building from arbitrary branch [" + gitBranch + "]. Un-setting artifact repositories.");
                project.setSnapshotArtifactRepository(null);
                project.setReleaseArtifactRepository(null);
            }
        } else {
            getLog().info("No GIT_BRANCH in build environment. Un-setting artifact repositories.");
            project.setSnapshotArtifactRepository(null);
            project.setReleaseArtifactRepository(null);
        }
    }

    private ArtifactRepository getDeploymentRepository(String altRepository) throws MojoExecutionException, MojoFailureException {
        Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher(altRepository);
        if (!matcher.matches()) {
            throw new MojoFailureException(altRepository, "Invalid syntax for repository.",
                    "Invalid syntax for repository. Use \"id::layout::url\".");
        }

        String id = matcher.group(1).trim();
        String layout = matcher.group(2).trim();
        String url = matcher.group(3).trim();

        ArtifactRepositoryLayout repoLayout = getLayout(layout);

        return repositoryFactory.createDeploymentArtifactRepository(id, url, repoLayout, true);
    }

    private ArtifactRepositoryLayout getLayout(String id) throws MojoExecutionException {
        ArtifactRepositoryLayout layout = repositoryLayouts.get(id);
        if (layout == null) {
            throw new MojoExecutionException("Invalid repository layout: " + id);
        }

        return layout;
    }
}
