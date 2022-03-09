package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.prefix.NoPluginFoundForPrefixException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maven extension which removes (skips) undesired plugins from the build reactor when running on a master branch.
 * <p/>
 * Essentially, enables using the master branch as a 'promotion' branch.
 */
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "promote-master")
public class MasterPromoteExtension extends AbstractBranchDetectingExtension {

    private static final Set<String> PLUGIN_WHITELIST = Collections.unmodifiableSet(
            new HashSet<>(
                    Arrays.asList(
                            "org.apache.maven.plugins:maven-deploy-plugin",
                            "com.e-gineering:gitflow-helper-maven-plugin"
                    )
            )
    );
    
    @Override
    public void afterProjectsRead(final MavenSession session) throws MavenExecutionException {
        super.afterProjectsRead(session);

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
        Map<MavenProject, List<Plugin>> pluginsToDrop = new HashMap<>();

        final List<String> configuredPluginsToRetain;
        if (this.retainPlugins != null) {
            configuredPluginsToRetain = this.retainPlugins;
        } else {
            configuredPluginsToRetain = Collections.emptyList();
        }

        for (MavenProject project : session.getProjects()) {

            // Create a list of all plugins that are not in the whitelist, not explicitly invoked from the commandline,
            // and not configured to be allowed on master/support.
            List<Plugin> dropPlugins = project.getModel().getBuild().getPlugins()
                    .stream()
                    .filter(plugin -> !PLUGIN_WHITELIST.contains(plugin.getKey()))
                    .filter(plugin -> !pluginsToRetain.contains(plugin))
                    .filter(plugin -> !configuredPluginsToRetain.contains(plugin.getKey()))
                    .collect(Collectors.toList());

            pluginsToDrop.put(project, dropPlugins);
        }

        if (pluginFound) {
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
                    project.getModel().getBuild().getPlugins().removeAll(pluginsToDrop.get(project));
                }
            }
        }
    }
}
