package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Set a project property value based upon the current ${env.GIT_BRANCH} resolution.
 */
@Mojo(name = "set-property", defaultPhase = LifecyclePhase.VALIDATE)
public class SetPropertyMojo extends AbstractGitflowBranchMojo {

    @Parameter(property = "key", readonly = true, required = true)
    private String key;

    @Parameter(property = "masterBranchValue", readonly = true, required = false)
    private String masterBranchValue = null;

    @Parameter(property = "releaseBranchValue", readonly = true, required = false)
    private String releaseBranchValue = null;

    @Parameter(property = "hotfixBranchValue", readonly = true, required = false)
    private String hotfixBranchValue = null;

    @Parameter(property = "developmentBranchValue", readonly = true, required = false)
    private String developmentBranchValue = null;

    @Parameter(property = "otherBranchValue", readonly = true, required = false)
    private String otherBranchValue = null;

    @Parameter(property = "undefinedBranchValue", readonly = true, required = false)
    private String undefinedBranchValue = null;

    @Override
    protected void execute(final GitBranchType type, final String gitBranch, final String branchPattern) throws MojoExecutionException, MojoFailureException {
        String resolvedValue = null;
        switch (type) {
            case MASTER: {
                resolvedValue = resolveExpression(masterBranchValue);
                break;
            }
            case RELEASE: {
                resolvedValue = resolveExpression(releaseBranchValue);
                break;
            }
            case HOTFIX: {
                resolvedValue = resolveExpression(hotfixBranchValue);
                break;
            }
            case DEVELOPMENT: {
                resolvedValue = resolveExpression(developmentBranchValue);
                break;
            }
            case OTHER: {
                resolvedValue = resolveExpression(otherBranchValue);
                break;
            }
            case UNDEFINED: {
                resolvedValue = resolveExpression(undefinedBranchValue);
                break;
            }
        }
        getLog().info("Setting " + key + " =  '" + resolvedValue + "'");
        project.getProperties().put(key, resolvedValue);
    }
}
