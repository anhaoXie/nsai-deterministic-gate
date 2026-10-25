package com.durgaprasad.nsai.local.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleSetLoaderTest {

    private static RuleSet load(String yaml) {
        return RuleSetLoader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test.yaml");
    }

    @Test
    void loadsIntentsRulesAndPolicy() {
        RuleSet ruleSet = load("""
                unknownIntent: allow
                intents:
                  LOAN_OFFER:
                    description: Loan offers
                    rules:
                      - id: rate
                        field: interestRate
                        min: 1
                        max: 18.5
                      - id: currency
                        field: currency
                        allowed: [USD]
                        required: true
                        message: USD only
                """);

        assertThat(ruleSet.unknownIntentPolicy()).isEqualTo(UnknownIntentPolicy.ALLOW);
        IntentRules intent = ruleSet.forIntent("LOAN_OFFER").orElseThrow();
        assertThat(intent.description()).isEqualTo("Loan offers");
        assertThat(intent.rules()).extracting(Rule::id).containsExactly("rate", "currency");

        Rule rate = intent.rules().get(0);
        assertThat(rate.min()).isEqualTo(1.0);
        assertThat(rate.max()).isEqualTo(18.5);

        Rule currency = intent.rules().get(1);
        assertThat(currency.required()).isTrue();
        assertThat(currency.allowed()).containsExactly("USD");
        assertThat(currency.message()).isEqualTo("USD only");
    }

    @Test
    void unknownIntentPolicyDefaultsToDeny() {
        RuleSet ruleSet = load("""
                intents:
                  A:
                    rules:
                      - id: r
                        forbidden: 'x'
                """);

        assertThat(ruleSet.unknownIntentPolicy()).isEqualTo(UnknownIntentPolicy.DENY);
    }

    @Test
    @DisplayName("Typos in rule keys fail fast instead of silently weakening the gate")
    void rejectsUnknownRuleKey() {
        assertThatThrownBy(() -> load("""
                intents:
                  A:
                    rules:
                      - id: r
                        field: f
                        maxx: 10
                """))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("unknown key 'maxx'");
    }

    @Test
    void rejectsUnknownRootKey() {
        assertThatThrownBy(() -> load("intent: {}\n"))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("unknown key 'intent'");
    }

    @Test
    void requiresIntents() {
        assertThatThrownBy(() -> load("unknownIntent: deny\n"))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("'intents' is required");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> load(""))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("is empty");
    }

    @Test
    void rejectsIntentWithoutRules() {
        assertThatThrownBy(() -> load("intents:\n  A:\n    description: nothing\n"))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("'rules' must be a non-empty list");
    }

    @Test
    void requiresRuleId() {
        assertThatThrownBy(() -> load("""
                intents:
                  A:
                    rules:
                      - field: f
                        required: true
                """))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("'id' is required");
    }

    @Test
    void rejectsDuplicateRuleIds() {
        assertThatThrownBy(() -> load("""
                intents:
                  A:
                    rules:
                      - id: r
                        forbidden: 'x'
                      - id: r
                        forbidden: 'y'
                """))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("duplicate rule id 'r'");
    }

    @Test
    void rejectsRuleWithNoCheck() {
        assertThatThrownBy(() -> load("""
                intents:
                  A:
                    rules:
                      - id: r
                        field: f
                """))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("needs at least one of");
    }

    @Test
    void fieldChecksNeedAField() {
        assertThatThrownBy(() -> load("""
                intents:
                  A:
                    rules:
                      - id: r
                        max: 5
                """))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("'field' is required");
    }

    @Test
    void rejectsMinGreaterThanMax() {
        assertThatThrownBy(() -> load("""
                intents:
                  A:
                    rules:
                      - id: r
                        field: f
                        min: 10
                        max: 5
                """))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("min (10.0) is greater than max (5.0)");
    }

    @Test
    void rejectsNonNumericBound() {
        assertThatThrownBy(() -> load("""
                intents:
                  A:
                    rules:
                      - id: r
                        field: f
                        max: ten
                """))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("expected a number");
    }

    @Test
    void rejectsInvalidRegex() {
        assertThatThrownBy(() -> load("""
                intents:
                  A:
                    rules:
                      - id: r
                        forbidden: '(unclosed'
                """))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("invalid regex");
    }

    @Test
    void rejectsInvalidUnknownIntentPolicy() {
        assertThatThrownBy(() -> load("unknownIntent: maybe\nintents: {}\n"))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("unknownIntent must be 'deny' or 'allow'");
    }

    @Test
    @DisplayName("SafeConstructor refuses YAML tags that would instantiate Java classes")
    void refusesArbitraryJavaTypes() {
        assertThatThrownBy(() -> load("intents: !!javax.script.ScriptEngineManager []\n"))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("Cannot read rule file");
    }

    @Test
    void repairDefaultsToNoneAndAcceptsClamp() {
        RuleSet ruleSet = load("""
                intents:
                  A:
                    rules:
                      - id: rate
                        field: interestRate
                        max: 18
                        repair: clamp
                      - id: currency
                        field: currency
                        allowed: [USD]
                """);

        var rules = ruleSet.forIntent("A").orElseThrow().rules();
        assertThat(rules.get(0).repair()).isEqualTo(RepairStrategy.CLAMP);
        assertThat(rules.get(1).repair()).isEqualTo(RepairStrategy.NONE);
    }

    @Test
    void clampNeedsABound() {
        assertThatThrownBy(() -> load("""
                intents:
                  A:
                    rules:
                      - id: currency
                        field: currency
                        allowed: [USD]
                        repair: clamp
                """))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("repair: clamp needs a min or max");
    }

    @Test
    void rejectsUnknownRepairStrategy() {
        assertThatThrownBy(() -> load("""
                intents:
                  A:
                    rules:
                      - id: rate
                        field: interestRate
                        max: 18
                        repair: guess
                """))
                .isInstanceOf(RuleDefinitionException.class)
                .hasMessageContaining("repair must be 'none' or 'clamp'");
    }
}
