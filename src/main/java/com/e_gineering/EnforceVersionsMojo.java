package com.e_gineering;

import org.apache.maven.artifact.ArtifactUtils;
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
public class EnforceVersionsMojo extends AbstractGitEnforcerMojo {

    private String[] versionedBranchPatterns;

    public void execute() throws MojoFailureException {
        String gitBranch = System.getenv("GIT_BRANCH");
        if (gitBranch != null) {
            getLog().debug("Detected GIT_BRANCH: '" + gitBranch + "' in build environment.");

            versionedBranchPatterns = new String[]{
                    masterBranchPattern,
                    releaseBranchPattern,
                    hotfixBranchPattern,
                    bugfixBranchPattern
            };

            Matcher gitMatcher = null;
            int i = 0;
            for (; i < versionedBranchPatterns.length; i++) {
                Pattern branchPattern = Pattern.compile(versionedBranchPatterns[i]);
                gitMatcher = branchPattern.matcher(gitBranch);
                if (!gitMatcher.matches()) {
                    gitMatcher = null;
                } else {
                    getLog().debug("Found matching pattern: " + versionedBranchPatterns[i]);
                    break;
                }
            }

            // Expecting a maven pom with a non-SNAPSHOT version.
            if (gitMatcher != null) {
                if (ArtifactUtils.isSnapshot(project.getVersion())) {
                    throw new MojoFailureException("The current git branch: [" + gitBranch + "] is defined as a release branch. The maven project version: [" + project.getVersion() + "] is currently a snapshot version.");
                }

                // If there is a group 1, expect it to match (exactly) the current projectVersion.
                if (gitMatcher.groupCount() >= 1) {
                    if (!gitMatcher.group(1).trim().equals(project.getVersion().trim())) {
                        throw new MojoFailureException("The current git branch: [" + gitBranch + "] expected the maven project version to be: [" + gitMatcher.group(1).trim() + "], but the maven project version is: [" + project.getVersion() + "]");
                    }
                }
            } else { // No branches matched. This should be a -SNAPSHOT.
                if (!ArtifactUtils.isSnapshot(project.getVersion())) {
                    throw new MojoFailureException("Builds from non-release git branches must end with -SNAPSHOT");
                }
            }
        } else {
            getLog().info("No GIT_BRANCH in build environment. Ignoring assertions for git branch version semantics.");
        }
    }
}
