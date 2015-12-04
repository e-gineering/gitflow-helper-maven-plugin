package com.e_gineering;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Maven extension which removes (skips) undesired plugins from the build reactor when running on a master branch.
 *
 * Essentially, enables using the master branch as a 'promotion' branch.
 */
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "promote-master")
public class MasterPromoteExtension extends AbstractMavenLifecycleParticipant {

    @Requirement
    private Logger logger;

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        String gitBranch = System.getenv("GIT_BRANCH");

        if (gitBranch != null) {
            // Look for a gitflow-helper-maven-plugin with a masterBranchPattern configured.
            String masterBranchPattern = null;
            boolean pluginFound = false;

            // Build up a map of plugins to remove from projects, if we're on the master branch.
            HashMap<MavenProject, List<Plugin>> pluginsToDrop = new HashMap<MavenProject, List<Plugin>>();

            for (MavenProject project : session.getProjects()) {
                List<Plugin> dropPlugins = new ArrayList<Plugin>();

                for (Plugin plugin : project.getBuildPlugins()) {
                    if (plugin.getKey().equals("com.e-gineering:gitflow-helper-maven-plugin")) {
                        pluginFound = true;

                        logger.info("gitflow-helper-maven-plugin found in project: [" + project.getName() + "]");

                        if (masterBranchPattern == null) {
                            masterBranchPattern = extractMasterBranchPattern(plugin.getConfiguration());
                            for (int i = 0; i < plugin.getExecutions().size() && masterBranchPattern == null; i++) {
                                masterBranchPattern = extractMasterBranchPattern(plugin.getExecutions().get(i).getConfiguration());
                            }
                        }
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
            }

            // Test to see if the current GIT_BRANCH matches the masterBranchPattern...
            if (gitBranch.matches(masterBranchPattern)) {
                logger.info("gitflow-helper-maven-plugin: GIT_BRANCH: [" + gitBranch + "] matches masterBranchPattern: [" + masterBranchPattern + "]");

                for (MavenProject project : session.getProjects()) {
                    // Drop all the plugins from the build except for the gitflow-helper-maven-plugin.
                    project.getBuildPlugins().removeAll(pluginsToDrop.get(project));
                }

                logger.info("Overriding build lifecycle plugins for promotion build.");
            }
        } else {
            logger.debug("No GIT_BRANCH found in environment. Leaving build lifecycle unaltered.");
        }
    }

    private String extractMasterBranchPattern(Object configuration) {
        try {
            return ((Xpp3Dom)configuration).getChild("masterBranchPattern").getValue();
        } catch (Exception ex) { }
        return null;
    }
}
