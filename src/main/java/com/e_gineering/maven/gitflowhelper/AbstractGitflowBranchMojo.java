package com.e_gineering.maven.gitflowhelper;

import com.e_gineering.maven.gitflowhelper.properties.PropertyResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.manager.ScmManager;
import org.codehaus.plexus.util.cli.CommandLineUtils;

import java.io.IOException;
import java.util.Properties;

/**
 * Abstracts Per-Branch builds & Logging
 */
public abstract class AbstractGitflowBranchMojo extends AbstractMojo {

    private Properties systemEnvVars = new Properties();

    @Component
    protected ScmManager scmManager;

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "(origin/)?master", property = "masterBranchPattern", required = true)
    private String masterBranchPattern;

    @Parameter(defaultValue = "(origin/)?support/(.*)", property = "supportBranchPattern", required = true)
    private String supportBranchPattern;

    @Parameter(defaultValue = "(origin/)?release/(.*)", property = "releaseBranchPattern", required = true)
    private String releaseBranchPattern;

    @Parameter(defaultValue = "(origin/)?hotfix/(.*)", property = "hotfixBranchPattern", required = true)
    private String hotfixBranchPattern;

    @Parameter(defaultValue = "(origin/)?develop", property = "developmentBranchPattern", required = true)
    private String developmentBranchPattern;

    // An expression that resolves to the git branch at run-time.
    // @Parameter tag causes property resolution to fail for patterns containing ${env.}.
    // The default value _must_ be provided programmaticially at run-time.
    @Parameter(property = "gitBranchExpression", required = false)
    private String gitBranchExpression;

    /**
     * If this is "equals" then exact version matching to branch name matching is preformed.
     * Otherwise, this is treated as a "startsWith".
     */
    @Parameter(defaultValue = "equals", property = "releaseBranchMatchType", required = true)
    String releaseBranchMatchType;

    /**
     * Convenience Method exposing Property Resolving for subclasses.
     *
     * @param expression
     * @return
     */
    protected String resolveExpression(final String expression) {
        return PropertyResolver.resolveValue(expression, project.getProperties(), systemEnvVars);
    }

    /**
     * Method to be implemented by branch-aware mojos
     *
     * @param currentBranch
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    protected abstract void execute(final GitBranchInfo currentBranch) throws MojoExecutionException, MojoFailureException;

    public void execute() throws MojoExecutionException, MojoFailureException {
        // Weather we resolve a single branch name or not, it won't hurt to run it through property replacement.
        try {
            systemEnvVars = CommandLineUtils.getSystemEnvVars();
        } catch (IOException ioe) {
            throw new MojoExecutionException("Unable to read System Envirionment Variables: ", ioe);
        }

        // Validate the match type.
        checkReleaseBranchMatchTypeParam();

        ScmUtils scmUtils = new ScmUtils(systemEnvVars, scmManager, project, getLog(), masterBranchPattern, supportBranchPattern, releaseBranchPattern, hotfixBranchPattern, developmentBranchPattern);
        GitBranchInfo branchInfo = scmUtils.resolveBranchInfo(gitBranchExpression);

        getLog().debug("Building for: " + branchInfo);
        execute(branchInfo);
    }

    private void checkReleaseBranchMatchTypeParam() throws MojoFailureException {
        if (!"equals".equals(releaseBranchMatchType) && !"startsWith".equals(releaseBranchMatchType)) {
            throw new MojoFailureException("'releaseBranchMatchType' should be either 'equals' or 'startsWith'. Found '" + releaseBranchMatchType + "'.");
        }
    }
}

