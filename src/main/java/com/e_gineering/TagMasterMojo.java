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
 * Invokes configures the builds SCM settings based on environment variables from Jenkins, and does an scm:tag
 * If the env.GIT_BRANCH matches the masterBranchPattern.
 */
@Mojo(name = "tag-master", defaultPhase = LifecyclePhase.VERIFY)
public class TagMasterMojo extends AbstractGitEnforcerMojo {

    @Parameter(defaultValue = "${project.version}", property = "tag", required = true)
    private String tag;

    @Component
    private MavenSession mavenSession;

    @Component
    private BuildPluginManager pluginManager;

    public void execute() throws MojoFailureException, MojoExecutionException {
        String gitBranch = System.getenv("GIT_BRANCH");
        String gitURL = System.getenv("GIT_URL");

        if (gitBranch != null && gitURL != null) {
            getLog().info("Detected GIT_BRANCH: '" + gitBranch + "' in build environment.");
            getLog().info("Detected GIT_URL: '" + gitURL + "' in build enviornment.");

            if (gitBranch.matches(masterBranchPattern)) {
                getLog().info("Attempting to invoke an scm:tag execution for a build on the master branch...");

                if (project.getScm() == null) {
                    project.setScm(new Scm());
                }
                project.getScm().setDeveloperConnection("scm:git:" + gitURL);

                executeMojo(
                        plugin(
                                groupId("org.apache.maven.plugins"),
                                artifactId("maven-scm-plugin"),
                                version("1.9.4")
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
                getLog().info("Jenkins build from a non-master branch. Leaving build configuration unaltered.");
            }
        } else {
            getLog().info("Jenkins git environment variables unset or missing. Leaving build configuration unaltered.");
        }
    }
}
