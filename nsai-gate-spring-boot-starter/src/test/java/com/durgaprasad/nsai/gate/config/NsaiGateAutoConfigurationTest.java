package com.durgaprasad.nsai.gate.config;

import com.durgaprasad.nsai.annotation.NSDeterministicGate;
import com.durgaprasad.nsai.gate.aspect.DeterministicGateAspect;
import com.durgaprasad.nsai.gate.exception.ComplianceViolationException;
import com.durgaprasad.nsai.graph.GraphRulesConfiguration;
import com.durgaprasad.nsai.graph.Neo4jRuleConnector;
import com.durgaprasad.nsai.local.LocalModeConfiguration;
import com.durgaprasad.nsai.local.LocalRuleConnector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** What an application gets just by adding the starter to its classpath. */
class NsaiGateAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, NsaiGateAutoConfiguration.class,
                    LocalModeConfiguration.class, GraphRulesConfiguration.class));

    /** Stand-in for an application's AI service. */
    static class OfferService {

        @NSDeterministicGate(intent = "LOAN_OFFER", symbolicSource = "LOCAL_RULES")
        public String draft(String modelOutput) {
            return modelOutput;
        }
    }

    @Test
    @DisplayName("The starter registers its auto-configurations with Spring Boot")
    void autoConfigurationsAreRegistered() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
                .contains(NsaiGateAutoConfiguration.class.getName(), LocalModeConfiguration.class.getName(),
                        GraphRulesConfiguration.class.getName());
    }

    @Test
    @DisplayName("By default: the gate is on, no rule source is registered")
    void gateOnWithoutRuleSources() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(DeterministicGateAspect.class);
            assertThat(ctx).hasSingleBean(EnforcementConfig.class);
            assertThat(ctx).doesNotHaveBean(LocalRuleConnector.class);
            assertThat(ctx).doesNotHaveBean(Neo4jRuleConnector.class);
        });
    }

    @Test
    void enforcementModeIsBoundFromProperties() {
        runner.withPropertyValues("nsai.gateway.enforcement.mode=HARD_REJECT")
                .run(ctx -> assertThat(ctx.getBean(EnforcementConfig.class).getMode())
                        .isEqualTo(EnforcementMode.HARD_REJECT));
    }

    @Test
    @DisplayName("Annotated methods in application beans are gated, with no component scanning of the library")
    void gatesApplicationBeans() {
        runner.withPropertyValues(
                        "nsai.gateway.local.enabled=true",
                        "nsai.gateway.local.rules=classpath:rules/minimal-rules.yaml",
                        "nsai.gateway.enforcement.mode=HARD_REJECT")
                .withBean(OfferService.class)
                .run(ctx -> {
                    OfferService service = ctx.getBean(OfferService.class);

                    assertThat(service.draft("{\"interestRate\":\"9%\"}")).contains("9%");
                    assertThatThrownBy(() -> service.draft("{\"interestRate\":\"25%\"}"))
                            .isInstanceOf(ComplianceViolationException.class)
                            .hasMessageContaining("max-interest-rate");
                });
    }

    @Test
    @DisplayName("With no rule source, a gated call fails closed instead of passing through")
    void gatedCallWithoutRuleSourceFailsClosed() {
        runner.withBean(OfferService.class)
                .run(ctx -> assertThatThrownBy(() -> ctx.getBean(OfferService.class).draft("anything"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("No SymbolicConnector registered for symbolicSource 'LOCAL_RULES'"));
    }

    @Test
    void graphModeRegistersTheNeo4jConnector() {
        runner.withPropertyValues("nsai.gateway.graph.enabled=true")
                .withBean(Driver.class, () -> mock(Driver.class))
                .run(ctx -> assertThat(ctx).hasSingleBean(Neo4jRuleConnector.class));
    }

    @Test
    void applicationCanReplaceTheAspect() {
        DeterministicGateAspect custom = new DeterministicGateAspect();
        runner.withBean(DeterministicGateAspect.class, () -> custom)
                .run(ctx -> assertThat(ctx.getBean(DeterministicGateAspect.class)).isSameAs(custom));
    }
}
