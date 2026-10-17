package com.durgaprasad.nsai.local;

import com.durgaprasad.nsai.core.RepairChange;
import com.durgaprasad.nsai.core.RepairResult;
import com.durgaprasad.nsai.core.ValidationResult;
import com.durgaprasad.nsai.core.Violation;
import com.durgaprasad.nsai.local.rules.RuleEvaluator;
import com.durgaprasad.nsai.local.rules.RuleSetLoader;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalRuleConnectorTest {

    private final LocalRuleConnector connector = new LocalRuleConnector(new RuleEvaluator(
            RuleSetLoader.load(getClass().getResourceAsStream("/nsai-rules.yaml"), "nsai-rules.yaml")));

    @Test
    void identifiesItselfAsLocalRules() {
        assertThat(connector.getSourceIdentifier()).isEqualTo("LOCAL_RULES");
    }

    @Test
    void evaluateReportsTheFailedRuleAndValidateAgrees() {
        String proposal = "{\"clientId\":\"CUST-2002\",\"interestRate\":\"25%\",\"termMonths\":12,\"currency\":\"USD\"}";

        ValidationResult result = connector.evaluate("LOAN_OFFER", proposal, Map.of());

        assertThat(result.violations()).extracting(Violation::ruleId).containsExactly("max-interest-rate");
        assertThat(connector.validate("LOAN_OFFER", proposal, Map.of())).isFalse();
    }

    @Test
    void compliantOfferPasses() {
        String proposal = "{\"clientId\":\"CUST-1001\",\"interestRate\":\"6.9%\",\"termMonths\":36,\"currency\":\"USD\"}";

        assertThat(connector.validate("LOAN_OFFER", proposal, Map.of())).isTrue();
    }

    @Test
    void repairsAClampableViolationUsingTheShippedRules() {
        String proposal = "{\"clientId\":\"CUST-2002\",\"interestRate\":\"25%\",\"termMonths\":12,\"currency\":\"USD\"}";
        ValidationResult failed = connector.evaluate("LOAN_OFFER", proposal, Map.of());

        RepairResult result = connector.repair("LOAN_OFFER", proposal, failed, Map.of()).orElseThrow();

        assertThat(result.repairedProposal()).contains("\"interestRate\":\"18%\"");
        assertThat(result.changes())
                .containsExactly(new RepairChange("max-interest-rate", "interestRate", "25%", "18%"));
        assertThat(connector.validate("LOAN_OFFER", result.repairedProposal(), Map.of())).isTrue();
    }

    @Test
    void doesNotRepairRulesWithoutClamp() {
        String proposal = "{\"clientId\":\"CUST-2002\",\"interestRate\":\"6%\",\"currency\":\"EUR\"}";
        ValidationResult failed = connector.evaluate("LOAN_OFFER", proposal, Map.of());

        assertThat(connector.repair("LOAN_OFFER", proposal, failed, Map.of())).isEmpty();
    }
}
