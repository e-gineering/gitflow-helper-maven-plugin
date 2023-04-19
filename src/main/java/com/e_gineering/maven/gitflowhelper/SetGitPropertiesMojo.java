package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;

/**
 * Stores the branch type and the git branch in Maven properties
 */
@Mojo(name = "set-git-properties", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class SetGitPropertiesMojo extends AbstractGitflowBranchMojo
{
    /**
     * Defines the name of the property where the branch type is stored
     */
    @Parameter(property = "branchTypeProperty", defaultValue = "branchType")
    private String branchTypeProperty = "branchType";

    /**
     * Defines the name of the property where the Git branch name is stored.
     */
    @Parameter(property = "branchNameProperty", defaultValue = "gitBranchName")
    private String branchNameProperty = "branchName";

    /**
     * Der branchNamePropertyMapper allows to store the Git branch name
     * into additional properties.<br>
     * The branchName can be mapped by a java class, or an JSR223 scripting language
     */
    @Parameter(property = "branchNamePropertyMappers")
    private PropertyMapper[] branchNamePropertyMappers;

    @Override
    protected void execute(final GitBranchInfo gitBranchInfo) throws MojoExecutionException, MojoFailureException
    {
        if(!StringUtils.isBlank(branchTypeProperty)) {
            project.getProperties().setProperty(branchTypeProperty, gitBranchInfo.getType().name());
        }
        if(!StringUtils.isBlank(branchNameProperty)) {
            project.getProperties().setProperty(branchNameProperty, gitBranchInfo.getName());
        }

        if(branchNamePropertyMappers != null) {
            for(PropertyMapper pm : branchNamePropertyMappers)
            {
                String mappedValue = pm.map(gitBranchInfo);
                getLog().info("Mapped Git branch name [" + gitBranchInfo.getName() + "] for property [" + pm.getPropertyName() +"] to [" + mappedValue + "]");
                project.getProperties().setProperty(pm.getPropertyName(), mappedValue);
            }
        }
    }
}
