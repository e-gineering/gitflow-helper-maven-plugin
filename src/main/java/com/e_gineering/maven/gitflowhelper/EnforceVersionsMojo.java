package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * If there is an ${env.GIT_BRANCH} property, assert that the current ${project.version} is semantically correct for the
 * git branch.
 */
@Mojo(name = "enforce-versions", defaultPhase = LifecyclePhase.VALIDATE)
public class EnforceVersionsMojo extends AbstractGitflowBranchMojo {

    private static EnumSet<GitBranchType> versionedTypes = EnumSet.of(GitBranchType.MASTER, GitBranchType.RELEASE, GitBranchType.HOTFIX, GitBranchType.BUGFIX);

    @Override
    protected void execute(final GitBranchType type, final String gitBranch, final String branchPattern) throws MojoExecutionException, MojoFailureException {
        if (versionedTypes.contains(type)) {
            getLog().debug("Versioned Branch Type: " + type + " with branchPattern: " + branchPattern + " Checking against current branch: " + gitBranch);
            Matcher gitMatcher = Pattern.compile(branchPattern).matcher(gitBranch);

            // We're in a release branch, we expect a non-SNAPSHOT version in the POM.
            if (gitMatcher.matches()) {
                if (ArtifactUtils.isSnapshot(project.getVersion())) {
                    throw new MojoFailureException("The current git branch: [" + gitBranch + "] is defined as a release branch. The maven project version: [" + project.getVersion() + "] is currently a snapshot version.");
                }

                // If there is a group 1, expect it to match (exactly) the current projectVersion.
                if (gitMatcher.groupCount() >= 1) {
                    if (!gitMatcher.group(1).trim().equals(project.getVersion().trim())) {
                        throw new MojoFailureException("The current git branch: [" + gitBranch + "] expected the maven project version to be: [" + gitMatcher.group(1).trim() + "], but the maven project version is: [" + project.getVersion() + "]");
                    }
                }
            }
        } else { // Unversioned branch type. Must be -SNAPSHOT.
            if (!ArtifactUtils.isSnapshot(project.getVersion())) {
                throw new MojoFailureException("Builds from non-release git branches must end with -SNAPSHOT");
            }
        }
    }
}
