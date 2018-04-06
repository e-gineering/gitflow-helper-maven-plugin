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

            // We're in a versioned branch, we expect a non-SNAPSHOT version in the POM.
            if (gitMatcher.matches()) {
                checkForSnapshots(gitBranch);

                // Non-master version branches require a pom version match of some kind to the branch subgroups.
                if (gitMatcher.groupCount() > 0 && gitMatcher.group(gitMatcher.groupCount()) != null) {
                    checkReleaseTypeBranchVersion(type, gitBranch, gitMatcher);
                }
            }
        } else if (GitBranchType.SNAPSHOT_TYPES.contains(type) && !ArtifactUtils.isSnapshot(project.getVersion())) {
            throw new MojoFailureException("The current git branch: [" + gitBranch + "] is detected as a SNAPSHOT-type branch, and expects a maven project version ending with -SNAPSHOT. The maven project version found was: [" + project.getVersion() + "]");
        } else if (GitBranchType.FEATURE_OR_BUGFIX_BRANCH.equals(type) && deploySnapshotTypeBranches) {
            checkFeatureOrBugfixBranchVersion(gitBranch, branchPattern);
        }
    }

    private void checkFeatureOrBugfixBranchVersion(String gitBranch, String branchPattern) throws MojoFailureException {
        // For FEATURE and BUGFIX branches, check if the POM version includes the branch name
        Matcher gitMatcher = Pattern.compile(branchPattern).matcher(gitBranch);
        if (gitMatcher.matches()) {
            String branchName = gitMatcher.group(gitMatcher.groupCount());
            String v = project.getVersion();
            String branchNameSnapshot = branchName + "-" + Artifact.SNAPSHOT_VERSION;
            if (v.length() < branchNameSnapshot.length() || !v.regionMatches(
                    true,
                    v.length() - branchNameSnapshot.length(),
                    branchNameSnapshot,
                    0,
                    branchNameSnapshot.length())
                    ) {
                throw new MojoFailureException("The project's version should end with [" + branchNameSnapshot + "]");
            }
        }
    }

    private void checkReleaseTypeBranchVersion(GitBranchType type, String gitBranch, Matcher gitMatcher) throws MojoFailureException {
        // RELEASE, HOTFIX and SUPPORT branches require a match of the maven project version to the subgroup.
        // Depending on the value of the 'releaseBranchMatchType' param, it's either 'equals' or 'startsWith'.
        if ("equals".equals(releaseBranchMatchType)) {
            // HOTFIX and RELEASE branches require an exact match to the last subgroup.
            if ((GitBranchType.RELEASE.equals(type) || GitBranchType.HOTFIX.equals(type)) && !gitMatcher.group(gitMatcher.groupCount()).trim().equals(project.getVersion().trim())) {
                throw new MojoFailureException("The current git branch: [" + gitBranch + "] expected the maven project version to be: [" + gitMatcher.group(gitMatcher.groupCount()).trim() + "], but the maven project version is: [" + project.getVersion() + "]");
            }

            // SUPPORT branches require a 'starts with' match of the maven project version to the subgroup.
            // ex: /origin/support/3.1 must have a maven version that starts with "3.1", ala: "3.1.2"
            if (GitBranchType.SUPPORT.equals(type) && !project.getVersion().startsWith(gitMatcher.group(gitMatcher.groupCount()).trim())) {
                throw new MojoFailureException("The current git branch: [" + gitBranch + "] expected the maven project version to start with: [" + gitMatcher.group(gitMatcher.groupCount()).trim() + "], but the maven project version is: [" + project.getVersion() + "]");
            }
        } else { // "startsWith"
            // ex: /origin/release/3.1 must have a maven version that starts with "3.1", ala: "3.1.2"
            if (gitMatcher.groupCount() > 0 && !GitBranchType.MASTER.equals(type)) {
                String releaseBranchVersion = gitMatcher.group(gitMatcher.groupCount()).trim();
                // Type check always returns true, as it's in VERSIONED_TYPES and not MASTER, but it's handy documentation
                if ((GitBranchType.RELEASE.equals(type) || GitBranchType.HOTFIX.equals(type) || GitBranchType.SUPPORT.equals(type)) && !project.getVersion().startsWith(releaseBranchVersion)) {
                    throw new MojoFailureException("The current git branch: [" + gitBranch + "] expected the maven project version to start with: [" + releaseBranchVersion + "], but the maven project version is: [" + project.getVersion() + "]");
                }
            }
        }
    }

    private void checkForSnapshots(String gitBranch) throws MojoFailureException {
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
