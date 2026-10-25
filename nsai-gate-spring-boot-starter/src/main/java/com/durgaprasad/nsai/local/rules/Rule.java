package com.durgaprasad.nsai.local.rules;

import java.util.List;
import java.util.regex.Pattern;

/**
 * One deterministic check applied to a neural proposal.
 * <p>
 * A rule reads a {@code field} from the proposal (from JSON when the proposal is JSON,
 * otherwise with the {@code pattern} regex) and applies any of {@code required},
 * {@code min}, {@code max} and {@code allowed}. A rule may instead, or also, reject the
 * proposal whenever its raw text matches {@code forbidden}.
 *
 * @param id        stable rule identifier, reported in violations
 * @param field     proposal field to check; dot notation for nested JSON (e.g. {@code offer.rate})
 * @param pattern   regex used to extract the field from free text; group 1 is the value
 * @param required  whether the field must be present
 * @param min       inclusive numeric lower bound, or {@code null}
 * @param max       inclusive numeric upper bound, or {@code null}
 * @param allowed   allowed values (case-sensitive), or an empty list for no restriction
 * @param forbidden regex that must not match anywhere in the proposal, or {@code null}
 * @param message   optional custom message used when the rule fails
 * @param repair    whether a violation may be repaired in SOFT_REPAIR mode
 */
public record Rule(
        String id,
        String field,
        Pattern pattern,
        boolean required,
        Double min,
        Double max,
        List<String> allowed,
        Pattern forbidden,
        String message,
        RepairStrategy repair) {

    public Rule {
        allowed = allowed == null ? List.of() : List.copyOf(allowed);
        repair = repair == null ? RepairStrategy.NONE : repair;
    }

    boolean hasFieldChecks() {
        return required || min != null || max != null || !allowed.isEmpty();
    }
}
