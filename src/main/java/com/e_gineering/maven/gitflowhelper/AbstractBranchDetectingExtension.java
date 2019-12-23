package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.internal.MojoDescriptorCreator;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.prefix.NoPluginFoundForPrefixException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.manager.ScmManager;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

public abstract class AbstractBranchDetectingExtension extends AbstractMavenLifecycleParticipant {
    @Requirement
    MojoDescriptorCreator descriptorCreator;
    
    @Requirement
    Logger logger;
    
    @Requirement
    ScmManager scmManager;
    
    boolean pluginFound = false;
    String masterBranchPattern;
    String supportBranchPattern;
    String releaseBranchPattern;
    String hotfixBranchPattern;
    String developmentBranchPattern;
    String featureOrBugfixBranchPattern;
    String otherDeployBranchPattern;
    String otherBranchVersionDelimiter;
    GitBranchInfo branchInfo;
    Properties systemEnvVars;
    
    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        try {
            systemEnvVars = CommandLineUtils.getSystemEnvVars();
        } catch (IOException ioe) {
            throw new MavenExecutionException("Unable to read System Envirionment Variables: ", ioe);
        }
        
        // Look for a configured gitflow-helper-maven-plugin,
        // To determine what the gitBranchExpression and branch patterns are...
        String gitBranchExpression = null;
    
        pluginFound = false;
        for (MavenProject project : session.getProjects()) {
            for (Plugin plugin : project.getModel().getBuild().getPlugins()) {
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
                    
                    if (otherDeployBranchPattern == null) {
                        otherDeployBranchPattern = extractPluginConfigValue("otherDeployBranchPattern", plugin);
                    }
                    
                    if (otherBranchVersionDelimiter == null) {
                        otherBranchVersionDelimiter = extractPluginConfigValue("otherBranchVersionDelimiter", plugin);
                    }
                
                    if (gitBranchExpression == null) {
                        gitBranchExpression = extractPluginConfigValue("gitBranchExpression", plugin);
                    }
                }
            }
        }
    
        // Any missing configuration options need to be defaulted.
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
            
            if (otherDeployBranchPattern == null) {
                logger.debug("Using default other deployment branch Pattern.");
                otherDeployBranchPattern = "";
            }
            logger.debug("Other Branch Deployment Pattern: " + otherDeployBranchPattern);
    
            if (otherBranchVersionDelimiter == null) {
                logger.debug("Using default otherBranchVersionDelimiter.");
                otherBranchVersionDelimiter = "+";
            }
            
            ScmUtils scmUtils = new ScmUtils(systemEnvVars, scmManager, session.getTopLevelProject(), new PlexusLoggerToMavenLog(logger), masterBranchPattern, supportBranchPattern, releaseBranchPattern, hotfixBranchPattern, developmentBranchPattern);
            branchInfo = scmUtils.resolveBranchInfo(gitBranchExpression);
        } else {
            logger.debug("Unable to configure gitflow-helper-maven-plugin lifecycle extensions. No Plugin configuration found.");
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
