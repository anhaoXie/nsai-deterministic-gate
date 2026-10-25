package com.durgaprasad.nsai.local;

import com.durgaprasad.nsai.local.rules.RuleSet;
import com.durgaprasad.nsai.local.rules.RuleSetLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ShippedRulesTest {

    @Test
    @DisplayName("The rule file shipped with the demo is valid")
    void shippedRuleFileLoads() {
        InputStream in = getClass().getResourceAsStream("/nsai-rules.yaml");
        assertThat(in).isNotNull();

        RuleSet ruleSet = RuleSetLoader.load(in, "nsai-rules.yaml");

        assertThat(ruleSet.forIntent("LOAN_OFFER")).isPresent();
    }
}
