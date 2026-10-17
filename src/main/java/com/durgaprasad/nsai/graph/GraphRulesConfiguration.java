package com.durgaprasad.nsai.graph;

import org.neo4j.driver.Driver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@code NEO4J_RULES} connector when {@code nsai.gateway.graph.enabled=true}.
 * The Neo4j {@link Driver} comes from Spring Boot ({@code spring.neo4j.uri} and
 * {@code spring.neo4j.authentication.*}). Off by default, so local mode needs no database.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "nsai.gateway.graph", name = "enabled", havingValue = "true")
public class GraphRulesConfiguration {

    @Bean
    public GraphRuleLoader graphRuleLoader(Driver driver) {
        return new GraphRuleLoader(driver);
    }

    @Bean
    public Neo4jRuleConnector neo4jRuleConnector(GraphRuleLoader graphRuleLoader) {
        return new Neo4jRuleConnector(graphRuleLoader);
    }
}
