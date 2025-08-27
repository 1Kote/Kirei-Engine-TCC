package br.com.tcc.kireiengine.config.model;

import java.util.concurrent.TimeUnit;

/**
 * Configuração para detecção de duplicados
 */
public class DuplicateDetectionConfig
{
    private boolean enabled;
    private long initialDelay;
    private long period;
    private TimeUnit timeUnit;
    private DuplicateRulesConfig rules;

    //Getters
    public boolean isEnabled() { return enabled; }
    public long getInitialDelay() { return initialDelay; }
    public long getPeriod() { return period; }
    public TimeUnit getTimeUnit() { return timeUnit; }
    public DuplicateRulesConfig getRules() { return rules; }
}