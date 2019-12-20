package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * When executed, attaches artifacts from a previously deployed (to a repository) build of this
 * project to the current build execution.
 */
@Mojo(name = "attach-deployed", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
@Execute(phase = LifecyclePhase.CLEAN)
public class AttachDeployedArtifactsMojo extends AbstractGitflowBasedRepositoryMojo {
    @Override
    protected void execute(final GitBranchInfo gitBranchInfo) throws MojoExecutionException, MojoFailureException {
        switch (gitBranchInfo.getType()) {
            case MASTER:
            case SUPPORT:
            {
                getLog().info("Attaching artifacts from release repository...");
                attachExistingArtifacts(releaseDeploymentRepository, true);
                break;
            }
            case RELEASE:
            case HOTFIX: {
                getLog().info("Attaching artifacts from stage repository...");
                attachExistingArtifacts(stageDeploymentRepository, true);
                break;
            }
            case DEVELOPMENT: {
                getLog().info("Attaching artifacts from snapshot repository...");
                attachExistingArtifacts(snapshotDeploymentRepository, true);
                break;
            }
            case OTHER: {
                String otherBranchesToDeploy = resolveExpression(otherDeployBranchPattern);
                if (!"".equals(otherBranchesToDeploy) && gitBranchInfo.getName().matches(otherBranchesToDeploy)) {
                    getLog().info("Attaching branch artifacts from snapshot repository...");
                    attachExistingArtifacts(snapshotDeploymentRepository, true);
                    break;
                }
            }
            default: {
                getLog().info("Attaching Artifacts from local repository...");
                // Use the 'local' repository to do this.
                attachExistingArtifacts(null, false);
            }
        }
    }
}
