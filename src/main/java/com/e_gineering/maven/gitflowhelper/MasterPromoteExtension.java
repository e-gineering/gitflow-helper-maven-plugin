package com.e_gineering.maven.gitflowhelper;

import com.e_gineering.maven.gitflowhelper.properties.PropertyResolver;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.IOException;
import java.util.*;

/**
 * Maven extension which removes (skips) undesired plugins from the build reactor when running on a master branch.
 * <p/>
 * Essentially, enables using the master branch as a 'promotion' branch.
 */
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "promote-master")
public class MasterPromoteExtension extends AbstractMavenLifecycleParticipant {

    @Requirement
    private Logger logger;

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        Properties systemEnvVars = null;
        try {
            systemEnvVars = CommandLineUtils.getSystemEnvVars();
        } catch (IOException ioe) {
            throw new MavenExecutionException("Unable to read System Envirionment Variables: ", ioe);
        }

        // Look for a gitflow-helper-maven-plugin, so we can determine what the gitBranchProperty and masterBranchPattern are...
        String masterBranchPattern = null;
        String gitBranchProperty = null;
        boolean pluginFound = false;

        // Build up a map of plugins to remove from projects, if we're on the master branch.
        HashMap<MavenProject, List<Plugin>> pluginsToDrop = new HashMap<MavenProject, List<Plugin>>();

        for (MavenProject project : session.getProjects()) {
            List<Plugin> dropPlugins = new ArrayList<Plugin>();

            for (Plugin plugin : project.getBuildPlugins()) {
                // Don't drop our plugin. Read it's config.
                if (plugin.getKey().equals("com.e-gineering:gitflow-helper-maven-plugin")) {
                    pluginFound = true;

                    logger.info("gitflow-helper-maven-plugin found in project: [" + project.getName() + "]");

                    if (masterBranchPattern == null) {
                        masterBranchPattern = extractMasterBranchPattern(plugin.getConfiguration());
                        for (int i = 0; i < plugin.getExecutions().size() && masterBranchPattern == null; i++) {
                            masterBranchPattern = extractMasterBranchPattern(plugin.getExecutions().get(i).getConfiguration());
                        }
                    }

                    if (gitBranchProperty == null) {
                        gitBranchProperty = extractGitBranchProperty(plugin.getConfiguration());
                        for (int i = 0; i < plugin.getExecutions().size() && gitBranchProperty == null; i++) {
                            gitBranchProperty = extractGitBranchProperty(plugin.getExecutions().get(i).getConfiguration());
                        }
                    }

                    // Don't drop the maven-deploy-plugin. Read it's config.
                } else if (plugin.getKey().equals("org.apache.maven.plugins:maven-deploy-plugin")) {
                    logger.debug("gitflow-helper-maven-plugin removing plugin: " + plugin + " from project: " + project.getName());
                } else {
                    dropPlugins.add(plugin);
                }
            }

            pluginsToDrop.put(project, dropPlugins);
        }

        if (pluginFound) {
            if (masterBranchPattern == null) {
                logger.info("Using default master branch Pattern.");
                masterBranchPattern = "origin/master";
            }
            logger.info("Master Branch Pattern: " + masterBranchPattern);

            if (gitBranchProperty == null) {
                logger.info("Using default gitBranchProperty.");
                gitBranchProperty = "${env.GIT_BRANCH}";
            }
            logger.info("Git Branch Property: " + gitBranchProperty);
        }

        PropertyResolver pr = new PropertyResolver();
        // Generate a random unique key for the property expression.
        String gitBranchPropKey = UUID.randomUUID().toString();
        session.getCurrentProject().getProperties().setProperty(gitBranchPropKey, gitBranchProperty);
        String gitBranch = pr.getPropertyValue(gitBranchPropKey, session.getCurrentProject().getProperties(), systemEnvVars);
        if (!gitBranch.equals(gitBranchProperty)) {
            logger.info("Resolved: " + gitBranchProperty + " to: " + gitBranch);

            // Test to see if the current GIT_BRANCH matches the masterBranchPattern...
            if (gitBranch.matches(masterBranchPattern)) {
                logger.info("gitflow-helper-maven-plugin: GIT_BRANCH: [" + gitBranch + "] matches masterBranchPattern: [" + masterBranchPattern + "]");

                for (MavenProject project : session.getProjects()) {
                    // Drop all the plugins from the build except for the gitflow-helper-maven-plugin.
                    project.getBuildPlugins().removeAll(pluginsToDrop.get(project));
                }

                logger.info("Overriding build lifecycle plugins for promotion build.");
            }
        }
    }

    private String extractMasterBranchPattern(Object configuration) {
        try {
            return ((Xpp3Dom) configuration).getChild("masterBranchPattern").getValue();
        } catch (Exception ex) {
        }
        return null;
    }

    private String extractGitBranchProperty(Object configuration) {
        try {
            return ((Xpp3Dom) configuration).getChild("gitBranchProperty").getValue();
        } catch (Exception ex) {
        }
        return null;
    }
}
