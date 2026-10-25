package com.durgaprasad.nsai.core;

import java.util.Objects;

/**
 * One value changed by a deterministic repair.
 *
 * @param ruleId the rule whose violation was repaired
 * @param field  the field that changed
 * @param from   the original value, as written in the proposal
 * @param to     the repaired value
 */
public record RepairChange(String ruleId, String field, String from, String to) {

    public RepairChange {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }

    @Override
    public String toString() {
        return field + ": " + from + " -> " + to + " (" + ruleId + ")";
    }
}
