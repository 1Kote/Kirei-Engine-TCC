package br.com.tcc.kireiengine.config.model;

import java.util.concurrent.TimeUnit;

public class SeiriConfig
{
        //Atributos de classe
        private boolean enabled;
        private long initialDelay;
        private long period;
        private TimeUnit timeUnit;
        private SeiriRuleConfig rules;

        // Getters
        public boolean isEnabled() { return enabled; }
        public long getInitialDelay() { return initialDelay; }
        public long getPeriod() { return period; }
        public TimeUnit getTimeUnit() { return timeUnit; }
        public SeiriRuleConfig getRules() { return rules; }
}




