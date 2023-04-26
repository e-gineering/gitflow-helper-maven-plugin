package com.e_gineering.maven.gitflowhelper;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.prefix.NoPluginFoundForPrefixException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.StringUtils;
import org.codehaus.plexus.component.annotations.Component;

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
        // The key of the map is the plugin key, the value is a set of specific executions of that plugin
        // to retain (where an '*' denotes that all executions should be retained).
        final Map<String, Set<String>> pluginWhitelist = new HashMap<>();

        // First load the default whitelist
        DEFAULT_PLUGIN_WHITELIST.forEach(plugin -> pluginWhitelist.computeIfAbsent(plugin, k -> new HashSet<>()).add("*"));

        // Then determine which plugin(s) are activated through commandline supplied goals
        List<String> goals = session.getGoals();
        for (String goal : goals) {
            int delimiter = goal.indexOf(":");
            if (delimiter != -1) {
                String prefix = goal.substring(0, delimiter);
                try {
                    String pluginKey = descriptorCreator.findPluginForPrefix(prefix, session).getKey();
                    pluginWhitelist.computeIfAbsent(pluginKey, k -> new HashSet<>()).add("*");
                    logger.debug("Retain plugin " + pluginKey + ", it was supplied on the command line (goal=" + goal +")");
                } catch (NoPluginFoundForPrefixException ex) {
                    logger.warn("gitflow-helper-maven-plugin: Unable to resolve project plugin for prefix: " + prefix + " for goal: " + goal);
                }
            }
        }

        // Finally parse the configured plugin (executions) to retain
        if (this.retainPlugins != null) {

            Pattern pattern = Pattern.compile("(?<groupId>[^:]+):(?<artifactId>[^@]+)(@(?<executionId>.+))?");
            for (String retainPlugin : retainPlugins) {
                Matcher matcher = pattern.matcher(retainPlugin);
                if(matcher.matches()) {

                    final String pluginKey = Plugin.constructKey(matcher.group("groupId"), matcher.group("artifactId"));

                    Set<String> executionWhiteList = pluginWhitelist.computeIfAbsent(pluginKey, (k) -> new HashSet<>());

                    String executionId = matcher.group("executionId");

                    if(StringUtils.isBlank(executionId)) {
                        executionWhiteList.add("*");
                    } else {
                        executionWhiteList.add(executionId.trim());
                    }

                } else {
                    throw new MavenExecutionException(
                            "Expected syntax for retainPlugin: groupId:artifactId[@execution-id] but found " + retainPlugin,
                            session.getRequest().getPom());
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
                        final Collection<String> executionToRetain = pluginWhitelist.get(plugin.getKey());
                        if(executionToRetain == null) {
                            iterator.remove();
                            continue;
                        }
                        if(executionToRetain.contains("*")) {
                            logger.debug("Retain all executions of plugin " + plugin.getKey());
                            continue;
                        }

                        Iterator<PluginExecution> executionIterator = plugin.getExecutions().iterator();
                        while(executionIterator.hasNext()) {
                            PluginExecution execution = executionIterator.next();

                            if(executionToRetain.remove(execution.getId())) {
                                logger.debug("Retain execution "+ plugin.getKey() + "@" + execution.getId());
                            }  else {
                                executionIterator.remove();
                            }
                        }
                        if(!executionToRetain.isEmpty()) {
                            logger.warn("Found unknown executions " + executionToRetain + " for plugin " + plugin.getKey() + " on the retainPlugins");
                        }
                    }
                }
            }
        }
    }
}
