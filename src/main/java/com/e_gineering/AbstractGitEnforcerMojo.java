package com.e_gineering;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Abstracts out the basic properties common to all our Mojos.
 */
public abstract class AbstractGitEnforcerMojo extends AbstractMojo {
    @Component
    protected MavenProject project;

    @Parameter(defaultValue = "origin/master", property = "masterBranchPattern", required = true)
    protected String masterBranchPattern;

    @Parameter(defaultValue = "origin/release/(.*)", property = "releaseBranchPattern", required = true)
    protected String releaseBranchPattern;

    @Parameter(defaultValue = "origin/hotfix/(.*)", property = "hotfixBranchPattern", required = true)
    protected String hotfixBranchPattern;

    @Parameter(defaultValue = "origin/bugfix/.*", property = "bugfixBranchPattern", required = true)
    protected String bugfixBranchPattern;

    @Parameter(defaultValue = "origin/development", property = "developmentBranchPattern", required = true)
    protected String developmentBranchPattern;
}
