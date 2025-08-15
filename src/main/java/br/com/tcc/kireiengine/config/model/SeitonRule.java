package br.com.tcc.kireiengine.config.model;

import java.util.List;

public class SeitonRule
{
    private String name;
    private List<String> extensions;
    private String destination;

    //Getters
    public String getName()
    {
         return name;
    }
    public List<String> getExtensions()
    {
        return extensions;
    }
    public String getDestination()
    {
        return destination;
    }

    //Setters
    public void setName(String name)
    {
        this.name = name;
    }

    public void setExtensions(List<String> extensions)
    {
        this.extensions = extensions;
    }

    public void setDestination(String destination)
    {
        this.destination = destination;
    }

}
