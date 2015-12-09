package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 * Invokes configures the builds SCM settings based on environment variables from a CI Server, and does an scm:tag for builds from Master.
 */
@Mojo(name = "tag-master", defaultPhase = LifecyclePhase.DEPLOY)
public class TagMasterMojo extends AbstractGitflowBranchMojo {

    // @Parameter tag causes property resolution to fail for patterns containing ${env.}. Default provided in execute();
    @Parameter(property = "gitURLProperty")
    private String gitURLProperty;

    @Parameter(defaultValue = "${project.version}", property = "tag", required = true)
    private String tag;

    @Parameter(defaultValue = "org.apache.maven.plugins", property = "tag.plugin.groupId", required = true)
    private String tagGroupId;

    @Parameter(defaultValue = "maven-scm-plugin", property = "tag.plugin.artifactId", required = true)
    private String tagArtifactId;

    @Parameter(defaultValue = "1.9.4", property = "tag.plugin.version", required = true)
    private String tagVersion;

    @Component
    private MavenSession mavenSession;

    @Component
    private BuildPluginManager pluginManager;

    @Override
    protected void execute(final GitBranchType type, final String gitBranch, final String branchPattern) throws MojoExecutionException, MojoFailureException {
        if (type.equals(GitBranchType.MASTER)) {
            if (gitURLProperty == null) {
                gitURLProperty = "${env.GIT_URL}";
            }
            String gitURL = resolveExpression(gitURLProperty);
            if (StringUtils.isNotEmpty(gitURL)) {
                getLog().debug("Detected GIT_URL: '" + gitURL + "' in build environment.");
                getLog().info("Invoking scm:tag for CI build matching branchPattern: [" + branchPattern + "]");

                // Use the execute mojo to run the maven-scm-plugin...
                executeMojo(
                        plugin(
                                groupId(tagGroupId),
                                artifactId(tagArtifactId),
                                version(tagVersion)
                        ),
                        goal("tag"),
                        configuration(
                                element(name("tag"), tag),
                                element(name("developerConnectionUrl"), "scm:git:" + gitURL)
                        ),
                        executionEnvironment(
                                project,
                                mavenSession,
                                pluginManager
                        )
                );
            } else {
                getLog().debug("CI git environment variables unset or missing. Leaving build configuration unaltered.");
            }
        }
    }
}
