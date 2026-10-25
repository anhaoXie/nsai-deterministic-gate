package com.durgaprasad.nsai.core;

import java.util.Objects;

/**
 * A single deterministic rule that a neural proposal failed.
 *
 * @param ruleId  stable identifier of the rule that failed (for audit logs and metrics)
 * @param message human-readable explanation of the failure
 */
public record Violation(String ruleId, String message) {

    public Violation {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(message, "message");
    }

    @Override
    public String toString() {
        return ruleId + ": " + message;
    }
}
