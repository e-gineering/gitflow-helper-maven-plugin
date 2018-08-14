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
                // Other branches never target release, but may target stage for non-SNAPSHOT artifacts.
                // For this reason, "overwrite" is considered _highly_ dangerous.
                if (!"false".equalsIgnoreCase(forceOtherDeploy)) {
                    // Setup the target based on the base project version.
                    if (ArtifactUtils.isSnapshot(project.getVersion())) {
                        setTargetSnapshots();
                    } else {
                        setTargetStage();
                    }

                    // Monkey with things to do our semVer magic.
                    if ("semVer".equalsIgnoreCase(forceOtherDeploy)) {
                    	if (ArtifactUtils.isSnapshot(project.getVersion())) {
                    		getLog().warn("Maven -SNAPSHOT builds break semVer standards, in that -SNAPSHOT must be the _last_ poriton of a maven version. In semVer, the pre-release status is supposed to come before the build meta-data.");
                    		getLog().info("The gitflow-helper-maven-plugin will inject the build metadata preceding the -SNAPSHOT, allowing for snapshot deployments of this branch.");
	                    }
                        String branchName = gitBranchInfo.getName();
                        String semVerAddition = "+" + branchName.replaceAll("[^0-9^A-Z^a-z^-^.]", "-");

                        updateArtifactVersion(project.getArtifact(), semVerAddition);
                        for (Artifact a : project.getAttachedArtifacts()) {
                        	updateArtifactVersion(a, semVerAddition);
                        }

                        getLog().info("Artifact versions updated with semVer build metadata: " + semVerAddition);
                    }
                    if ("overwrite".equalsIgnoreCase(forceOtherDeploy)) {
                        getLog().warn("DANGER! DANGER, WILL ROBINSON!");
                        getLog().warn("Deployment of this build will OVERWRITE Deployment of " + project.getVersion() + " in the targeted repository.");
                        getLog().warn("THIS IS NOT RECOMMENDED.");
                    }
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
    	String baseVersion = a.getBaseVersion();
    	String version = a.getVersion();

		String semBaseVersion = baseVersion + semVerAdditon;
		String semVersion = version + semVerAdditon;

		if (semBaseVersion.contains("-SNAPSHOT")) {
			semBaseVersion = semBaseVersion.replace("-SNAPSHOT", "") + "-SNAPSHOT";
		}
		if (semVersion.contains("-SNAPSHOT")) {
			semVersion = semVersion.replace("-SNAPSHOT", "") + "-SNAPSHOT";
		}
		a.setBaseVersion(semBaseVersion);
		a.setVersion(semVersion);
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
