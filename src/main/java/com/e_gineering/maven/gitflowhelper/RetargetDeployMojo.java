package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.model.DistributionManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Set the target repository for deployment based upon the GIT_BRANCH being built.
 */
@Mojo(name = "retarget-deploy", defaultPhase = LifecyclePhase.VALIDATE)
public class RetargetDeployMojo extends AbstractGitflowBasedRepositoryMojo {
    @Override
    protected void execute(final GitBranchType type, final String gitBranch, final String branchPattern) throws MojoExecutionException, MojoFailureException {
        if (project.getDistributionManagement() == null) {
            project.setDistributionManagement(new DistributionManagement());
        }

        switch (type) {
            case SUPPORT:
            case MASTER: {
                getLog().info("Setting release artifact repository to: [" + releaseDeploymentRepository + "]");
                project.setSnapshotArtifactRepository(null);
                project.setReleaseArtifactRepository(getDeploymentRepository(releaseDeploymentRepository));
                break;
            }
            case RELEASE: {
                getLog().info("Setting release artifact repository to: [" + stageDeploymentRepository + "]");
                project.setSnapshotArtifactRepository(null);
                project.setReleaseArtifactRepository(getDeploymentRepository(stageDeploymentRepository));
                break;
            }
            case HOTFIX: {
                getLog().info("Setting release artifact repository to: [" + stageDeploymentRepository + "]");
                project.setSnapshotArtifactRepository(null);
                project.setReleaseArtifactRepository(getDeploymentRepository(stageDeploymentRepository));
                break;
            }
            case DEVELOPMENT: {
                getLog().info("Setting snapshot artifact repository to: [" + snapshotDeploymentRepository + "]");
                project.setSnapshotArtifactRepository(getDeploymentRepository(snapshotDeploymentRepository));
                project.setReleaseArtifactRepository(null);
                break;
            }
            case FEATURE_OR_BUGFIX_BRANCH: {
                if (deploySnapshotTypeBranches) {
                    getLog().info("Setting snapshot artifact repository to: [" + snapshotDeploymentRepository + "]");
                    project.setSnapshotArtifactRepository(getDeploymentRepository(snapshotDeploymentRepository));
                    project.setReleaseArtifactRepository(null);
                } else {
                    unsetRepos();
                }
                break;
            }
            default: {
                unsetRepos();
                break;
            }
        }
    }

    private void unsetRepos() {
        getLog().info("Un-Setting artifact repositories.");
        project.setSnapshotArtifactRepository(null);
        project.setReleaseArtifactRepository(null);
        project.getProperties().put("maven.deploy.skip", "true");
        getLog().info("Setting maven.deploy.skip = 'true'");
    }
}
