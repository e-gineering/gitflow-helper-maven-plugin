package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.internal.MojoDescriptorCreator;
import org.apache.maven.model.Plugin;
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
    private ScmManager scmManager;

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        Properties systemEnvVars = null;
        try {
            systemEnvVars = CommandLineUtils.getSystemEnvVars();
        } catch (IOException ioe) {
            throw new MavenExecutionException("Unable to read System Envirionment Variables: ", ioe);
        }

        // Look for a gitflow-helper-maven-plugin, so we can determine what the gitBranchExpression and branch patterns are...
        String masterBranchPattern = null;
        String supportBranchPattern = null;
        // Although we're not interested in these patterns, they're needed for ScmUtils.
        String releaseBranchPattern = null;
        String hotfixBranchPattern = null;
        String developmentBranchPattern = null;
        String featureOrBugfixBranchPattern = null;

        String gitBranchExpression = null;
        boolean pluginFound = false;

        // Any plugin which is part of the project goals needs to be retained.
        List<Plugin> pluginsToRetain = new ArrayList<>(session.getGoals().size());

        List<String> goals = session.getGoals();
        for (String goal : goals) {
            int delimiter = goal.indexOf(":");
            if (delimiter != -1) {
                String prefix = goal.substring(0, delimiter);
                try {
                    pluginsToRetain.add(descriptorCreator.findPluginForPrefix(prefix, session));
                } catch (NoPluginFoundForPrefixException ex) {
                    logger.warn("gitflow-helper-maven-plugin: Unable to resolve project plugin for prefix: " + prefix + " for goal: " + goal);
                }
            }
        }

        // Build up a map of plugins to remove from projects, if we're on the master branch.
        HashMap<MavenProject, List<Plugin>> pluginsToDrop = new HashMap<>();

        for (MavenProject project : session.getProjects()) {
            List<Plugin> dropPlugins = new ArrayList<>();

            for (Plugin plugin : project.getBuildPlugins()) {
                // Don't drop our plugin. Read it's config
                if (plugin.getKey().equals("com.e-gineering:gitflow-helper-maven-plugin")) {
                    pluginFound = true;

                    logger.debug("gitflow-helper-maven-plugin found in project: [" + project.getName() + "]");

                    if (masterBranchPattern == null) {
                        masterBranchPattern = extractPluginConfigValue("masterBranchPattern", plugin);
                    }

                    if (supportBranchPattern == null) {
                        supportBranchPattern = extractPluginConfigValue("supportBranchPattern", plugin);
                    }

                    if (releaseBranchPattern == null) {
                        releaseBranchPattern = extractPluginConfigValue("releaseBranchPattern", plugin);
                    }

                    if (hotfixBranchPattern == null) {
                        hotfixBranchPattern = extractPluginConfigValue("hotfixBranchPattern", plugin);
                    }

                    if (developmentBranchPattern == null) {
                        developmentBranchPattern = extractPluginConfigValue("developmentBranchPattern", plugin);
                    }

                    if (featureOrBugfixBranchPattern == null) {
                        featureOrBugfixBranchPattern = extractPluginConfigValue("featureOrBugfixBranchPattern", plugin);
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

            pluginsToDrop.put(project, dropPlugins);
        }

        if (pluginFound) {
            if (masterBranchPattern == null) {
                logger.debug("Using default master branch Pattern.");
                masterBranchPattern = "(origin/)?master";
            }
            logger.debug("Master Branch Pattern: " + masterBranchPattern);

            if (supportBranchPattern == null) {
                logger.debug("Using default support branch Pattern.");
                supportBranchPattern = "(origin/)?support/(.*)";
            }
            logger.debug("Support Branch Pattern: " + supportBranchPattern);

            if (releaseBranchPattern == null) {
                logger.debug("Using default release branch Pattern.");
                releaseBranchPattern = "(origin/)?release/(.*)";
            }
            logger.debug("Release Branch Pattern: " + releaseBranchPattern);

            if (hotfixBranchPattern == null) {
                logger.debug("Using default hotfix branch Pattern.");
                hotfixBranchPattern = "(origin/)?hotfix/(.*)";
            }
            logger.debug("Hotfix Branch Pattern: " + hotfixBranchPattern);

            if (developmentBranchPattern == null) {
                logger.debug("Using default development Pattern.");
                developmentBranchPattern = "(origin/)?develop";
            }
            logger.debug("Development Branch Pattern: " + developmentBranchPattern);

            if (featureOrBugfixBranchPattern == null) {
                logger.debug("Using default feature or bugfix Pattern.");
                featureOrBugfixBranchPattern = "(origin/)?(?:feature|bugfix)/(.*)";
            }
            logger.debug("Feature or Bugfix Branch Pattern: " + featureOrBugfixBranchPattern);

            ScmUtils scmUtils = new ScmUtils(systemEnvVars, scmManager, session.getTopLevelProject(), new PlexusLoggerToMavenLog(logger), masterBranchPattern, supportBranchPattern, releaseBranchPattern, hotfixBranchPattern, developmentBranchPattern);
            GitBranchInfo branchInfo = scmUtils.resolveBranchInfo(gitBranchExpression);

            //GitBranchInfo branchInfo = ScmUtils.getGitBranchInfo(scmManager, session.getTopLevelProject(), new PlexusLoggerToMavenLog(logger), gitBranchExpression, masterBranchPattern, supportBranchPattern, releaseBranchPattern, hotfixBranchPattern, developmentBranchPattern, featureOrBugfixBranchPattern);
            boolean pruneBuild = false;
            if (branchInfo != null) {
                logger.info(branchInfo.toString());
                if (branchInfo.getType().equals(GitBranchType.MASTER)) {
                    logger.info("gitflow-helper-maven-plugin: Enabling MasterPromoteExtension. GIT_BRANCH: [" + branchInfo.getName() + "] matches masterBranchPattern: [" + masterBranchPattern + "]");
                    pruneBuild = true;
                } else if (branchInfo.getType().equals(GitBranchType.SUPPORT)) {
                    logger.info("gitflow-helper-maven-plugin: Enabling MasterPromoteExtension. GIT_BRANCH: [" + branchInfo.getName() + "] matches supportBranchPattern: [" + supportBranchPattern + "]");
                    pruneBuild = true;
                }
            } else {
                logger.warn("Can't determine the Git branch. Not disabling any plugins.");
            }

            if (pruneBuild) {
                for (MavenProject project : session.getProjects()) {
                    // Drop all the plugins from the build except for the gitflow-helper-maven-plugin, or plugins we
                    // invoked goals for which could be mapped back to plugins in our project build.
                    // Goals invoked from the commandline which cannot be mapped back to our project, will get warnings, but should still execute.
                    // If someone is on 'master' and starts executing goals, we need to allow them to do that.
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
        } catch (Exception ignored) {
        }
        return null;
    }
}
