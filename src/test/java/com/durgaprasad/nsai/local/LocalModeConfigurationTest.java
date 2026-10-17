package com.durgaprasad.nsai.local;

import com.durgaprasad.nsai.llm.LlmClient;
import com.durgaprasad.nsai.llm.MockLlmClient;
import com.durgaprasad.nsai.llm.OllamaLlmClient;
import com.durgaprasad.nsai.local.rules.RuleDefinitionException;
import com.durgaprasad.nsai.local.rules.RuleSet;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LocalModeConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LocalModeConfiguration.class);

    @Test
    void inactiveUnlessEnabled() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(LocalRuleConnector.class));
    }

    @Test
    void mockProviderByDefault() {
        runner.withPropertyValues("nsai.gateway.local.enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(LocalRuleConnector.class);
                    assertThat(ctx.getBean(LlmClient.class)).isInstanceOf(MockLlmClient.class);
                });
    }

    @Test
    void ollamaProviderIsSelectable() {
        runner.withPropertyValues(
                        "nsai.gateway.local.enabled=true",
                        "nsai.gateway.local.llm.provider=ollama",
                        "nsai.gateway.local.llm.ollama.model=qwen2.5:3b")
                .run(ctx -> assertThat(ctx.getBean(LlmClient.class).name()).isEqualTo("ollama:qwen2.5:3b"));
    }

    @Test
    void userSuppliedLlmClientWins() {
        LlmClient custom = new MockLlmClient(null, "custom");
        runner.withPropertyValues("nsai.gateway.local.enabled=true")
                .withBean(LlmClient.class, () -> custom)
                .run(ctx -> assertThat(ctx.getBean(LlmClient.class)).isSameAs(custom));
    }

    @Test
    void missingRuleFileFailsStartup() {
        runner.withPropertyValues("nsai.gateway.local.enabled=true",
                        "nsai.gateway.local.rules=classpath:does-not-exist.yaml")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().isInstanceOf(RuleDefinitionException.class)
                        .hasMessageContaining("Rule file not found"));
    }

    @Test
    void ollamaClientType() {
        runner.withPropertyValues("nsai.gateway.local.enabled=true", "nsai.gateway.local.llm.provider=OLLAMA")
                .run(ctx -> assertThat(ctx.getBean(LlmClient.class)).isInstanceOf(OllamaLlmClient.class));
    }

    @Test
    void registersLocalRulesConnectorAndLoadsDefaultRules() {
        runner.withPropertyValues("nsai.gateway.local.enabled=true")
                .run(ctx -> {
                    assertThat(ctx.getBean(LocalRuleConnector.class).getSourceIdentifier()).isEqualTo("LOCAL_RULES");
                    assertThat(ctx.getBean(RuleSet.class).forIntent("LOAN_OFFER")).isPresent();
                });
    }

    @Test
    void malformedRuleFileFailsStartupWithLocation() {
        runner.withPropertyValues("nsai.gateway.local.enabled=true",
                        "nsai.gateway.local.rules=classpath:rules/typo-rules.yaml")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().isInstanceOf(RuleDefinitionException.class)
                        .hasMessageContaining("unknown key 'maxx'"));
    }
}
