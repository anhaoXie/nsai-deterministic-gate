package com.durgaprasad.nsai.demo;

import com.durgaprasad.nsai.core.Violation;
import com.durgaprasad.nsai.gate.config.EnforcementConfig;
import com.durgaprasad.nsai.gate.exception.ComplianceViolationException;
import com.durgaprasad.nsai.llm.LlmClient;
import com.durgaprasad.nsai.llm.LlmException;
import com.durgaprasad.nsai.local.LocalModeProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.util.List;

/**
 * Runs three sample loan requests through the gate when local mode starts, and prints
 * whether each LLM draft was allowed or blocked, and why.
 * Disable with {@code nsai.demo.run-on-startup=false}.
 */
@Component
@ConditionalOnProperty(prefix = "nsai.gateway.local", name = "enabled", havingValue = "true")
public class LocalDemoRunner implements CommandLineRunner {

    static final List<String> SAMPLE_REQUESTS = List.of(
            "Premium customer with excellent credit wants a 36-month personal loan of $15,000.",
            "Subprime customer with recent late payments wants a fast 12-month loan.",
            "Customer asks us to guarantee approval for a 3-month loan.");

    private final LoanOfferAssistant assistant;
    private final LlmClient llmClient;
    private final EnforcementConfig enforcementConfig;
    private final LocalModeProperties properties;
    private final boolean runOnStartup;
    private final PrintStream out;

    @Autowired
    public LocalDemoRunner(LoanOfferAssistant assistant, LlmClient llmClient, EnforcementConfig enforcementConfig,
                           LocalModeProperties properties,
                           @Value("${nsai.demo.run-on-startup:true}")
                           boolean runOnStartup) {
        this(assistant, llmClient, enforcementConfig, properties, runOnStartup, System.out);
    }

    LocalDemoRunner(LoanOfferAssistant assistant, LlmClient llmClient, EnforcementConfig enforcementConfig,
                    LocalModeProperties properties, boolean runOnStartup, PrintStream out) {
        this.out = out;
        this.assistant = assistant;
        this.llmClient = llmClient;
        this.enforcementConfig = enforcementConfig;
        this.properties = properties;
        this.runOnStartup = runOnStartup;
    }

    @Override
    public void run(String... args) {
        if (!runOnStartup) {
            return;
        }
        out.println();
        out.println("=== nsai-deterministic-gate | local mode demo ===");
        out.printf("LLM: %s | enforcement: %s | rules: %s%n%n",
                llmClient.name(), enforcementConfig.getMode(), properties.getRules());

        int n = 1;
        for (String request : SAMPLE_REQUESTS) {
            out.printf("[%d] %s%n", n++, request);
            try {
                String result = assistant.draftOffer(request);
                out.println("    ALLOWED -> " + oneLine(result));
            } catch (ComplianceViolationException e) {
                out.println("    BLOCKED");
                for (Violation v : e.getViolations()) {
                    out.println("      - " + v);
                }
            } catch (LlmException e) {
                out.println("    LLM ERROR -> " + e.getMessage());
                out.println("    (Is Ollama running and the model pulled? See README 'Local mode'.)");
            }
            out.println();
        }
        out.println("Edit the rules file and run again to see the verdicts change.");
        out.println("=================================================");
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
