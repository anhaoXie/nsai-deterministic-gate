package com.durgaprasad.nsai.local.rules;

/**
 * Thrown at startup when a rule file is malformed, so mistakes fail fast instead of
 * silently weakening the gate.
 */
public class RuleDefinitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RuleDefinitionException(String message) {
        super(message);
    }

    public RuleDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
