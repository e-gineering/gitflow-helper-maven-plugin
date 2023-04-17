package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Assert;
import org.junit.Test;

import java.util.function.BiFunction;

public class TestPropertyMapper
{
    /**
     * Test the methode {@link PropertyMapper#map(GitBranchInfo)} with a Java Mapper
     * @throws MojoExecutionException on error
     */
    @Test
    public void testPropertyMapperWithJava() throws MojoExecutionException
    {
        PropertyMapper mapper = new PropertyMapper();
        mapper.setPropertyName("prop");
        mapper.setLanguage("java");
        mapper.setMapper(ToLowerCaseMapper.class.getName());

        GitBranchInfo info = new GitBranchInfo("FOO", GitBranchType.DEVELOPMENT, "bar");

        Assert.assertEquals("foo", mapper.map(info));
    }

    /**
     * Test the methode {@link PropertyMapper#map(GitBranchInfo)} with a Javascript Mapper
     * @throws MojoExecutionException on error
     */
    @Test
    public void testPropertyMapperWithJavascript() throws MojoExecutionException
    {
        PropertyMapper mapper = new PropertyMapper();
        mapper.setPropertyName("prop");
        mapper.setLanguage("javascript");
        mapper.setMapper(""
            + "function map(branchName, branchType) {\n"
            +"     return branchName.toLowerCase();\n"
            + "}"
        );

        GitBranchInfo info = new GitBranchInfo("FOO", GitBranchType.DEVELOPMENT, "bar");

        Assert.assertEquals("foo", mapper.map(info));

    }

    public static class ToLowerCaseMapper implements BiFunction<String,String,String>
    {
        @Override
        public String apply(String branchName, String branchType)
        {
            return branchName.toLowerCase();
        }
    }
}