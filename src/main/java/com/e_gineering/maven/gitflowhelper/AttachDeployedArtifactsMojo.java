package com.e_gineering.maven.gitflowhelper;

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
    protected void execute(GitBranchType type, String gitBranch, String branchPattern) throws MojoExecutionException, MojoFailureException {
        getLog().info("Attaching existing artifacts...");
        switch (type) {
            case MASTER: {
                attachExistingArtifacts(releaseDeploymentRepository, true);
                break;
            }
            case RELEASE: {
                attachExistingArtifacts(stageDeploymentRepository, true);
                break;
            }
            case HOTFIX: {
                attachExistingArtifacts(stageDeploymentRepository, true);
                break;
            }
            case DEVELOPMENT: {
                attachExistingArtifacts(snapshotDeploymentRepository, true);
                break;
            }
            default: {
                // Use the 'local' repository to do this.
                attachExistingArtifacts(null, false);
            }
        }
        getLog().info("Done");
    }
}
