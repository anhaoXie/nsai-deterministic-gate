package com.durgaprasad.nsai.local;

import com.durgaprasad.nsai.llm.LlmClient;
import com.durgaprasad.nsai.llm.MockLlmClient;
import com.durgaprasad.nsai.llm.OllamaLlmClient;
import com.durgaprasad.nsai.local.rules.RuleDefinitionException;
import com.durgaprasad.nsai.local.rules.RuleEvaluator;
import com.durgaprasad.nsai.local.rules.RuleSet;
import com.durgaprasad.nsai.local.rules.RuleSetLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wires local mode when {@code nsai.gateway.local.enabled=true}: loads the YAML rules,
 * registers the {@code LOCAL_RULES} connector and picks the LLM client (mock or Ollama).
 * A missing or malformed rule file stops the application from starting (fail fast).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "nsai.gateway.local", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LocalModeProperties.class)
public class LocalModeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LocalModeConfiguration.class);

    @Bean
    public RuleSet nsaiLocalRuleSet(LocalModeProperties properties, ResourceLoader resourceLoader) {
        String location = properties.getRules();
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new RuleDefinitionException("Rule file not found: " + location);
        }
        try {
            RuleSet ruleSet = RuleSetLoader.load(resource.getInputStream(), location);
            log.info("[LocalRules] Loaded {} intent(s) from {}: {}", ruleSet.intents().size(), location,
                    ruleSet.intents().keySet());
            return ruleSet;
        } catch (IOException e) {
            throw new RuleDefinitionException("Cannot open rule file " + location, e);
        }
    }

    @Bean
    public LocalRuleConnector localRuleConnector(RuleSet nsaiLocalRuleSet) {
        return new LocalRuleConnector(new RuleEvaluator(nsaiLocalRuleSet));
    }

    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    public LlmClient nsaiLlmClient(LocalModeProperties properties) {
        LocalModeProperties.Llm llm = properties.getLlm();
        return switch (llm.getProvider()) {
            case OLLAMA -> new OllamaLlmClient(llm.getOllama().getBaseUrl(), llm.getOllama().getModel(),
                    llm.getOllama().getTimeout(), llm.getOllama().isJsonOutput());
            case MOCK -> {
                Map<String, String> responses = new LinkedHashMap<>();
                llm.getMock().getScenarios().forEach(s -> responses.put(s.getKeyword(), s.getResponse()));
                yield new MockLlmClient(responses, llm.getMock().getDefaultResponse());
            }
        };
    }
}
