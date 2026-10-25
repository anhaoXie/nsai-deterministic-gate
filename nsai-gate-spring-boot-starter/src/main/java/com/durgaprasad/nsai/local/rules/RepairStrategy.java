package com.durgaprasad.nsai.local.rules;

/**
 * How a rule may be repaired when it fails in SOFT_REPAIR mode.
 * Declared per rule in the YAML file with {@code repair: clamp}.
 */
public enum RepairStrategy {
    /** The rule cannot be repaired automatically; a violation always blocks. The default. */
    NONE,
    /** A numeric value outside [min, max] may be clamped to the nearest bound. */
    CLAMP
}
