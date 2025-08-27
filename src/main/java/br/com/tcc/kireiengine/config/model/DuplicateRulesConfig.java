package br.com.tcc.kireiengine.config.model;

/**
 * Regras específicas para detecção de duplicados
 */
public class DuplicateRulesConfig
{
    private long minFileSizeBytes;
    private long maxFileSizeBytes;
    private boolean autoRemove;
    private String duplicatesDestination;
    private String keepStrategy; // "NEWEST", "OLDEST", "MANUAL"

    //Getters
    public long getMinFileSizeBytes()
    {
        return minFileSizeBytes;
    }
    public long getMaxFileSizeBytes()
    {
        return maxFileSizeBytes;
    }
    public boolean isAutoRemove()
    {
        return autoRemove;
    }
    public String getDuplicatesDestination()
    {
        return duplicatesDestination;
    }
    public String getKeepStrategy()
    {
        return keepStrategy;
    }
}