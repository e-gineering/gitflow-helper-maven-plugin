package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.utils.StringUtils;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.function.BiFunction;

/**
 * Helper for mapping the GIT branch name
 */
public class PropertyMapper
{
    private String propertyName;
    private String language = "java";
    private String mapper;

    public String getLanguage()
    {
        return language;
    }

    public void setLanguage(final String language)
    {
        this.language = language;
    }

    public String getPropertyName()
    {
        return propertyName;
    }

    public void setPropertyName(final String propertyName)
    {
        this.propertyName = propertyName;
    }

    public String getMapper()
    {
        return mapper;
    }

    public void setMapper(final String mapper)
    {
        this.mapper = mapper;
    }

    /**
     * Maps the Git branch name
     * @param gitBranchInfo  the Git Info
     * @return the mapped branch name
     * @throws MojoExecutionException on error
     */
    String map(GitBranchInfo gitBranchInfo) throws MojoExecutionException
    {
        if(StringUtils.isBlank(getPropertyName())) {
            throw new MojoExecutionException("Mapper has not propertyName");
        }
        if(StringUtils.isBlank(getLanguage())) {
            throw new MojoExecutionException("Mapper for property [" + getPropertyName() + "] has no language");
        }
        if(StringUtils.isBlank(getMapper())) {
            throw new MojoExecutionException("Mapper for property [" + getPropertyName() + "] has no mapper");
        }
        if("java".equalsIgnoreCase(language)) {

            Class<?> mapperClass;
            try
            {
                mapperClass = Class.forName(mapper.trim());
            }
            catch(ClassNotFoundException e)
            {
                throw new MojoExecutionException("Error in mapper for property [" + getPropertyName() + "]: class " + mapper + " not found", e);
            }

            if(!BiFunction.class.isAssignableFrom(mapperClass)) {
                throw new MojoExecutionException("Error in mapper for property [" + getPropertyName() + "]: class " + mapper + " does not implement " + BiFunction.class.getName());
            }

            try
            {
                @SuppressWarnings("unchecked")
                BiFunction<String,String,String> function = (BiFunction<String,String,String>)Class.forName(mapper).newInstance();
                return function.apply(gitBranchInfo.getName(), gitBranchInfo.getType().name());
            }
            catch(Exception e)
            {
                throw new MojoExecutionException("Error in mapper for property [" + getPropertyName() + "]", e);
            }
        }
        ScriptEngineManager mgr = new ScriptEngineManager();
        ScriptEngine engine = mgr.getEngineByName(language);
        if(engine == null) {
            throw new MojoExecutionException("Property mapper for property [" + getPropertyName() + "] has an unsupported language [" + language + "]");
        }
        try
        {
            engine.eval(mapper);

            Invocable inv = (Invocable) engine;

            Object ret = inv.invokeFunction("map", gitBranchInfo.getName(), gitBranchInfo.getType());
            if(ret == null) {
                return null;
            }
            return String.valueOf(ret);
        }
        catch(Exception e)
        {
            throw new MojoExecutionException("Error in property mapper for property [" + getPropertyName()+ "]", e);
        }
    }

}
