package com.e_gineering;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Scm;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 * Invokes configures the builds SCM settings based on environment variables from a CI Server, and does an scm:tag
 * If the env.GIT_BRANCH matches the masterBranchPattern.
 */
@Mojo(name = "tag-master", defaultPhase = LifecyclePhase.DEPLOY)
public class TagMasterMojo extends AbstractGitEnforcerMojo {

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

    public void execute() throws MojoFailureException, MojoExecutionException {
        String gitBranch = System.getenv("GIT_BRANCH");
        String gitURL = System.getenv("GIT_URL");

        if (gitBranch != null && gitURL != null) {
            getLog().debug("Detected GIT_BRANCH: '" + gitBranch + "' in build environment.");
            getLog().debug("Detected GIT_URL: '" + gitURL + "' in build environment.");

            if (gitBranch.matches(masterBranchPattern)) {
                getLog().info("Invoking scm:tag for CI build matching masterBranchPattern: [" + masterBranchPattern + "]");

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
                getLog().debug("CI build from a non-master branch. Leaving build configuration unaltered.");
            }
        } else {
            getLog().debug("CI git environment variables unset or missing. Leaving build configuration unaltered.");
        }
    }
}
