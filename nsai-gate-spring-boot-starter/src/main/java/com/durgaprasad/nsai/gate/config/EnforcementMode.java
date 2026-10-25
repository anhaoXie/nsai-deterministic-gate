package com.durgaprasad.nsai.gate.config;

public enum EnforcementMode {
    SOFT_REPAIR,  // Routes to SLM for automated patching
    HARD_REJECT   // Instantly drops payload and throws an exception
}