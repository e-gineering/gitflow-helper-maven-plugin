package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Set a project property value based upon the current ${env.GIT_BRANCH} resolution.
 */
@Mojo(name = "set-properties", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public class SetPropertiesMojo extends AbstractGitflowBranchMojo {

    /**
     * Properties to be applied if executing against a master branch
     */
    @Parameter(property = "masterBranchProperties")
    private Properties masterBranchProperties;

    /**
     * A Property file to load if executing against the master branch
     */
    @Parameter(property = "masterBranchPropertyFile")
    private File masterBranchPropertyFile;

    /**
     * Properties to be applied if executing against a support branch
     */
    @Parameter(property = "supportBranchProperties")
    private Properties supportBranchProperties;

    /**
     * A Property file to load if executing against the support branch
     */
    @Parameter(property = "supportBranchPropertyFile")
    private File supportBranchPropertyFile;

    /**
     * Properties to be applied if executing against a release branch
     */
    @Parameter(property = "releaseBranchProperties")
    private Properties releaseBranchProperties;

    /**
     * A Property file to load if executing against a release branch
     */
    @Parameter(property = "releaseBranchPropertyFile")
    private File releaseBranchPropertyFile;

    /**
     * Properties to be applied if executing against a hotfix branch
     */
    @Parameter(property = "hotfixBranchProperties")
    private Properties hotfixBranchProperties;

    /**
     * A Property file to load if executing against a hotfix branch
     */
    @Parameter(property = "hotfixBranchPropertyFile")
    private File hotfixBranchPropertyFile;

    /**
     * Properties to be applied if executing against the development branch
     */
    @Parameter(property = "developmentBranchProperties")
    private Properties developmentBranchProperties;

    /**
     * A Property file to load if executing against the development branch
     */
    @Parameter(property = "developmentBranchPropertyFile")
    private File developmentBranchPropertyFile;

    /**
     * Properties to be applied if executing against a non-releasable (feature) branch
     */
    @Parameter(property = "otherBranchProperties")
    private Properties otherBranchProperties;

    /**
     * A Property file to load if executing against a non-releasable (feature) branch
     */
    @Parameter(property = "otherBranchPropertyFile")
    private File otherBranchPropertyFile;

    /**
     * Properties to be applied if executing against an undefined (local) branch
     */
    @Parameter(property = "undefinedBranchProperties")
    private Properties undefinedBranchProperties;

    /**
     * A Property file to load if executing against an undefined (local) branch
     */
    @Parameter(property = "undefinedBranchPropertyFile")
    private File undefinedBranchPropertyFile;

    /**
     * Scope in which to set the properties. default is "project", set to "system" in order to set system-level properties.
     */
    @Parameter(property = "scope", defaultValue = "project")
    private String scope;

    /**
     * Weather or not to attempt to resolve keys / values as if they're properties.
     */
    @Parameter(property ="resolve", defaultValue = "true")
    private boolean resolve;

    /**
     * Added to the beginning of any property key which is set or loaded by this plugin.
     */
    @Parameter(property = "keyPrefix", defaultValue = "")
    private String keyPrefix = "";


    @Override
    protected void execute(final GitBranchType type, final String gitBranch, final String branchPattern) throws MojoExecutionException, MojoFailureException {
        Properties toInject = null;
        File toLoad = null;
        switch (type) {
            case SUPPORT: {
                toInject = supportBranchProperties;
                toLoad = supportBranchPropertyFile;
                break;
            }
            case MASTER: {
                toInject = masterBranchProperties;
                toLoad = masterBranchPropertyFile;
                break;
            }
            case RELEASE: {
                toInject = releaseBranchProperties;
                toLoad = releaseBranchPropertyFile;
                break;
            }
            case HOTFIX: {
                toInject = hotfixBranchProperties;
                toLoad = hotfixBranchPropertyFile;
                break;
            }
            case DEVELOPMENT: {
                toInject = developmentBranchProperties;
                toLoad = developmentBranchPropertyFile;
                break;
            }
            case OTHER: {
                toInject = otherBranchProperties;
                toLoad = otherBranchPropertyFile;
                break;
            }
            case UNDEFINED: {
                toInject = undefinedBranchProperties;
                toLoad = undefinedBranchPropertyFile;
                break;
            }
        }

        setProperties(toInject);

        if (toLoad != null) {
            toInject = new Properties();
            FileInputStream fis = null;
            try {
                getLog().info("Loading properties from: " + toLoad.getCanonicalPath());
                fis = new FileInputStream(toLoad);
                toInject.load(fis);
            } catch (IOException ioe) {
                getLog().error("Could not load from : " + toLoad.getAbsolutePath(), ioe);
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException ioe) {
                    }
                    fis = null;
                }
            }
            setProperties(toInject);
        }
    }

    public void setProperties(Properties toInject) {
        if (toInject == null) {
            return;
        }
        getLog().info("Setting " + toInject.size() + " properties...");

        for (Enumeration<?> propertyNames = toInject.propertyNames(); propertyNames.hasMoreElements(); ) {
            String propertyName = propertyNames.nextElement().toString();

            String key = keyPrefix + resolveExpression(propertyName);
            String value = resolveExpression(toInject.getProperty(propertyName));

            getLog().debug("  " + key + " = " + value);

            Object replaced;
            if ("system".equalsIgnoreCase(scope)) {
                replaced = System.setProperty(key, value);
            } else {
                replaced = project.getProperties().setProperty(key, value);
            }

            if (replaced != null) {
                getLog().debug("   replaced previous value : " + replaced);
            }
        }
    }
}
