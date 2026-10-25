package com.durgaprasad.nsai.local.rules;

import com.durgaprasad.nsai.core.ValidationResult;
import com.durgaprasad.nsai.core.Violation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEvaluatorTest {

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
                  - id: currency
                    field: currency
                    allowed: [USD, CAD]
                  - id: no-guarantee
                    forbidden: '(?i)guaranteed approval'
                    message: No guarantees
              NESTED:
                rules:
                  - id: nested-limit
                    field: offer.amount
                    max: 50000
            """;

    private final RuleEvaluator evaluator = new RuleEvaluator(load(RULES));

    private static RuleSet load(String yaml) {
        return RuleSetLoader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test.yaml");
    }

    private static java.util.List<String> ruleIds(ValidationResult result) {
        return result.violations().stream().map(Violation::ruleId).toList();
    }

    @Test
    @DisplayName("Compliant JSON proposal passes every rule")
    void compliantJsonPasses() {
        ValidationResult result = evaluator.evaluate("LOAN_OFFER",
                "{\"clientId\":\"C-1\",\"interestRate\":\"6.9%\",\"currency\":\"USD\"}");

        assertThat(result.valid()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    @DisplayName("Value above max is reported with the field, value and limit")
    void maxViolation() {
        ValidationResult result = evaluator.evaluate("LOAN_OFFER",
                "{\"clientId\":\"C-1\",\"interestRate\":\"25%\",\"currency\":\"USD\"}");

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).singleElement()
                .satisfies(v -> {
                    assertThat(v.ruleId()).isEqualTo("rate");
                    assertThat(v.message()).contains("interestRate = 25 exceeds the maximum 18");
                });
    }

    @Test
    void minViolation() {
        ValidationResult result = evaluator.evaluate("LOAN_OFFER",
                "{\"clientId\":\"C-1\",\"interestRate\":1.5}");

        assertThat(ruleIds(result)).containsExactly("rate");
        assertThat(result.violations().get(0).message()).contains("below the minimum 3");
    }

    @Test
    void bothBoundsAreInclusive() {
        assertThat(evaluator.evaluate("LOAN_OFFER", "{\"clientId\":\"C\",\"interestRate\":3}").valid()).isTrue();
        assertThat(evaluator.evaluate("LOAN_OFFER", "{\"clientId\":\"C\",\"interestRate\":18}").valid()).isTrue();
    }

    @Test
    void missingRequiredField() {
        ValidationResult result = evaluator.evaluate("LOAN_OFFER", "{\"interestRate\":\"5%\"}");

        assertThat(ruleIds(result)).containsExactly("client-id");
        assertThat(result.violations().get(0).message()).contains("required field 'clientId' is missing");
    }

    @Test
    void valueNotInAllowedList() {
        ValidationResult result = evaluator.evaluate("LOAN_OFFER",
                "{\"clientId\":\"C-1\",\"interestRate\":5,\"currency\":\"EUR\"}");

        assertThat(ruleIds(result)).containsExactly("currency");
    }

    @Test
    void optionalFieldMayBeAbsent() {
        // currency has no 'required' flag, so leaving it out is fine
        assertThat(evaluator.evaluate("LOAN_OFFER", "{\"clientId\":\"C-1\",\"interestRate\":5}").valid()).isTrue();
    }

    @Test
    void nonNumericValueFailsNumericRule() {
        ValidationResult result = evaluator.evaluate("LOAN_OFFER", "{\"clientId\":\"C-1\",\"interestRate\":\"low\"}");

        assertThat(ruleIds(result)).containsExactly("rate");
        assertThat(result.violations().get(0).message()).contains("is not a number");
    }

    @Test
    @DisplayName("Forbidden text is caught and the custom message is used")
    void forbiddenText() {
        ValidationResult result = evaluator.evaluate("LOAN_OFFER",
                "{\"clientId\":\"C-1\",\"interestRate\":5,\"message\":\"Guaranteed approval today!\"}");

        assertThat(ruleIds(result)).containsExactly("no-guarantee");
        assertThat(result.violations().get(0).message())
                .startsWith("No guarantees")
                .contains("'Guaranteed approval'");
    }

    @Test
    @DisplayName("Free-text proposals are checked with each rule's regex")
    void freeTextExtraction() {
        ValidationResult ok = evaluator.evaluate("LOAN_OFFER", "Client ID: C-77. Your rate is 7.5% APR.");
        ValidationResult bad = evaluator.evaluate("LOAN_OFFER", "Client ID: C-77. Your rate is 29% APR.");

        assertThat(ok.valid()).isTrue();
        assertThat(ruleIds(bad)).containsExactly("rate");
    }

    @Test
    @DisplayName("Every occurrence is checked, so a compliant value cannot hide a violating one")
    void allOccurrencesChecked() {
        ValidationResult result = evaluator.evaluate("LOAN_OFFER",
                "Client ID: C-9. Choose a rate of 5% for 12 months, or a rate of 22% for 24 months.");

        assertThat(ruleIds(result)).containsExactly("rate");
        assertThat(result.violations().get(0).message()).contains("22");
    }

    @Test
    void jsonArrayValuesAreAllChecked() {
        ValidationResult result = evaluator.evaluate("LOAN_OFFER",
                "{\"clientId\":\"C-1\",\"interestRate\":[\"4%\",\"19%\"]}");

        assertThat(ruleIds(result)).containsExactly("rate");
    }

    @Test
    @DisplayName("JSON wrapped in Markdown fences and prose is still parsed")
    void toleratesCodeFences() {
        String proposal = "Here is the offer:\n```json\n{\"clientId\":\"C-1\",\"interestRate\":\"30%\"}\n```\nThanks!";

        assertThat(ruleIds(evaluator.evaluate("LOAN_OFFER", proposal))).containsExactly("rate");
    }

    @Test
    void nestedFieldPath() {
        assertThat(evaluator.evaluate("NESTED", "{\"offer\":{\"amount\":\"$45,000\"}}").valid()).isTrue();
        assertThat(ruleIds(evaluator.evaluate("NESTED", "{\"offer\":{\"amount\":60000}}")))
                .containsExactly("nested-limit");
    }

    @Test
    void multipleViolationsAreAllReported() {
        ValidationResult result = evaluator.evaluate("LOAN_OFFER",
                "Guaranteed approval at a rate of 40%!");

        assertThat(ruleIds(result)).containsExactlyInAnyOrder("client-id", "rate", "no-guarantee");
    }

    @Test
    @DisplayName("Unknown intents are denied by default (fail closed)")
    void unknownIntentDeniedByDefault() {
        ValidationResult result = evaluator.evaluate("WIRE_TRANSFER", "{}");

        assertThat(ruleIds(result)).containsExactly("unknown-intent");
    }

    @Test
    void unknownIntentAllowedWhenConfigured() {
        RuleEvaluator lenient = new RuleEvaluator(load("unknownIntent: allow\n" + RULES));

        assertThat(lenient.evaluate("WIRE_TRANSFER", "anything").valid()).isTrue();
    }

    @Test
    void nullProposalIsTreatedAsEmpty() {
        assertThat(ruleIds(evaluator.evaluate("LOAN_OFFER", null))).containsExactly("client-id");
    }

    @Test
    @DisplayName("Evaluation is deterministic: same input, same result")
    void deterministic() {
        String proposal = "Guaranteed approval at a rate of 40%!";

        assertThat(evaluator.evaluate("LOAN_OFFER", proposal)).isEqualTo(evaluator.evaluate("LOAN_OFFER", proposal));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "25%      | 25.0",
            "'$1,500' | 1500.0",
            "' 7.25 ' | 7.25",
            "1_000    | 1000.0"
    })
    void parsesCommonNumberFormats(String raw, double expected) {
        assertThat(RuleEvaluator.toNumber(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"abc", "NaN", "Infinity", "%"})
    void rejectsNonNumbers(String raw) {
        assertThat(RuleEvaluator.toNumber(raw)).isNull();
    }
}
