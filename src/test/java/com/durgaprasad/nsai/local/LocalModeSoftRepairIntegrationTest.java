package com.durgaprasad.nsai.local;

import com.durgaprasad.nsai.core.Violation;
import com.durgaprasad.nsai.demo.LoanOfferAssistant;
import com.durgaprasad.nsai.gate.exception.ComplianceViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The local profile in SOFT_REPAIR mode: real Spring context, YAML rules, mock LLM. */
@SpringBootTest(properties = {
        "nsai.demo.run-on-startup=false",
        "nsai.gateway.enforcement.mode=SOFT_REPAIR"
})
@ActiveProfiles("local")
class LocalModeSoftRepairIntegrationTest {

    @Autowired
    private LoanOfferAssistant assistant;

    @Test
    @DisplayName("A rate above the cap is clamped to 18% and the rest of the offer is kept")
    void rateAboveCapIsClampedToTheMaximum() {
        String result = assistant.draftOffer("Subprime customer wants a fast loan");

        assertThat(result)
                .contains("\"interestRate\":\"18%\"")
                .contains("\"clientId\":\"CUST-2002\"")
                .doesNotContain("25%");
    }

    @Test
    @DisplayName("Violations that cannot be repaired are still rejected")
    void unrepairableDraftIsRejected() {
        // missing client id, 3-month term and "guaranteed approval" have no repair: clamp
        assertThatThrownBy(() -> assistant.draftOffer("Please guarantee approval"))
                .isInstanceOfSatisfying(ComplianceViolationException.class, e ->
                        assertThat(e.getViolations()).extracting(Violation::ruleId)
                                .contains("client-id-required", "no-guaranteed-approval"));
    }

    @Test
    void compliantDraftIsUntouched() {
        assertThat(assistant.draftOffer("premium")).contains("CUST-1001").contains("6.9%");
    }
}
