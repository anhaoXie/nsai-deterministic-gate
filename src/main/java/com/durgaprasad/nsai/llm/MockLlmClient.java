package com.durgaprasad.nsai.llm;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic stand-in for an LLM. Returns a canned response for the first keyword
 * found in the prompt (case-insensitive, in configuration order), or a default response.
 * <p>
 * Lets anyone run the gate — and its tests — with no model, GPU or API key.
 */
public class MockLlmClient implements LlmClient {

    private final Map<String, String> responsesByKeyword;
    private final String defaultResponse;

    public MockLlmClient(Map<String, String> responsesByKeyword, String defaultResponse) {
        this.responsesByKeyword = new LinkedHashMap<>(responsesByKeyword == null ? Map.of() : responsesByKeyword);
        this.defaultResponse = defaultResponse == null ? "" : defaultResponse;
    }

    @Override
    public String complete(String prompt) {
        String lower = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        return responsesByKeyword.entrySet().stream()
                .filter(e -> lower.contains(e.getKey().toLowerCase(Locale.ROOT)))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(defaultResponse);
    }

    @Override
    public String name() {
        return "mock";
    }
}
