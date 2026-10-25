package com.durgaprasad.nsai.local.rules;

/**
 * What to do with a proposal whose intent has no rules defined.
 */
public enum UnknownIntentPolicy {
    /** Reject the proposal (fail closed). The default, and the safe choice for guardrails. */
    DENY,
    /** Let the proposal through unchecked. */
    ALLOW
}
