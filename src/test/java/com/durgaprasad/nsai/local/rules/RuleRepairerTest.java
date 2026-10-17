package com.durgaprasad.nsai.local.rules;

import com.durgaprasad.nsai.core.RepairChange;
import com.durgaprasad.nsai.core.RepairResult;
import com.durgaprasad.nsai.core.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RuleRepairerTest {

    private static final String RULES = """
            intents:
              LOAN_OFFER:
                rules:
                  - id: client-id
                    field: clientId
                    pattern: '(?i)client\\s*id\\W+([A-Z0-9-]+)'
                    required: true
                  - id: rate
                    field: interestRate
                    pattern: '(?i)\\brate\\b\\D{0,20}?(\\d+(?:\\.\\d+)?)\\s*%'
                    min: 3
                    max: 18
                    repair: clamp
                  - id: term
                    field: termMonths
                    min: 6
                    max: 84
                  - id: no-guarantee
                    forbidden: '(?i)guaranteed approval'
              NESTED:
                rules:
                  - id: amount
                    field: offer.amount
                    max: 50000
                    repair: clamp
            """;

    private final RuleEvaluator evaluator = new RuleEvaluator(
            RuleSetLoader.load(new ByteArrayInputStream(RULES.getBytes(StandardCharsets.UTF_8)), "test.yaml"));
    private final RuleRepairer repairer = new RuleRepairer(evaluator);

    private Optional<RepairResult> repair(String intent, String proposal) {
        ValidationResult failed = evaluator.evaluate(intent, proposal);
        assertThat(failed.valid()).as("test proposal must fail validation first").isFalse();
        return repairer.repair(intent, proposal, failed);
    }

    @Test
    @DisplayName("A JSON rate above the maximum is clamped to the maximum, keeping the % suffix")
    void clampsJsonStringToMax() {
        RepairResult result = repair("LOAN_OFFER",
                "{\"clientId\":\"C-1\",\"interestRate\":\"25%\",\"termMonths\":12}").orElseThrow();

        assertThat(result.repairedProposal())
                .contains("\"interestRate\":\"18%\"")
                .contains("\"clientId\":\"C-1\"")
                .contains("\"termMonths\":12");
        assertThat(result.changes()).containsExactly(new RepairChange("rate", "interestRate", "25%", "18%"));
        assertThat(evaluator.evaluate("LOAN_OFFER", result.repairedProposal()).valid()).isTrue();
    }

    @Test
    void clampsJsonNumberToMin() {
        RepairResult result = repair("LOAN_OFFER", "{\"clientId\":\"C-1\",\"interestRate\":1.5}").orElseThrow();

        assertThat(result.repairedProposal()).contains("\"interestRate\":3");
        assertThat(result.changes()).singleElement().satisfies(c -> {
            assertThat(c.from()).isEqualTo("1.5");
            assertThat(c.to()).isEqualTo("3");
        });
    }

    @Test
    void keepsDecimalBounds() {
        RuleEvaluator decimal = new RuleEvaluator(RuleSetLoader.load(new ByteArrayInputStream("""
                intents:
                  A:
                    rules:
                      - id: r
                        field: rate
                        max: 17.5
                        repair: clamp
                """.getBytes(StandardCharsets.UTF_8)), "test.yaml"));
        String proposal = "{\"rate\":\"20%\"}";

        RepairResult result = new RuleRepairer(decimal)
                .repair("A", proposal, decimal.evaluate("A", proposal)).orElseThrow();

        assertThat(result.repairedProposal()).isEqualTo("{\"rate\":\"17.5%\"}");
    }

    @Test
    @DisplayName("Every out-of-range array element is clamped; in-range ones are left alone")
    void clampsArrayElements() {
        RepairResult result = repair("LOAN_OFFER",
                "{\"clientId\":\"C-1\",\"interestRate\":[\"4%\",\"19%\",\"2%\"]}").orElseThrow();

        assertThat(result.repairedProposal()).contains("\"interestRate\":[\"4%\",\"18%\",\"3%\"]");
        assertThat(result.changes()).extracting(RepairChange::to).containsExactly("18%", "3%");
    }

    @Test
    void clampsNestedField() {
        RepairResult result = repair("NESTED", "{\"offer\":{\"amount\":60000,\"term\":24}}").orElseThrow();

        assertThat(result.repairedProposal()).isEqualTo("{\"offer\":{\"amount\":50000,\"term\":24}}");
    }

    @Test
    @DisplayName("Text around the JSON (prose, code fences) is preserved")
    void preservesTextAroundJson() {
        RepairResult result = repair("LOAN_OFFER",
                "Here you go:\n```json\n{\"clientId\":\"C-1\",\"interestRate\":\"30%\"}\n```\nThanks!").orElseThrow();

        assertThat(result.repairedProposal())
                .startsWith("Here you go:\n```json\n{")
                .endsWith("}\n```\nThanks!")
                .contains("\"interestRate\":\"18%\"");
    }

    @Test
    @DisplayName("In free text only the matched number changes, every occurrence is clamped")
    void clampsFreeText() {
        RepairResult result = repair("LOAN_OFFER",
                "Client ID: C-9. Choose a rate of 5% for 12 months, or a rate of 22% for 24 months.").orElseThrow();

        assertThat(result.repairedProposal())
                .isEqualTo("Client ID: C-9. Choose a rate of 5% for 12 months, or a rate of 18% for 24 months.");
        assertThat(result.changes()).containsExactly(new RepairChange("rate", "interestRate", "22", "18"));
    }

    @Test
    @DisplayName("Nothing is repaired if any failed rule is not marked repair: clamp")
    void refusesWhenAnyViolationIsNotClampable() {
        // the rate could be clamped, but the term rule has no repair strategy
        assertThat(repair("LOAN_OFFER", "{\"clientId\":\"C-1\",\"interestRate\":\"25%\",\"termMonths\":3}")).isEmpty();
        assertThat(repair("LOAN_OFFER", "Guaranteed approval! Client ID: C-1, rate of 25%.")).isEmpty();
    }

    @Test
    void refusesMissingRequiredField() {
        assertThat(repair("LOAN_OFFER", "{\"interestRate\":\"25%\"}")).isEmpty();
    }

    @Test
    void refusesNonNumericValue() {
        assertThat(repair("LOAN_OFFER", "{\"clientId\":\"C-1\",\"interestRate\":\"very high\"}")).isEmpty();
    }

    @Test
    void refusesUnknownIntent() {
        assertThat(repair("WIRE_TRANSFER", "{\"amount\":1}")).isEmpty();
    }

    @Test
    void returnsEmptyForPassingOrNullInput() {
        assertThat(repairer.repair("LOAN_OFFER", "x", ValidationResult.pass())).isEmpty();
        assertThat(repairer.repair("LOAN_OFFER", null, ValidationResult.fail("rate", "x"))).isEmpty();
        assertThat(repairer.repair("LOAN_OFFER", "x", null)).isEmpty();
    }

    @Test
    @DisplayName("A violation id the rule file does not know is never repaired")
    void refusesUnknownRuleId() {
        assertThat(repairer.repair("LOAN_OFFER", "{\"clientId\":\"C-1\",\"interestRate\":\"25%\"}",
                ValidationResult.fail("made-up", "x"))).isEmpty();
    }

    @Test
    @DisplayName("Repair is deterministic: same input, same output")
    void deterministic() {
        String proposal = "{\"clientId\":\"C-1\",\"interestRate\":\"25%\"}";

        assertThat(repair("LOAN_OFFER", proposal)).isEqualTo(repair("LOAN_OFFER", proposal));
    }
}
