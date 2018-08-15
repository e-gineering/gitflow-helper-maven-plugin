package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * If there is an ${env.GIT_BRANCH} property, assert that the current ${project.version} is semantically correct for the
 * git branch. Also, make sure there are no SNAPSHOT (plugin) dependencies if enforceNonSnapshots = true.
 */
@Mojo(requiresDependencyCollection = ResolutionScope.TEST, name = "enforce-versions", defaultPhase = LifecyclePhase.VALIDATE)
public class EnforceVersionsMojo extends AbstractGitflowBranchMojo {

    @Parameter(defaultValue = "true", property = "enforceNonSnapshots", required = true)
    private boolean enforceNonSnapshots;

    @Parameter(defaultValue = "false", property = "allowGitflowPluginSnapshot", required = true)
    private boolean allowGitflowPluginSnapshot;

    @Override
    protected void execute(final GitBranchInfo branchInfo) throws MojoExecutionException, MojoFailureException {
        if (branchInfo.isVersioned()) {
            getLog().debug("Versioned Branch: " + branchInfo);
            Matcher gitMatcher = Pattern.compile(branchInfo.getPattern()).matcher(branchInfo.getName());

            // We're in a versioned branch, we expect a non-SNAPSHOT version in the POM.
            if (gitMatcher.matches()) {
                // Always assert that pom versions match our expectations.
                if (hasSnapshotInModel(project)) {
                    throw new MojoFailureException("The current git branch: [" + branchInfo.getName() + "] is defined as a release branch. The maven project or one of its parents is currently a snapshot version.");
                }

                // Non-master version branches require a pom version match of some kind to the branch subgroups.
                if (gitMatcher.groupCount() > 0 && gitMatcher.group(gitMatcher.groupCount()) != null) {
                    checkReleaseTypeBranchVersion(branchInfo, gitMatcher);
                }

                // Optionally (default true) reinforce that no dependencies may be snapshots.
                if (enforceNonSnapshots) {
                    Set<String> snapshotDeps = getSnapshotDeps();
                    if (!snapshotDeps.isEmpty()) {
                        throw new MojoFailureException("The current git branch: [" + branchInfo.getName() + "] is defined as a release branch. The maven project has the following SNAPSHOT dependencies: " + snapshotDeps.toString());
                    }

                    Set<String> snapshotPluginDeps = getSnapshotPluginDeps();
                    if (!snapshotPluginDeps.isEmpty()) {
                        throw new MojoFailureException("The current git branch: [" + branchInfo.getName() + "] is defined as a release branch. The maven project has the following SNAPSHOT plugin dependencies: " + snapshotPluginDeps.toString());
                    }
                }
            }
        } else if (branchInfo.isSnapshot() && !ArtifactUtils.isSnapshot(project.getVersion())) {
            throw new MojoFailureException("The current git branch: [" + branchInfo.getName() + "] is detected as a SNAPSHOT-type branch, and expects a maven project version ending with -SNAPSHOT. The maven project version found was: [" + project.getVersion() + "]");
        }
    }

    private void checkReleaseTypeBranchVersion(final GitBranchInfo branchInfo, final Matcher gitMatcher) throws MojoFailureException {
        // RELEASE, HOTFIX and SUPPORT branches require a match of the maven project version to the subgroup.
        // Depending on the value of the 'releaseBranchMatchType' param, it's either 'equals' or 'startsWith'.
        if ("equals".equals(releaseBranchMatchType)) {
            // HOTFIX and RELEASE branches require an exact match to the last subgroup.
            if ((GitBranchType.RELEASE.equals(branchInfo.getType()) || GitBranchType.HOTFIX.equals(branchInfo.getType())) && !gitMatcher.group(gitMatcher.groupCount()).trim().equals(project.getVersion().trim())) {
                throw new MojoFailureException("The current git branch: [" + branchInfo.getName() + "] expected the maven project version to be: [" + gitMatcher.group(gitMatcher.groupCount()).trim() + "], but the maven project version is: [" + project.getVersion() + "]");
            }

            // SUPPORT branches require a 'starts with' match of the maven project version to the subgroup.
            // ex: /origin/support/3.1 must have a maven version that starts with "3.1", ala: "3.1.2"
            if (GitBranchType.SUPPORT.equals(branchInfo.getType()) && !project.getVersion().startsWith(gitMatcher.group(gitMatcher.groupCount()).trim())) {
                throw new MojoFailureException("The current git branch: [" + branchInfo.getName() + "] expected the maven project version to start with: [" + gitMatcher.group(gitMatcher.groupCount()).trim() + "], but the maven project version is: [" + project.getVersion() + "]");
            }
        } else { // "startsWith"
            // ex: /origin/release/3.1 must have a maven version that starts with "3.1", ala: "3.1.2"
            if (gitMatcher.groupCount() > 0 && !GitBranchType.MASTER.equals(branchInfo.getType())) {
                String releaseBranchVersion = gitMatcher.group(gitMatcher.groupCount()).trim();
                // Type check always returns true, as it's in VERSIONED_TYPES and not MASTER, but it's handy documentation
                if ((GitBranchType.RELEASE.equals(branchInfo.getType()) || GitBranchType.HOTFIX.equals(branchInfo.getType()) || GitBranchType.SUPPORT.equals(branchInfo.getType())) && !project.getVersion().startsWith(releaseBranchVersion)) {
                    throw new MojoFailureException("The current git branch: [" + branchInfo.getName() + "] expected the maven project version to start with: [" + releaseBranchVersion + "], but the maven project version is: [" + project.getVersion() + "]");
                }
            }
        }
    }

    private boolean hasSnapshotInModel(final MavenProject project) {
        MavenProject parent = project.getParent();

        boolean projectIsSnapshot = ArtifactUtils.isSnapshot(project.getVersion());
        boolean parentIsSnapshot = parent != null && hasSnapshotInModel(parent);

        return projectIsSnapshot || parentIsSnapshot;
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
                if (allowGitflowPluginSnapshot && plugin.getGroupId().equals("com.e-gineering") && plugin.getArtifactId().equals("gitflow-helper-maven-plugin")) {
                    getLog().warn("SNAPSHOT com.e-gineering:gitflow-helper-maven-plugin detected. Allowing for this build.");
                    continue;
                }
                getLog().debug("SNAPSHOT plugin dependency found: " + plugin.toString());
                snapshotPluginDeps.add(plugin.toString());
            }
        }

        return snapshotPluginDeps;
    }
}
