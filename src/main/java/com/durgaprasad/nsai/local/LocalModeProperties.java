package com.durgaprasad.nsai.local;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings for local mode ({@code nsai.gateway.local.*}): a YAML rule engine plus a
 * mock or Ollama LLM, so the gate runs with no cloud account or graph database.
 */
@ConfigurationProperties(prefix = "nsai.gateway.local")
public class LocalModeProperties {

    /** Turns local mode on. */
    private boolean enabled = false;

    /** Location of the YAML rule file, e.g. {@code classpath:nsai-rules.yaml} or {@code file:/config/rules.yaml}. */
    private String rules = "classpath:nsai-rules.yaml";

    private final Llm llm = new Llm();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRules() {
        return rules;
    }

    public void setRules(String rules) {
        this.rules = rules;
    }

    public Llm getLlm() {
        return llm;
    }

    public enum Provider { MOCK, OLLAMA }

    public static class Llm {

        /** Which neural layer to use: {@code mock} (default, no model needed) or {@code ollama}. */
        private Provider provider = Provider.MOCK;

        private final Ollama ollama = new Ollama();

        private final Mock mock = new Mock();

        public Provider getProvider() {
            return provider;
        }

        public void setProvider(Provider provider) {
            this.provider = provider;
        }

        public Ollama getOllama() {
            return ollama;
        }

        public Mock getMock() {
            return mock;
        }
    }

    public static class Ollama {

        private String baseUrl = "http://localhost:11434";

        private String model = "llama3.2";

        private Duration timeout = Duration.ofSeconds(120);

        /** Ask Ollama to return JSON, which makes field-level rules more reliable. */
        private boolean jsonOutput = true;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public boolean isJsonOutput() {
            return jsonOutput;
        }

        public void setJsonOutput(boolean jsonOutput) {
            this.jsonOutput = jsonOutput;
        }
    }

    public static class Mock {

        /** Canned responses; the first scenario whose keyword appears in the prompt wins. */
        private List<Scenario> scenarios = new ArrayList<>();

        /** Returned when no scenario keyword matches. */
        private String defaultResponse = "{}";

        public List<Scenario> getScenarios() {
            return scenarios;
        }

        public void setScenarios(List<Scenario> scenarios) {
            this.scenarios = scenarios;
        }

        public String getDefaultResponse() {
            return defaultResponse;
        }

        public void setDefaultResponse(String defaultResponse) {
            this.defaultResponse = defaultResponse;
        }
    }

    public static class Scenario {

        private String keyword;

        private String response;

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }

        public String getResponse() {
            return response;
        }

        public void setResponse(String response) {
            this.response = response;
        }
    }
}
