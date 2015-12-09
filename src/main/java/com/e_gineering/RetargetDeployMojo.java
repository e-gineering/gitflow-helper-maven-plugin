package com.e_gineering;

import org.apache.maven.model.DistributionManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Set the target repository for deployment based upon the GIT_BRANCH being built.
 */
@Mojo(name = "retarget-deploy", defaultPhase = LifecyclePhase.VALIDATE)
public class RetargetDeployMojo extends AbstractGitBasedDeployMojo {
    @Override
    protected void execute(final GitBranchType type) throws MojoExecutionException, MojoFailureException {
        if (project.getDistributionManagement() == null) {
            project.setDistributionManagement(new DistributionManagement());
        }

        switch (type) {
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
            default: {
                getLog().info("Un-Setting artifact repositories");
                project.setSnapshotArtifactRepository(null);
                project.setReleaseArtifactRepository(null);
                break;
            }
        }
    }
}
