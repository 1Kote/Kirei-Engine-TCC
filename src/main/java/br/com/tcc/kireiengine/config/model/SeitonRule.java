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

}
