package com.durgaprasad.nsai.demo;

import com.durgaprasad.nsai.core.Violation;
import com.durgaprasad.nsai.gate.config.EnforcementConfig;
import com.durgaprasad.nsai.gate.exception.ComplianceViolationException;
import com.durgaprasad.nsai.llm.LlmClient;
import com.durgaprasad.nsai.llm.LlmException;
import com.durgaprasad.nsai.local.LocalModeProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LocalDemoRunnerTest {

    private final LoanOfferAssistant assistant = mock(LoanOfferAssistant.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    private LocalDemoRunner runner(boolean runOnStartup) {
        when(llmClient.name()).thenReturn("mock");
        return new LocalDemoRunner(assistant, llmClient, new EnforcementConfig(), new LocalModeProperties(),
                runOnStartup, new PrintStream(buffer, true, StandardCharsets.UTF_8));
    }

    private String output() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void printsVerdictForEverySampleRequest() {
        List<String> requests = LocalDemoRunner.SAMPLE_REQUESTS;
        when(assistant.draftOffer(requests.get(0))).thenReturn("{\"ok\": true}");
        when(assistant.draftOffer(requests.get(1))).thenThrow(new ComplianceViolationException(
                "blocked", "LOAN_OFFER", List.of(new Violation("max-interest-rate", "too high"))));
        when(assistant.draftOffer(requests.get(2))).thenThrow(new LlmException("connection refused"));

        runner(true).run();

        assertThat(output())
                .contains("LLM: mock")
                .contains("ALLOWED -> {\"ok\": true}")
                .contains("BLOCKED")
                .contains("- max-interest-rate: too high")
                .contains("LLM ERROR -> connection refused");
    }

    @Test
    void canBeDisabled() {
        LocalDemoRunner runner = new LocalDemoRunner(assistant, llmClient, new EnforcementConfig(),
                new LocalModeProperties(), false, new PrintStream(buffer, true, StandardCharsets.UTF_8));

        runner.run();

        assertThat(output()).isEmpty();
        verifyNoInteractions(assistant);
    }

    @Test
    void promptAsksForJsonAndIncludesRequest() {
        LlmClient echo = mock(LlmClient.class);
        when(echo.complete(anyString())).thenAnswer(inv -> inv.getArgument(0));

        String prompt = new LoanOfferAssistant(echo).draftOffer("36-month loan");

        assertThat(prompt).contains("single JSON object").contains("\"7.5%\"").contains("36-month loan");
    }
}
