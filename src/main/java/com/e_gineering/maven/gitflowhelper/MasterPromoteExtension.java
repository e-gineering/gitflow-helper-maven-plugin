package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.prefix.NoPluginFoundForPrefixException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maven extension which removes (skips) undesired plugins from the build reactor when running on a master branch.
 * <p/>
 * Essentially, enables using the master branch as a 'promotion' branch.
 */
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "promote-master")
public class MasterPromoteExtension extends AbstractBranchDetectingExtension {

    private static final Set<String> DEFAULT_PLUGIN_WHITELIST = Collections.unmodifiableSet(
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

        // Build a whitelist of plugin (executions) that should remain while running on master.
        // The key of the map is the plugin key, the value is a collection of specific executions of that plugin
        // to retain (where an empty collection denotes that all executions should be retained).
        final Map<String, Collection<String>> pluginWhitelist = new HashMap<>();

        // First load the default whitelist
        DEFAULT_PLUGIN_WHITELIST.forEach(plugin -> pluginWhitelist.put(plugin, Collections.emptyList()));

        // Then determine which plugin(s) are activated through commandline supplied goals
        List<String> goals = session.getGoals();
        for (String goal : goals) {
            int delimiter = goal.indexOf(":");
            if (delimiter != -1) {
                String prefix = goal.substring(0, delimiter);
                try {
                    pluginWhitelist.put(descriptorCreator.findPluginForPrefix(prefix, session).getKey(), Collections.emptyList());
                } catch (NoPluginFoundForPrefixException ex) {
                    logger.warn("gitflow-helper-maven-plugin: Unable to resolve project plugin for prefix: " + prefix + " for goal: " + goal);
                }
            }
        }

        // Finally parse the configured plugin (executions) to retain
        if (this.retainPlugins != null) {
            for (String retainPlugin : retainPlugins) {
                String[] elements = retainPlugin.split(":");
                if (elements.length != 2 && elements.length != 3) {
                    throw new MavenExecutionException(
                            "Expected syntax for retainPlugin: groupId:artifactId[:execution-id] but found " + retainPlugin,
                            session.getRequest().getPom()
                    );
                }
                final String pluginKey = Plugin.constructKey(elements[0], elements[1]);
                if (elements.length == 2) {
                    pluginWhitelist.put(pluginKey, Collections.emptyList());
                } else {
                    final Collection<String> executionsToRetain;
                    if (pluginWhitelist.containsKey(pluginKey)) {
                        executionsToRetain = pluginWhitelist.get(pluginKey);
                    } else {
                        executionsToRetain = new HashSet<>();
                        pluginWhitelist.put(pluginKey, executionsToRetain);
                    }
                    executionsToRetain.add(elements[2]);
                }
            }
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
                logger.info("The following plugin (execution) whitelist will be applied: " + pluginWhitelist);
                for (MavenProject project : session.getProjects()) {
                    // Using the pluginWhiteList, determine which plugin (executions) are allowed to stay.
                    final Iterator<Plugin> iterator = project.getModel().getBuild().getPlugins().iterator();
                    while (iterator.hasNext()) {
                        Plugin plugin = iterator.next();
                        if (pluginWhitelist.containsKey(plugin.getKey())) {
                            // If the plugin key is present in the whitelist, either all executions must be retained
                            // (in case of an empty collection), or only those mentioned in the collection.
                            final Collection<String> executionToRetain = pluginWhitelist.get(plugin.getKey());
                            if (!executionToRetain.isEmpty()) {
                                plugin.getExecutions()
                                        .removeIf(pluginExecution -> !executionToRetain.contains(pluginExecution.getId()));
                            }
                        } else {
                            // If the plugin's key is not present in the whitelist, it can be dropped
                            iterator.remove();
                        }
                    }
                }
            }
        }
    }
}
