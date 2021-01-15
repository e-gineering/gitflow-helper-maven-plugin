package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.versioning.VersionRange;
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
public class RetargetDeployMojo extends AbstractGitflowBasedRepositoryMojo {
    @Override
    protected void execute(final GitBranchInfo gitBranchInfo) throws MojoExecutionException, MojoFailureException {
        // Ensure we have a 'null' distribution management for other plugins which expect it.
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
                String otherBranchesToDeploy = resolveExpression(otherDeployBranchPattern);
	            if (!"".equals(otherBranchesToDeploy) && gitBranchInfo.getName().matches(otherBranchesToDeploy)) {
                    setTargetSnapshots();

                    project.setVersion(getAsBranchSnapshotVersion(project.getVersion(), gitBranchInfo.getName()));

                    // Update any attached artifacts.
                    updateArtifactVersion(project.getArtifact(), gitBranchInfo.getName());
                    for (Artifact a : project.getAttachedArtifacts()) {
                        updateArtifactVersion(a, gitBranchInfo.getName());
                    }

                    getLog().info("Artifact versions updated with build metadata: " + getAsBranchSnapshotVersion("", gitBranchInfo.getName()));
                    break;
                }
            }
            default: {
                unsetRepos();
                break;
            }
        }
    }

    /**
     * Updates artifact versions for a given branch name.
     * @param a artifact to update (may be null)
     * @param branchName the branch name
     */
    private void updateArtifactVersion(Artifact a, String branchName) {
        if (a != null) {
            a.setVersion(getAsBranchSnapshotVersion(a.getVersion(), branchName));
            try {
                a.setVersionRange(VersionRange.createFromVersion(a.getVersion()));
            } catch (UnsupportedOperationException uoe) { // Some artifact types don't like this.
                getLog().debug("Unable to update VersionRange for artifact.");
            }
        }
    }


    /**
     * Given a String version (which may be a final or -SNAPSHOT version) return a
     * version version string mangled to include a `+normalized-branch-name-SNAPSHOT format version.
     *
     * @param version The base version (ie, 1.0.2-SNAPSHOT)
     * @param branchName to be normalized
     * @return A mangled version string with the branchname and -SNAPSHOT.
     */
    private String getAsBranchSnapshotVersion(final String version, final String branchName) {
        return version.replace("-SNAPSHOT", "") + otherBranchVersionDelimiter + branchName.replaceAll("[^0-9A-Za-z-.]", "-") + "-SNAPSHOT";
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
