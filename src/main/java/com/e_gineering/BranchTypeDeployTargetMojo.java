package com.e_gineering;

import org.apache.maven.model.DeploymentRepository;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Set the target repository for deployment based upon the GIT_BRANCH being built.
 */
@Mojo(name = "retarget-deploy", defaultPhase = LifecyclePhase.VALIDATE)
public class BranchTypeDeployTargetMojo extends AbstractGitBasedDeployMojo {
    @Override
    protected void execute(final GitBranchType type) throws MojoExecutionException, MojoFailureException {
        if (project.getDistributionManagement() == null) {
            project.setDistributionManagement(new DistributionManagement());
        }

        switch (type) {
            case MASTER: {
                getLog().info("Building from master branch. Setting release artifact repository to: [" + releaseDeploymentRepository + "]");
                project.setSnapshotArtifactRepository(null);
                project.setReleaseArtifactRepository(getDeploymentRepository(releaseDeploymentRepository));
                break;
            }
            case RELEASE: {
                getLog().info("Building from release branch. Setting release artifact repository to: [" + testDeploymentRepository + "]");
                project.setSnapshotArtifactRepository(null);
                project.setReleaseArtifactRepository(getDeploymentRepository(testDeploymentRepository));
                break;
            }
            case HOTFIX: {
                getLog().info("Building from hotfix branch. Setting release artifact repository to: [" + testDeploymentRepository + "]");
                project.setSnapshotArtifactRepository(null);
                project.setReleaseArtifactRepository(getDeploymentRepository(testDeploymentRepository));
                break;
            }
            case BUGFIX: {
                getLog().info("Building from bugfix branch. Un-Setting artifact repositories");
                project.setSnapshotArtifactRepository(null);
                project.setReleaseArtifactRepository(null);
                break;
            }
            case DEVELOPMENT: {
                getLog().info("Building from development branch. Setting snapshot artifact repository to: [" + snapshotDeploymentRepository + "]");
                project.setSnapshotArtifactRepository(getDeploymentRepository(snapshotDeploymentRepository));
                project.setReleaseArtifactRepository(null);
                break;
            }
            case OTHER: {
                getLog().info("Building from arbitrary branch [" + gitBranch + "]. Un-setting artifact repositories.");
                project.setSnapshotArtifactRepository(null);
                project.setReleaseArtifactRepository(null);
            }
        }
    }
}
