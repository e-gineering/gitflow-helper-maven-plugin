package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.Enumeration;
import java.util.Properties;

/**
 * Set a project property value based upon the current ${env.GIT_BRANCH} resolution.
 */
@Mojo(name = "set-properties", defaultPhase = LifecyclePhase.INITIALIZE)
public class SetPropertiesMojo extends AbstractGitflowBranchMojo {

    @Parameter(property = "masterBranchProperties", readonly = true, required = false)
    private Properties masterBranchProperties = new Properties();

    @Parameter(property = "releaseBranchProperties", readonly = true, required = false)
    private Properties releaseBranchProperties = new Properties();

    @Parameter(property = "hotfixBranchProperties", readonly = true, required = false)
    private Properties hotfixBranchProperties = new Properties();

    @Parameter(property = "developmentBranchValue", readonly = true, required = false)
    private Properties developmentBranchProperties = new Properties();

    @Parameter(property = "otherBranchValue", readonly = true, required = false)
    private Properties otherBranchProperties = new Properties();

    @Parameter(property = "undefinedBranchValue", readonly = true, required = false)
    private Properties undefinedBranchValue = new Properties();

    @Parameter(property = "scope", readonly = false, required = true, defaultValue = "project")
    private String scope;

    @Parameter(property ="resolve", defaultValue = "true")
    private boolean resolve;


    @Override
    protected void execute(final GitBranchType type, final String gitBranch, final String branchPattern) throws MojoExecutionException, MojoFailureException {
        Properties toInject = null;
        switch (type) {
            case MASTER: {
                toInject = masterBranchProperties;
                break;
            }
            case RELEASE: {
                toInject = releaseBranchProperties;
                break;
            }
            case HOTFIX: {
                toInject = hotfixBranchProperties;
                break;
            }
            case DEVELOPMENT: {
                toInject = developmentBranchProperties;
                break;
            }
            case OTHER: {
                toInject = otherBranchProperties;
                break;
            }
            case UNDEFINED: {
                toInject = undefinedBranchValue;
                break;
            }
        }

        getLog().debug("Setting " + toInject.size() + " properties...");

        for (Enumeration<?> propertyNames = toInject.propertyNames(); propertyNames.hasMoreElements(); ) {
            String propertyName = propertyNames.nextElement().toString();

            String key = resolveExpression(propertyName);
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
