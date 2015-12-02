package com.e_gineering;

import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Invokes configures the builds SCM settings based on environment variables from Jenkins, and does an scm:tag
 * If the env.GIT_BRANCH matches the masterBranchPattern.
 */
@Mojo(name = "tag-master", defaultPhase = LifecyclePhase.VALIDATE)
public class TagMasterMojo extends AbstractGitEnforcerMojo {

    @Parameter(defaultValue = "${project.version}", property = "tag", required = true)
    private String tag;

    public void execute() throws MojoFailureException, MojoExecutionException {
        String gitBranch = System.getenv("GIT_BRANCH");
        String gitURL = System.getenv("GIT_URL");

        if (gitBranch != null && gitURL != null) {
            getLog().debug("Detected GIT_BRANCH: '" + gitBranch + "' in build environment.");
            getLog().debug("Detected GIT_URL: '" + gitURL + "' in build enviornment.");

            if (gitBranch.matches(masterBranchPattern)) {
                getLog().debug("Attempting to configure an scm:tag execution for a build on the master branch...");

                Plugin scmPlugin = project.getPluginManagement().getPluginsAsMap().get("org.apache.maven.plugins:maven-scm-plugin");
                if (scmPlugin == null) {
                    getLog().debug("No maven-scm-plugin configuration in plguinManagement. Using defaults.");
                    scmPlugin = new Plugin();
                    scmPlugin.setGroupId("org.apache.maven.plugins");
                    scmPlugin.setArtifactId("maven-scm-plugin");
                } else {
                    getLog().debug("Found maven-scm-plugin in pluginManagement configuration. Using as basis for Plugin.");
                    scmPlugin = scmPlugin.clone();
                }

                // Setup the execution, and add it to our Plugin.
                PluginExecution scmTag = new PluginExecution();
                scmTag.setId("scm-tag-master");
                scmTag.setPhase("verify");
                scmTag.getGoals().add("tag");
                scmPlugin.addExecution(scmTag);

                // Setup the properties for the tag and connectionUrl...
                project.getProperties().put("tag", tag);
                project.getScm().setDeveloperConnection("scm:git:" + gitURL);

                getLog().info("Updating build Plugins to include maven-scm-plugin, with the 'scm:tag' goal bound to the 'verify' phase.");
                project.getModel().getBuild().addPlugin(scmPlugin);
            } else {
                getLog().debug("Jenkins build from a non-master branch. Leaving build configuration unaltered.");
            }
        } else {
            getLog().debug("Jenkins git environment variables unset or missing. Leaving build configuration unaltered.");
        }
    }
}
