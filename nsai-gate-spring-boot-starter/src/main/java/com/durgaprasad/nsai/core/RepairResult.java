package com.durgaprasad.nsai.core;

import java.util.List;
import java.util.Objects;

/**
 * A proposal that was repaired and re-validated successfully.
 *
 * @param repairedProposal the corrected text returned to the caller
 * @param changes          every value that was changed, for the audit log
 */
public record RepairResult(String repairedProposal, List<RepairChange> changes) {

    public RepairResult {
        Objects.requireNonNull(repairedProposal, "repairedProposal");
        changes = changes == null ? List.of() : List.copyOf(changes);
        if (changes.isEmpty()) {
            throw new IllegalArgumentException("A repair must change at least one value");
        }
    }
}
