package br.com.tcc.kireiengine.config.model;

import java.util.List;

public class Configuration
{
    private List<String> monitorFolders;
    private List<SeitonRule> seitonRules;
    private SeiriConfig seiriConfig;
    private SeisoConfig seisoConfig;
    private DuplicateDetectionConfig duplicateDetectionConfig;

    //Getters
    public List<String> getMonitorFolders()
    {
        return monitorFolders;
    }
    public List<SeitonRule> getSeitonRules()
    {
        return seitonRules;
    }
    public SeiriConfig getSeiriConfig()
    {
        return seiriConfig;
    }
    public SeisoConfig getSeisoConfig()
    {
        return seisoConfig;
    }
    public DuplicateDetectionConfig getDuplicateDetectionConfig()
    {
        return duplicateDetectionConfig;
    }

    //Setters
    public void setMonitorFolders(List<String> monitorFolders)
    {
        this.monitorFolders = monitorFolders;
    }
    public void setSeitonRules(List<SeitonRule> seitonRules)
    {
        this.seitonRules = seitonRules;
    }
    public void setSeiriConfig(SeiriConfig seiriConfig)
    {
        this.seiriConfig = seiriConfig;
    }
    public void setSeisoConfig(SeisoConfig seisoConfig)
    {
        this.seisoConfig = seisoConfig;
    }
    public void setDuplicateDetectionConfig(DuplicateDetectionConfig duplicateDetectionConfig)
    {
        this.duplicateDetectionConfig = duplicateDetectionConfig;
    }
}