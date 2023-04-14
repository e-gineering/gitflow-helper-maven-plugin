package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.utils.StringUtils;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.function.Function;

public class PropertyMapper
{
    private String id;
    private String propertyName;
    private String language = "java";
    private String mapper;

    public String getId()
    {
        return id;
    }

    public void setId(final String id)
    {
        this.id = id;
    }

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

    String map(String propertyValue) throws MojoExecutionException
    {
        if(StringUtils.isBlank(getId())) {
            throw new MojoExecutionException("Property mapper has no id");
        }
        if(StringUtils.isBlank(getLanguage())) {
            throw new MojoExecutionException("Property mapper with id [" + getId() + "] has no language");
        }
        if(StringUtils.isBlank(getMapper())) {
            throw new MojoExecutionException("Property mapper with id [" + getId() + "] has no mapper");
        }
        if("java".equalsIgnoreCase(language)) {

            Class<?> mapperClass;
            try
            {
                mapperClass = Class.forName(mapper);
            }
            catch(ClassNotFoundException e)
            {
                throw new MojoExecutionException("Error in property mapper with id [" + getId() + "]: class " + mapper + " not found", e);
            }

            if(!Function.class.isAssignableFrom(mapperClass)) {
                throw new MojoExecutionException("Error in property mapper with id [" + getId() + "]: class " + mapper + " does not implement " + Function.class.getName());
            }

            try
            {
                @SuppressWarnings("unchecked")
                Function<String,String> function = (Function<String, String>)Class.forName(mapper).newInstance();
                return function.apply(propertyValue);
            }
            catch(Exception e)
            {
                throw new MojoExecutionException("Error in property mapper with id [" + getId() + "]", e);
            }
        }
        ScriptEngineManager mgr = new ScriptEngineManager();
        ScriptEngine engine = mgr.getEngineByName(language);
        if(engine == null) {
            throw new MojoExecutionException("Property mapper with id [" + getId() + "] has an unsupported language [" + language + "]");
        }
        try
        {
            Bindings bindings = engine.createBindings();
            bindings.put("propertyValue", propertyValue);
            Object ret = engine.eval(mapper, bindings);
            if(ret == null) {
                return null;
            }
            return String.valueOf(ret);
        }
        catch(ScriptException e)
        {
            throw new MojoExecutionException("Error in property mapper with id " + getId(), e);
        }
    }

}
