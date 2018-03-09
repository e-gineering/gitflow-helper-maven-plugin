package com.e_gineering.maven.gitflowhelper;

import com.e_gineering.maven.gitflowhelper.properties.PropertyResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.manager.ScmManager;

import java.util.Properties;

/**
 * Abstracts Per-Branch builds & Logging
 */
public abstract class AbstractGitflowBranchMojo extends AbstractMojo {

    private Properties systemEnvVars = new Properties();

    private PropertyResolver resolver = new PropertyResolver();

    @Component
    protected MavenProject project;

    @Component
    protected ScmManager scmManager;

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

    // @Parameter tag causes property resolution to fail for patterns containing ${env.}. Default provided in execute();
    @Parameter(property = "gitBranchExpression", required = false)
    private String gitBranchExpression;

    protected abstract void execute(final GitBranchType type, final String gitBranch, final String branchPattern) throws MojoExecutionException, MojoFailureException;

    /**
     * Method exposing Property Resolving for subclasses.
     *
     * @param expression
     * @return
     */
    protected String resolveExpression(final String expression) {
        return resolver.resolveValue(expression, project.getProperties(), systemEnvVars);
    }

    private void logExecute(final GitBranchType type, final String gitBranch, final String branchPattern) throws MojoExecutionException, MojoFailureException {
        getLog().debug("Building for GitBranchType: " + type.name() + ". gitBranch: '" + gitBranch + "' branchPattern: '" + branchPattern + "'");
        execute(type, gitBranch, branchPattern);
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        GitBranchInfo branchInfo = ScmUtils.getGitBranchInfo(scmManager, project, getLog(), gitBranchExpression, masterBranchPattern, supportBranchPattern, releaseBranchPattern, hotfixBranchPattern, developmentBranchPattern);
        if (branchInfo != null) {
            getLog().info(branchInfo.toString());

            if (branchInfo.getBranchType().equals(GitBranchType.MASTER)) {
                logExecute(GitBranchType.MASTER, branchInfo.getBranchName(), masterBranchPattern);
            } else if (branchInfo.getBranchType().equals(GitBranchType.SUPPORT)) {
                logExecute(GitBranchType.SUPPORT, branchInfo.getBranchName(), supportBranchPattern);
            } else if (branchInfo.getBranchType().equals(GitBranchType.RELEASE)) {
                logExecute(GitBranchType.RELEASE, branchInfo.getBranchName(), releaseBranchPattern);
            } else if (branchInfo.getBranchType().equals(GitBranchType.HOTFIX)) {
                logExecute(GitBranchType.HOTFIX, branchInfo.getBranchName(), hotfixBranchPattern);
            } else if (branchInfo.getBranchType().equals(GitBranchType.DEVELOPMENT)) {
                logExecute(GitBranchType.DEVELOPMENT, branchInfo.getBranchName(), developmentBranchPattern);
            } else {
                logExecute(GitBranchType.OTHER, branchInfo.getBranchName(), null);
            }
        } else {
            logExecute(GitBranchType.UNDEFINED, "UNKNOWN_BRANCH", null);
        }
    }
}
