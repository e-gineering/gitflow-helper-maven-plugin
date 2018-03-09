package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * If there is an ${env.GIT_BRANCH} property, assert that the current ${project.version} is semantically correct for the
 * git branch. Also, make sure there are no SNAPSHOT (plugin) dependencies.
 */
@Mojo(requiresDependencyCollection = ResolutionScope.TEST, name = "enforce-versions", defaultPhase = LifecyclePhase.VALIDATE)
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

                Set<String> snapshotDeps = getSnapshotDeps();
                if (!snapshotDeps.isEmpty()) {
                    throw new MojoFailureException("The current git branch: [" + gitBranch + "] is defined as a release branch. The maven project has the following SNAPSHOT dependencies: " + snapshotDeps.toString());
                }

                Set<String> snapshotPluginDeps = getSnapshotPluginDeps();
                if (!snapshotPluginDeps.isEmpty()) {
                    throw new MojoFailureException("The current git branch: [" + gitBranch + "] is defined as a release branch. The maven project has the following SNAPSHOT plugin dependencies: " + snapshotPluginDeps.toString());
                }

                // Non-master version branches require a pom version match of some kind to the branch subgroups.
                if (gitMatcher.groupCount() > 0 && gitMatcher.group(gitMatcher.groupCount()) != null) {
                    // RELEASE, HOTFIX and SUPPORT branches require a 'starts with' match of the maven project version to the subgroup.
                    // ex: /origin/support/3.1 must have a maven version that starts with "3.1", ala: "3.1.2"
                    if (gitMatcher.groupCount() > 0 && !GitBranchType.MASTER.equals(type)) {
                        String releaseBranchVersion = gitMatcher.group(gitMatcher.groupCount()).trim();
                        // Type check always returns true, as it's in VERSIONED_TYPES and not MASTER, but it's handy documentation
                        if ((GitBranchType.RELEASE.equals(type) || GitBranchType.HOTFIX.equals(type) || GitBranchType.SUPPORT.equals(type)) && !project.getVersion().startsWith(releaseBranchVersion)) {
                            throw new MojoFailureException("The current git branch: [" + gitBranch + "] expected the maven project version to start with: [" + releaseBranchVersion + "], but the maven project version is: [" + project.getVersion() + "]");
                        }
                    }
                }
            }
        } else if (GitBranchType.DEVELOPMENT.equals(type) && !ArtifactUtils.isSnapshot(project.getVersion())) {
            throw new MojoFailureException("The current git branch: [" + gitBranch + "] is detected as the gitflow development branch, and expects a maven project version ending with -SNAPSHOT. The maven project version found was: [" + project.getVersion() + "]");
        }
    }

    private Set<String> getSnapshotDeps() {
        Set<String> snapshotDeps = new HashSet<>();
        for (Artifact dep : project.getArtifacts()) {
            if (ArtifactUtils.isSnapshot(dep.getVersion())) {
                getLog().debug("SNAPSHOT dependency found: " + dep.toString());
                snapshotDeps.add(dep.toString());
            }
        }

        return snapshotDeps;
    }

    private Set<String> getSnapshotPluginDeps() {
        Set<String> snapshotPluginDeps = new HashSet<>();
        for (Artifact plugin : project.getPluginArtifacts()) {
            if (plugin.isSnapshot()) {
                getLog().debug("SNAPSHOT plugin dependency found: " + plugin.toString());
                snapshotPluginDeps.add(plugin.toString());
            }
        }

        return snapshotPluginDeps;
    }
}
