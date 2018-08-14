package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
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
    protected void execute(final GitBranchInfo gitBranchInfo) throws MojoExecutionException, MojoFailureException {
        if (project.getDistributionManagement() == null) {
            project.setDistributionManagement(new DistributionManagement());
        }

        switch (gitBranchInfo.getType()) {
            case SUPPORT:
            case MASTER: {
                setTargetRelease();
                break;
            }
            case RELEASE:
            case HOTFIX: {
                setTargetStage();
                break;
            }
            case DEVELOPMENT: {
                setTargetSnapshots();
                break;
            }
            case OTHER: {
                // If other branches are set to deploy
                // Other branches never target release, but may target stage for non-SNAPSHOT artifacts.
                // For this reason, "overwrite" is considered _highly_ dangerous.
                getLog().debug("Resolving: " + otherDeployBranchPattern);
                String otherBranchesToDeploy = resolveExpression(otherDeployBranchPattern);
                getLog().debug("Resolved to: " + otherBranchesToDeploy);
	            if (!"".equals(otherBranchesToDeploy) && gitBranchInfo.getName().matches(otherBranchesToDeploy)) {
                    setTargetSnapshots();

                    String branchName = gitBranchInfo.getName();
                    String semVerAddition = "+" + branchName.replaceAll("[^0-9^A-Z^a-z^-^.]", "-") + "-SNAPSHOT";

                    updateArtifactVersion(project.getArtifact(), semVerAddition);
                    for (Artifact a : project.getAttachedArtifacts()) {
                        updateArtifactVersion(a, semVerAddition);
                    }

                    getLog().info("Artifact versions updated with semVer build metadata: " + semVerAddition);
                    break;
                }
            }
            default: {
                unsetRepos();
                break;
            }
        }
    }

    private void updateArtifactVersion(final Artifact a, final String semVerAdditon) {
        // Handle null-safety. In some cases Projects don't have primary artifacts.
        if (a != null) {
            // If the version contains -SNAPSHOT, replace it with ""
            a.setVersion(a.getVersion().replace("-SNAPSHOT", "") + semVerAdditon);
            a.setBaseVersion(a.getBaseVersion().replace("-SNAPSHOT", "") + semVerAdditon);
        }
    }


    private void setTargetSnapshots() throws MojoExecutionException, MojoFailureException {
        getLog().info("Setting snapshot artifact repository to: [" + snapshotDeploymentRepository + "]");
        project.setSnapshotArtifactRepository(getDeploymentRepository(snapshotDeploymentRepository));
        project.setReleaseArtifactRepository(null);
    }

    private void setTargetStage() throws MojoExecutionException, MojoFailureException {
        getLog().info("Setting release artifact repository to: [" + stageDeploymentRepository + "]");
        project.setSnapshotArtifactRepository(null);
        project.setReleaseArtifactRepository(getDeploymentRepository(stageDeploymentRepository));
    }

    private void setTargetRelease() throws MojoExecutionException, MojoFailureException {
        getLog().info("Setting release artifact repository to: [" + releaseDeploymentRepository + "]");
        project.setSnapshotArtifactRepository(null);
        project.setReleaseArtifactRepository(getDeploymentRepository(releaseDeploymentRepository));
    }

    private void unsetRepos() {
        getLog().info("Un-Setting artifact repositories.");
        project.setSnapshotArtifactRepository(null);
        project.setReleaseArtifactRepository(null);
        project.getProperties().put("maven.deploy.skip", "true");
        getLog().info("Setting maven.deploy.skip = 'true'");
    }
}
