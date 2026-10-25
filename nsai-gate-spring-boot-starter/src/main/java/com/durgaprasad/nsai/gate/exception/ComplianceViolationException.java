package com.durgaprasad.nsai.gate.exception;

import com.durgaprasad.nsai.core.Violation;

import java.util.List;

/**
 * Thrown in {@code HARD_REJECT} mode when a neural proposal fails deterministic validation.
 * Carries the failed rules so callers can log, count or explain the rejection.
 * The raw proposal is deliberately not included, since it may contain personal data.
 */
public class ComplianceViolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String intent;
    private final transient List<Violation> violations;

    public ComplianceViolationException(String message) {
        this(message, null, List.of());
    }

    public ComplianceViolationException(String message, String intent, List<Violation> violations) {
        super(message);
        this.intent = intent;
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public String getIntent() {
        return intent;
    }

    public List<Violation> getViolations() {
        return violations;
    }
}
