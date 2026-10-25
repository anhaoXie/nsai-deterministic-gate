package com.durgaprasad.nsai.local.rules;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable set of deterministic rules, keyed by intent.
 */
public final class RuleSet {

    private final Map<String, IntentRules> intents;
    private final UnknownIntentPolicy unknownIntentPolicy;

    public RuleSet(Map<String, IntentRules> intents, UnknownIntentPolicy unknownIntentPolicy) {
        this.intents = Collections.unmodifiableMap(new LinkedHashMap<>(intents));
        this.unknownIntentPolicy = unknownIntentPolicy == null ? UnknownIntentPolicy.DENY : unknownIntentPolicy;
    }

    public Optional<IntentRules> forIntent(String intent) {
        return Optional.ofNullable(intents.get(intent));
    }

    public Map<String, IntentRules> intents() {
        return intents;
    }

    public UnknownIntentPolicy unknownIntentPolicy() {
        return unknownIntentPolicy;
    }
}
