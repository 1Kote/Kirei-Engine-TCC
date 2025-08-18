package br.com.tcc.kireiengine.config.model;

public class MoveFilesRule
{
    private boolean enabled;
    private int days;
    private String destination;

    //Getters
    public boolean isEnabled() { return enabled; }
    public int getDays() { return days; }
    public String getDestination() { return destination; }
}