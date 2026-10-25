package com.durgaprasad.nsai.gate.config;

import com.durgaprasad.nsai.gate.aspect.DeterministicGateAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Turns the gate on in any Spring Boot application that has this library on its classpath:
 * every method annotated with {@code @NSDeterministicGate} is checked against the rule source
 * it names. Rule sources come from {@code LocalModeConfiguration} (YAML) and
 * {@code GraphRulesConfiguration} (Neo4j), or from your own {@code SymbolicConnector} beans.
 * <p>
 * Settings: {@code nsai.gateway.enforcement.mode} = {@code HARD_REJECT} or {@code SOFT_REPAIR}.
 */
@AutoConfiguration
@EnableConfigurationProperties(EnforcementConfig.class)
public class NsaiGateAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DeterministicGateAspect deterministicGateAspect() {
        return new DeterministicGateAspect();
    }
}
