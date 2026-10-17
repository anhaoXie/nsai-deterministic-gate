package com.durgaprasad.nsai.local;

import com.durgaprasad.nsai.core.SymbolicConnector;
import com.durgaprasad.nsai.core.Violation;
import com.durgaprasad.nsai.demo.LoanOfferAssistant;
import com.durgaprasad.nsai.gate.exception.ComplianceViolationException;
import com.durgaprasad.nsai.llm.LlmClient;
import com.durgaprasad.nsai.llm.MockLlmClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end test of local mode: real Spring context, real AOP proxy, YAML rules and
 * the mock LLM. Proves the gate runs with no cloud account and no Neo4j server.
 */
@SpringBootTest(properties = "nsai.demo.run-on-startup=false")
@ActiveProfiles("local")
class LocalModeIntegrationTest {

    @Autowired
    private LoanOfferAssistant assistant;

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private List<SymbolicConnector> connectors;

    @Test
    void localModeWiresMockLlmAndLocalRules() {
        assertThat(llmClient).isInstanceOf(MockLlmClient.class);
        assertThat(connectors).extracting(SymbolicConnector::getSourceIdentifier).contains("LOCAL_RULES");
    }

    @Test
    @DisplayName("Compliant LLM draft is returned to the caller")
    void compliantDraftIsAllowed() {
        String offer = assistant.draftOffer("Premium customer wants a 36-month loan");

        assertThat(offer).contains("\"interestRate\": \"6.9%\"");
    }

    @Test
    @DisplayName("Draft above the rate cap is blocked in HARD_REJECT mode")
    void rateAboveCapIsBlocked() {
        assertThatThrownBy(() -> assistant.draftOffer("Subprime customer wants a fast loan"))
                .isInstanceOfSatisfying(ComplianceViolationException.class, e ->
                        assertThat(e.getViolations()).extracting(Violation::ruleId)
                                .containsExactly("max-interest-rate"));
    }

    @Test
    @DisplayName("Free-text draft with several problems reports every failed rule")
    void freeTextDraftReportsAllViolations() {
        assertThatThrownBy(() -> assistant.draftOffer("Please guarantee approval"))
                .isInstanceOfSatisfying(ComplianceViolationException.class, e ->
                        assertThat(e.getViolations()).extracting(Violation::ruleId)
                                .containsExactlyInAnyOrder("client-id-required", "term-range", "no-guaranteed-approval"));
    }
}
