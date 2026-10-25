package com.durgaprasad.nsai.local.rules;

import java.util.List;

/**
 * The rules that apply to one business intent (for example {@code LOAN_OFFER}).
 */
public record IntentRules(String intent, String description, List<Rule> rules) {

    public IntentRules {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
