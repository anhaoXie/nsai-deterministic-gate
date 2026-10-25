package com.durgaprasad.nsai.graph;

import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * Registers the {@code NEO4J_RULES} connector when {@code nsai.gateway.graph.enabled=true}
 * and the Neo4j Java driver is on the classpath. The {@link Driver} comes from Spring Boot
 * ({@code spring.neo4j.uri} and {@code spring.neo4j.authentication.*}).
 * <p>
 * Rules are cached per intent for {@code nsai.gateway.graph.cache-ttl-seconds} (default 600).
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration")
@ConditionalOnClass(name = "org.neo4j.driver.Driver")
@ConditionalOnProperty(prefix = "nsai.gateway.graph", name = "enabled", havingValue = "true")
public class GraphRulesConfiguration {

    @Bean
    public GraphRuleLoader graphRuleLoader(Driver driver,
                                           @Value("${nsai.gateway.graph.cache-ttl-seconds:600}") long cacheTtlSeconds) {
        return new GraphRuleLoader(driver, Duration.ofSeconds(cacheTtlSeconds));
    }

    @Bean
    public Neo4jRuleConnector neo4jRuleConnector(GraphRuleLoader graphRuleLoader) {
        return new Neo4jRuleConnector(graphRuleLoader);
    }
}
