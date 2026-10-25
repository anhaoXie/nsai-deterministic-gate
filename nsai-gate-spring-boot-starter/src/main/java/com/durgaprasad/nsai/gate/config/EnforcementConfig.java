package com.durgaprasad.nsai.gate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Enforcement settings ({@code nsai.gateway.enforcement.*}).
 */
@ConfigurationProperties(prefix = "nsai.gateway.enforcement")
public class EnforcementConfig {
    
    private EnforcementMode mode = EnforcementMode.SOFT_REPAIR; // Default fallback

    public EnforcementMode getMode() {
        return mode;
    }

    public void setMode(EnforcementMode mode) {
        this.mode = mode;
    }
}