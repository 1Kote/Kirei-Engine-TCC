package br.com.tcc.kireiengine.config.model;

import java.util.List;

public class CleanTemporaryFoldersRule
{
    private boolean enabled;
    private List<String> folders;

    //Getters
    public boolean isEnabled()
    {
        return enabled;
    }
    public List<String> getFolders()
    {
        return folders;
    }
}