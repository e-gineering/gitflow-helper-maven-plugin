package com.e_gineering.maven.gitflowhelper;

import com.e_gineering.maven.gitflowhelper.properties.PropertyResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

/**
 * Abstracts Per-Branch builds & Logging
 */
public abstract class AbstractGitflowBranchMojo extends AbstractMojo {

    private Properties systemEnvVars = new Properties();

    private PropertyResolver resolver = new PropertyResolver();


    @Component
    protected MavenProject project;

    @Parameter(defaultValue = "origin/master", property = "masterBranchPattern", required = true)
    private String masterBranchPattern;

    @Parameter(defaultValue = "origin/release/(.*)", property = "releaseBranchPattern", required = true)
    private String releaseBranchPattern;

    @Parameter(defaultValue = "origin/hotfix/(.*)", property = "hotfixBranchPattern", required = true)
    private String hotfixBranchPattern;

    @Parameter(defaultValue = "origin/bugfix/.*", property = "bugfixBranchPattern", required = true)
    private String bugfixBranchPattern;

    @Parameter(defaultValue = "origin/development", property = "developmentBranchPattern", required = true)
    private String developmentBranchPattern;

    // @Parameter tag causes property resolution to fail for patterns containing ${env.}. Default provided in execute();
    @Parameter(property = "gitBranchProperty", required = false)
    private String gitBranchProperty;

    protected abstract void execute(final GitBranchType type, final String gitBranch, final String branchPattern) throws MojoExecutionException, MojoFailureException;

    /**
     * Method exposing Property Resolving for subclasses.
     *
     * @param expression
     * @return
     */
    protected String resolveExpression(final String expression) {
        String propKey = UUID.randomUUID().toString();
        project.getProperties().setProperty(propKey, expression);

        return resolver.getPropertyValue(propKey, project.getProperties(), systemEnvVars);
    }

    private void logExecute(final GitBranchType type, final String gitBranch, final String branchPattern) throws MojoExecutionException, MojoFailureException {
        getLog().debug("Building for GitBranchType: " + type.name() + ". gitBranch: '" + gitBranch + "' branchPattern: '" + branchPattern + "'");
        execute(type, gitBranch, branchPattern);
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (gitBranchProperty == null) {
            gitBranchProperty = "${env.GIT_BRANCH}";
        }

        try {
            systemEnvVars = CommandLineUtils.getSystemEnvVars();
        } catch (IOException ioe) {
            throw new MojoExecutionException("Unable to read System Envirionment Variables: ", ioe);
        }

        // Try to resolve the gitBranchProperty to an actual Value...
        String gitBranch = resolveExpression(gitBranchProperty);
        if (StringUtils.isNotEmpty(gitBranch) && !gitBranch.equals(gitBranchProperty)) {
            getLog().debug("Detected GIT_BRANCH: '" + gitBranch + "' in build environment.");

            /*
             * /origin/master goes to the maven 'release' repo.
             * /origin/release/.* , /origin/hotfix/.* , and /origin/bugfix/.* go to the maven 'test' repo.
             * /origin/development goes to the 'snapshot' repo.
             * All other builds will use the default semantics for 'deploy'.
             */
            if (gitBranch.matches(masterBranchPattern)) {
                logExecute(GitBranchType.MASTER, gitBranch, masterBranchPattern);
            } else if (gitBranch.matches(releaseBranchPattern)) {
                logExecute(GitBranchType.RELEASE, gitBranch, releaseBranchPattern);
            } else if (gitBranch.matches(hotfixBranchPattern)) {
                logExecute(GitBranchType.HOTFIX, gitBranch, hotfixBranchPattern);
            } else if (gitBranch.matches(bugfixBranchPattern)) {
                logExecute(GitBranchType.BUGFIX, gitBranch, bugfixBranchPattern);
            } else if (gitBranch.matches(developmentBranchPattern)) {
                logExecute(GitBranchType.DEVELOPMENT, gitBranch, developmentBranchPattern);
            } else {
                logExecute(GitBranchType.OTHER, gitBranch, null);
            }
        } else {
            getLog().debug("GIT_BRANCH Undefined. Build will continue as configured.");
        }
    }
}
