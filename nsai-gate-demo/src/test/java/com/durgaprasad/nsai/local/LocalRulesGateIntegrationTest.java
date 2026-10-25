package com.durgaprasad.nsai.local;

import com.durgaprasad.nsai.annotation.NSDeterministicGate;
import com.durgaprasad.nsai.core.Violation;
import com.durgaprasad.nsai.gate.exception.ComplianceViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End to end through Spring: a real AOP proxy around an annotated method, the YAML rules
 * loaded at startup, and HARD_REJECT enforcement. The "LLM" here simply returns the text
 * it is given, so each test controls exactly what the gate sees.
 */
@SpringBootTest(properties = {
        "nsai.gateway.local.enabled=true",
        "nsai.gateway.enforcement.mode=HARD_REJECT",
        "nsai.demo.run-on-startup=false"
})
@Import(LocalRulesGateIntegrationTest.LoanOfferService.class)
class LocalRulesGateIntegrationTest {

    /** Stand-in for an AI service: returns the "model output" it is given. */
    static class LoanOfferService {

        @NSDeterministicGate(intent = "LOAN_OFFER", symbolicSource = LocalRuleConnector.SOURCE_ID)
        public String draftOffer(String modelOutput) {
            return modelOutput;
        }

        @NSDeterministicGate(intent = "WIRE_TRANSFER", symbolicSource = LocalRuleConnector.SOURCE_ID)
        public String draftTransfer(String modelOutput) {
            return modelOutput;
        }
    }

    @Autowired
    private LoanOfferService service;

    @Test
    @DisplayName("Compliant offer is returned unchanged")
    void compliantOfferIsAllowed() {
        String offer = "{\"clientId\":\"CUST-1001\",\"interestRate\":\"6.9%\",\"termMonths\":36,\"currency\":\"USD\"}";

        assertThat(service.draftOffer(offer)).isEqualTo(offer);
    }

    @Test
    @DisplayName("Rate above the 18% cap is blocked with the rule that failed")
    void rateAboveCapIsBlocked() {
        String offer = "{\"clientId\":\"CUST-2002\",\"interestRate\":\"25%\",\"termMonths\":12,\"currency\":\"USD\"}";

        assertThatThrownBy(() -> service.draftOffer(offer))
                .isInstanceOfSatisfying(ComplianceViolationException.class, e ->
                        assertThat(e.getViolations()).extracting(Violation::ruleId)
                                .containsExactly("max-interest-rate"));
    }

    @Test
    @DisplayName("Free-text offer with several problems reports every failed rule")
    void freeTextOfferReportsAllViolations() {
        String offer = "Great news! You have guaranteed approval at an interest rate of 4.5% for 3 months.";

        assertThatThrownBy(() -> service.draftOffer(offer))
                .isInstanceOfSatisfying(ComplianceViolationException.class, e ->
                        assertThat(e.getViolations()).extracting(Violation::ruleId)
                                .containsExactlyInAnyOrder("client-id-required", "term-range", "no-guaranteed-approval"));
    }

    @Test
    @DisplayName("An intent with no rules is denied (fail closed)")
    void unknownIntentIsDenied() {
        assertThatThrownBy(() -> service.draftTransfer("{\"amount\": 100}"))
                .isInstanceOfSatisfying(ComplianceViolationException.class, e ->
                        assertThat(e.getViolations()).extracting(Violation::ruleId)
                                .containsExactly("unknown-intent"));
    }
}
