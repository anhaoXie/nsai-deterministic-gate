package com.durgaprasad.nsai.llm;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MockLlmClientTest {

    @Test
    void returnsResponseForFirstMatchingKeywordIgnoringCase() {
        Map<String, String> responses = new LinkedHashMap<>();
        responses.put("premium", "A");
        responses.put("loan", "B");
        MockLlmClient client = new MockLlmClient(responses, "default");

        assertThat(client.complete("PREMIUM customer wants a loan")).isEqualTo("A");
        assertThat(client.complete("Any loan please")).isEqualTo("B");
    }

    @Test
    void fallsBackToDefaultResponse() {
        MockLlmClient client = new MockLlmClient(Map.of("premium", "A"), "default");

        assertThat(client.complete("hello")).isEqualTo("default");
        assertThat(client.complete(null)).isEqualTo("default");
        assertThat(client.name()).isEqualTo("mock");
    }

    @Test
    void handlesNullConfiguration() {
        assertThat(new MockLlmClient(null, null).complete("x")).isEmpty();
    }
}
