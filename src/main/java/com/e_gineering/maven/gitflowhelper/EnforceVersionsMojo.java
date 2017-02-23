package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * If there is an ${env.GIT_BRANCH} property, assert that the current ${project.version} is semantically correct for the
 * git branch.
 */
@Mojo(name = "enforce-versions", defaultPhase = LifecyclePhase.VALIDATE)
public class EnforceVersionsMojo extends AbstractGitflowBranchMojo {

    @Override
    protected void execute(final GitBranchType type, final String gitBranch, final String branchPattern) throws MojoExecutionException, MojoFailureException {
        if (GitBranchType.VERSIONED_TYPES.contains(type)) {
            getLog().debug("Versioned Branch Type: " + type + " with branchPattern: " + branchPattern + " Checking against current branch: " + gitBranch);
            Matcher gitMatcher = Pattern.compile(branchPattern).matcher(gitBranch);

            // We're in a release branch, we expect a non-SNAPSHOT version in the POM.
            if (gitMatcher.matches()) {
                if (ArtifactUtils.isSnapshot(project.getVersion())) {
                    throw new MojoFailureException("The current git branch: [" + gitBranch + "] is defined as a release branch. The maven project version: [" + project.getVersion() + "] is currently a snapshot version.");
                }

                // Expect the last group on non-master (and non-support) branches to match (exactly) the current projectVersion. (only release / hotfix branches)
                if (gitMatcher.groupCount() > 0 && !(GitBranchType.MASTER.equals(type) || GitBranchType.SUPPORT.equals(type))) {
                    if (!gitMatcher.group(gitMatcher.groupCount()).trim().equals(project.getVersion().trim())) {
                        throw new MojoFailureException("The current git branch: [" + gitBranch + "] expected the maven project version to be: [" + gitMatcher.group(gitMatcher.groupCount()).trim() + "], but the maven project version is: [" + project.getVersion() + "]");
                    }
                }
            }
        } else if (GitBranchType.DEVELOPMENT.equals(type) && !ArtifactUtils.isSnapshot(project.getVersion())) {
            throw new MojoFailureException("The current git branch: [" + gitBranch + "] is detected as the gitflow development branch, and expects a maven project version ending with -SNAPSHOT. The maven project version found was: [" + project.getVersion() + "]");
        }
    }
}
