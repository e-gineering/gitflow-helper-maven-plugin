package com.e_gineering.maven.gitflowhelper;

import com.e_gineering.maven.gitflowhelper.properties.PropertyResolver;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.internal.MojoDescriptorCreator;
import org.apache.maven.model.Plugin;
import org.apache.maven.monitor.logging.DefaultLog;
import org.apache.maven.plugin.prefix.NoPluginFoundForPrefixException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.manager.ScmManager;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

/**
 * Maven extension which removes (skips) undesired plugins from the build reactor when running on a master branch.
 * <p/>
 * Essentially, enables using the master branch as a 'promotion' branch.
 */
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "promote-master")
public class MasterPromoteExtension extends AbstractMavenLifecycleParticipant {

    @Requirement
    private MojoDescriptorCreator descriptorCreator;

    @Requirement
    private Logger logger;

    @Requirement
    protected ScmManager scmManager;

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        Properties systemEnvVars = null;
        try {
            systemEnvVars = CommandLineUtils.getSystemEnvVars();
        } catch (IOException ioe) {
            throw new MavenExecutionException("Unable to read System Envirionment Variables: ", ioe);
        }

        // Look for a gitflow-helper-maven-plugin, so we can determine what the gitBranchExpression and masterBranchPattern are...
        String masterBranchPattern = null;

        String gitBranchExpression = null;
        boolean pluginFound = false;

        // Any plugin which is part of the project goals needs to be retained.
        List<Plugin> pluginsToRetain = new ArrayList<Plugin>(session.getGoals().size());

        List<String> goals = session.getGoals();
        for (String goal : goals) {
            int delimiter = goal.indexOf(":");
            if (delimiter != -1) {
                String prefix = goal.substring(0, delimiter);
                try {
                    pluginsToRetain.add(descriptorCreator.findPluginForPrefix(prefix, session));
                } catch (NoPluginFoundForPrefixException ex) {
                    throw new MavenExecutionException("Unable to resolve plugin for prefix: " + prefix, ex);
                }
            }
        }

        // Build up a map of plugins to remove from projects, if we're on the master branch.
        HashMap<MavenProject, List<Plugin>> pluginsToDrop = new HashMap<MavenProject, List<Plugin>>();

        for (MavenProject project : session.getProjects()) {
            List<Plugin> dropPlugins = new ArrayList<Plugin>();

            for (Plugin plugin : project.getBuildPlugins()) {
                // Don't drop our plugin. Read it's config
                if (plugin.getKey().equals("com.e-gineering:gitflow-helper-maven-plugin")) {
                    pluginFound = true;

                    logger.debug("gitflow-helper-maven-plugin found in project: [" + project.getName() + "]");

                    if (masterBranchPattern == null) {
                        masterBranchPattern = extractPluginConfigValue("masterBranchPattern", plugin);
                    }

                    if (gitBranchExpression == null) {
                        gitBranchExpression = extractPluginConfigValue("gitBranchExpression", plugin);
                    }
                    // Don't drop things we declare goals for.
                } else if (pluginsToRetain.contains(plugin)) {
                    logger.debug("gitflow-helper-maven-plugin retaining plugin: " + plugin + " from project: " + project.getName());
                    // Don't drop the maven-deploy-plugin
                } else if (plugin.getKey().equals("org.apache.maven.plugins:maven-deploy-plugin")) {
                    logger.debug("gitflow-helper-maven-plugin retaining plugin: " + plugin + " from project: " + project.getName());
                } else {
                    logger.debug("gitflow-helper-maven-plugin removing plugin: " + plugin + " from project: " + project.getName());
                    dropPlugins.add(plugin);
                }
            }

            if (gitBranchExpression == null) {
                gitBranchExpression = ScmUtils.resolveBranchOrExpression(scmManager, project, new DefaultLog(logger));
            }

            pluginsToDrop.put(project, dropPlugins);
        }

        if (pluginFound) {
            if (masterBranchPattern == null) {
                logger.debug("Using default master branch Pattern.");
                masterBranchPattern = "origin/master";
            }
            logger.debug("Master Branch Pattern: " + masterBranchPattern);

            if (gitBranchExpression == null) {
                logger.debug("Using default gitBranchExpression.");
                gitBranchExpression = "${env.GIT_BRANCH}";
            }
            logger.debug("Git Branch Expression: " + gitBranchExpression);


            PropertyResolver pr = new PropertyResolver();
            String gitBranch = pr.resolveValue(gitBranchExpression, session.getCurrentProject().getProperties(), systemEnvVars);
            logger.info("Build Extension Resolved: " + gitBranchExpression + " to: " + gitBranch);

            // Test to see if the current GIT_BRANCH matches the masterBranchPattern...
            if (gitBranch != null && gitBranch.matches(masterBranchPattern)) {
                logger.info("gitflow-helper-maven-plugin: Enabling MasterPromoteExtension. GIT_BRANCH: [" + gitBranch + "] matches masterBranchPattern: [" + masterBranchPattern + "]");

                for (MavenProject project : session.getProjects()) {
                    // Drop all the plugins from the build except for the gitflow-helper-maven-plugin.
                    project.getBuildPlugins().removeAll(pluginsToDrop.get(project));
                }
            }
        }
    }

    private String extractPluginConfigValue(String parameter, Plugin plugin) {
        String value = extractConfigValue(parameter, plugin.getConfiguration());
        for (int i = 0; i < plugin.getExecutions().size() && value == null; i++) {
            value = extractConfigValue(parameter, plugin.getExecutions().get(i).getConfiguration());
        }
        return value;
    }

    private String extractConfigValue(String parameter, Object configuration) {
        try {
            return ((Xpp3Dom) configuration).getChild(parameter).getValue();
        } catch (Exception ex) {
        }
        return null;
    }
}
