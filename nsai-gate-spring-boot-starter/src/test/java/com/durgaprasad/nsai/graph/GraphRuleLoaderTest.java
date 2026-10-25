package com.durgaprasad.nsai.graph;

import com.durgaprasad.nsai.graph.GraphRuleLoader.RuleNode;
import com.durgaprasad.nsai.local.rules.IntentRules;
import com.durgaprasad.nsai.local.rules.RepairStrategy;
import com.durgaprasad.nsai.local.rules.Rule;
import com.durgaprasad.nsai.local.rules.RuleDefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Turning rule nodes into rules, without a database. */
class GraphRuleLoaderTest {

    private static RuleNode node(String elementId, int depth, Map<String, Object> properties) {
        return new RuleNode(elementId, depth, properties);
    }

    @Test
    @DisplayName("Graph properties map to the same rule as the YAML keys (Neo4j integers and lists included)")
    void mapsNodePropertiesToRules() {
        IntentRules rules = GraphRuleLoader.assemble("LOAN_OFFER", "Loan offers", List.of(
                node("4:a:1", 1, Map.of("id", "term-range", "field", "termMonths", "min", 6L, "max", 84L)),
                node("4:a:2", 2, Map.of("id", "max-interest-rate", "field", "interestRate", "max", 18.0,
                        "required", true, "repair", "clamp")),
                node("4:a:3", 2, Map.of("id", "supported-currency", "field", "currency",
                        "allowed", List.of("USD", "CAD")))));

        assertThat(rules.intent()).isEqualTo("LOAN_OFFER");
        assertThat(rules.description()).isEqualTo("Loan offers");
        assertThat(rules.rules()).extracting(Rule::id)
                .containsExactly("term-range", "max-interest-rate", "supported-currency");
        Rule rate = rules.rules().get(1);
        assertThat(rate.max()).isEqualTo(18.0);
        assertThat(rate.required()).isTrue();
        assertThat(rate.repair()).isEqualTo(RepairStrategy.CLAMP);
        assertThat(rules.rules().get(0).min()).isEqualTo(6.0);
        assertThat(rules.rules().get(2).allowed()).containsExactly("USD", "CAD");
    }

    @Test
    @DisplayName("The intent's own rule overrides an inherited rule with the same id")
    void nearestDefinitionWins() {
        IntentRules rules = GraphRuleLoader.assemble("LOAN_OFFER", "", List.of(
                node("4:a:9", 2, Map.of("id", "max-interest-rate", "field", "interestRate", "max", 18.0)),
                node("4:a:1", 1, Map.of("id", "max-interest-rate", "field", "interestRate", "max", 12.0))));

        assertThat(rules.rules()).singleElement().satisfies(r -> assertThat(r.max()).isEqualTo(12.0));
    }

    @Test
    @DisplayName("A rule reached through two inheritance paths is used once")
    void sameNodeTwiceIsUsedOnce() {
        Map<String, Object> props = Map.of("id", "no-guarantee", "forbidden", "(?i)guaranteed approval");

        IntentRules rules = GraphRuleLoader.assemble("A", "", List.of(node("4:a:5", 2, props), node("4:a:5", 3, props)));

        assertThat(rules.rules()).hasSize(1);
    }

    @Test
    void sameIdTwiceAtTheSameLevelIsAnError() {
        assertThatThrownBy(() -> GraphRuleLoader.assemble("A", "", List.of(
                node("4:a:1", 1, Map.of("id", "r", "field", "f", "max", 1.0)),
                node("4:a:2", 1, Map.of("id", "r", "field", "f", "max", 2.0)))))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("rule id 'r' is defined twice at the same level");
    }

    @Test
    @DisplayName("A typo in a rule node is rejected, as in the YAML file")
    void unknownPropertyIsAnError() {
        assertThatThrownBy(() -> GraphRuleLoader.assemble("A", "", List.of(
                node("4:a:7", 1, Map.of("id", "r", "field", "f", "maxx", 5L)))))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("Neo4j rule node 4:a:7")
                .hasMessageContaining("unknown key 'maxx'");
    }

    @Test
    void invalidRegexIsAnError() {
        assertThatThrownBy(() -> GraphRuleLoader.assemble("A", "", List.of(
                node("4:a:8", 1, Map.of("id", "r", "forbidden", "(unclosed")))))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("invalid regex");
    }

    @Test
    void intentWithoutRulesIsAnError() {
        assertThatThrownBy(() -> GraphRuleLoader.assemble("EMPTY", "", List.of()))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("has no rules");
    }
}
