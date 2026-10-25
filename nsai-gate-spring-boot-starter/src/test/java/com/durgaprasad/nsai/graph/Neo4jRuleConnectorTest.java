package com.durgaprasad.nsai.graph;

import com.durgaprasad.nsai.core.ValidationResult;
import com.durgaprasad.nsai.core.Violation;
import com.durgaprasad.nsai.local.rules.IntentRules;
import com.durgaprasad.nsai.local.rules.RuleDefinitionException;
import com.durgaprasad.nsai.local.rules.RuleSetLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.exceptions.ServiceUnavailableException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Fail-closed behaviour of the graph connector, with the loader mocked. */
class Neo4jRuleConnectorTest {

    private static final String OFFER = "{\"clientId\":\"C-1\",\"interestRate\":\"25%\"}";

    private final GraphRuleLoader loader = mock(GraphRuleLoader.class);
    private final Neo4jRuleConnector connector = new Neo4jRuleConnector(loader);

    private static IntentRules rateRule() {
        return new IntentRules("LOAN_OFFER", "", List.of(RuleSetLoader.ruleFrom(
                Map.of("id", "max-interest-rate", "field", "interestRate", "max", 18.0, "repair", "clamp"), "test")));
    }

    private static List<String> ruleIds(ValidationResult result) {
        return result.violations().stream().map(Violation::ruleId).toList();
    }

    @Test
    void identifiesItselfAsNeo4jRules() {
        assertThat(connector.getSourceIdentifier()).isEqualTo("NEO4J_RULES");
    }

    @Test
    void evaluatesWithTheLoadedRules() {
        when(loader.load("LOAN_OFFER")).thenReturn(Optional.of(rateRule()));

        assertThat(ruleIds(connector.evaluate("LOAN_OFFER", OFFER, Map.of()))).containsExactly("max-interest-rate");
        assertThat(connector.validate("LOAN_OFFER", "{\"interestRate\":\"9%\"}", Map.of())).isTrue();
    }

    @Test
    void repairsWithTheLoadedRules() {
        when(loader.load("LOAN_OFFER")).thenReturn(Optional.of(rateRule()));
        ValidationResult failed = connector.evaluate("LOAN_OFFER", OFFER, Map.of());

        assertThat(connector.repair("LOAN_OFFER", OFFER, failed, Map.of()).orElseThrow().repairedProposal())
                .contains("\"interestRate\":\"18%\"");
    }

    @Test
    @DisplayName("An intent that is not in the graph is denied")
    void unknownIntentIsDenied() {
        when(loader.load("WIRE_TRANSFER")).thenReturn(Optional.empty());

        assertThat(ruleIds(connector.evaluate("WIRE_TRANSFER", "{}", Map.of()))).containsExactly("unknown-intent");
    }

    @Test
    @DisplayName("Neo4j unreachable: the proposal is blocked, never allowed through")
    void databaseDownBlocks() {
        when(loader.load("LOAN_OFFER")).thenThrow(new ServiceUnavailableException("connection refused"));

        ValidationResult result = connector.evaluate("LOAN_OFFER", "{\"interestRate\":\"9%\"}", Map.of());

        assertThat(result.valid()).isFalse();
        assertThat(ruleIds(result)).containsExactly("rule-source-unavailable");
        assertThat(connector.repair("LOAN_OFFER", OFFER, result, Map.of())).isEmpty();
    }

    @Test
    @DisplayName("An invalid rule node blocks the proposal and names the problem")
    void invalidRuleBlocks() {
        when(loader.load("LOAN_OFFER")).thenThrow(new RuleDefinitionException("Neo4j rule node 4:a:7 (r): unknown key 'maxx'"));

        ValidationResult result = connector.evaluate("LOAN_OFFER", "{}", Map.of());

        assertThat(ruleIds(result)).containsExactly("invalid-rule-definition");
        assertThat(result.violations().get(0).message()).contains("unknown key 'maxx'");
    }
}
