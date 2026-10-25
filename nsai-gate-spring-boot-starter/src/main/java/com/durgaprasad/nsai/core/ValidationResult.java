package com.durgaprasad.nsai.core;

import java.util.List;

/**
 * Outcome of validating a neural proposal against a symbolic source.
 *
 * @param valid      {@code true} when the proposal satisfies every rule
 * @param violations the rules that failed; empty when {@code valid} is {@code true}
 */
public record ValidationResult(boolean valid, List<Violation> violations) {

    private static final ValidationResult PASS = new ValidationResult(true, List.of());

    public ValidationResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
        if (valid && !violations.isEmpty()) {
            throw new IllegalArgumentException("A valid result cannot carry violations");
        }
        if (!valid && violations.isEmpty()) {
            throw new IllegalArgumentException("A failed result needs at least one violation");
        }
    }

    public static ValidationResult pass() {
        return PASS;
    }

    public static ValidationResult fail(List<Violation> violations) {
        return new ValidationResult(false, violations);
    }

    public static ValidationResult fail(String ruleId, String message) {
        return fail(List.of(new Violation(ruleId, message)));
    }
}
