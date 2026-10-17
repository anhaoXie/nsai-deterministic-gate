package com.durgaprasad.nsai.demo;

import com.durgaprasad.nsai.annotation.NSDeterministicGate;
import com.durgaprasad.nsai.llm.LlmClient;
import com.durgaprasad.nsai.local.LocalRuleConnector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Example AI service for a fictional retail bank: asks the LLM to draft a personal-loan
 * offer, and lets {@link NSDeterministicGate} check the draft against the local rules
 * before anyone sees it.
 */
@Service
@ConditionalOnProperty(prefix = "nsai.gateway.local", name = "enabled", havingValue = "true")
public class LoanOfferAssistant {

    static final String PROMPT_TEMPLATE = """
            You are an assistant for Example Bank drafting personal-loan offers.
            Reply with a single JSON object and nothing else, using exactly these fields:
              "clientId"     (string),
              "interestRate" (annual rate as a string with %%, e.g. "7.5%%"),
              "termMonths"   (integer),
              "currency"     (ISO code, e.g. "USD"),
              "message"      (one friendly sentence to the customer).
            Customer request: %s
            """;

    private final LlmClient llmClient;

    public LoanOfferAssistant(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @NSDeterministicGate(intent = "LOAN_OFFER", symbolicSource = LocalRuleConnector.SOURCE_ID)
    public String draftOffer(String customerRequest) {
        return llmClient.complete(PROMPT_TEMPLATE.formatted(customerRequest));
    }
}
