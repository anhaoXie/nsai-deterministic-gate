package com.durgaprasad.nsai.graph;

import com.durgaprasad.nsai.core.ValidationResult;
import com.durgaprasad.nsai.core.Violation;
import com.durgaprasad.nsai.demo.LoanOfferAssistant;
import com.durgaprasad.nsai.gate.exception.ComplianceViolationException;
import com.durgaprasad.nsai.local.rules.Rule;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Graph mode end to end: a real Neo4j 5 in Docker, seeded with graph-seed.cypher, the real
 * Spring context and AOP proxy, and the mock LLM. Skipped when Docker is not available.
 */
@SpringBootTest(properties = {
        "nsai.demo.run-on-startup=false",
        "nsai.gateway.graph.enabled=true",
        "nsai.gateway.enforcement.mode=HARD_REJECT",
        "nsai.demo.symbolic-source=NEO4J_RULES"
})
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class GraphRulesIntegrationTest {

    @Container
    @ServiceConnection
    static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>("neo4j:5.26-community")
            .withAdminPassword("nsai-test-password");

    @Autowired
    private Driver driver;

    @Autowired
    private Neo4jRuleConnector connector;

    @Autowired
    private GraphRuleLoader loader;

    @Autowired
    private LoanOfferAssistant assistant;


    @BeforeEach
    void resetGraph() throws IOException {
        run("MATCH (n) DETACH DELETE n");
        String seed = new ClassPathResource("graph-seed.cypher").getContentAsString(StandardCharsets.UTF_8);
        String withoutComments = seed.lines()
                .filter(line -> !line.trim().startsWith("//"))
                .collect(Collectors.joining("\n"));
        Arrays.stream(withoutComments.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .forEach(this::run);
        loader.clearCache();
    }

    private void run(String cypher) {
        try (Session session = driver.session()) {
            session.run(cypher).consume();
        }
    }

    private static List<String> ruleIds(ValidationResult result) {
        return result.violations().stream().map(Violation::ruleId).toList();
    }

    @Test
    @DisplayName("LOAN_OFFER gets its own rules plus those inherited from two levels up, nearest first")
    void loadsInheritedRules() {
        assertThat(loader.load("LOAN_OFFER").orElseThrow().rules()).extracting(Rule::id).containsExactly(
                "client-id-required", "term-range",            // LOAN_OFFER
                "max-interest-rate", "supported-currency",     // CONSUMER_LENDING
                "no-guaranteed-approval");                     // ALL_CUSTOMER_COMMS
    }

    @Test
    @DisplayName("Through the gate, the graph gives the same verdicts as the YAML rules")
    void sameVerdictsAsYamlThroughTheGate() {
        assertThat(assistant.draftOffer("premium")).contains("CUST-1001");

        assertThatThrownBy(() -> assistant.draftOffer("Subprime customer wants a fast loan"))
                .isInstanceOfSatisfying(ComplianceViolationException.class, e ->
                        assertThat(e.getViolations()).extracting(Violation::ruleId).containsExactly("max-interest-rate"));

        assertThatThrownBy(() -> assistant.draftOffer("Please guarantee approval"))
                .isInstanceOfSatisfying(ComplianceViolationException.class, e ->
                        assertThat(e.getViolations()).extracting(Violation::ruleId).containsExactlyInAnyOrder(
                                "client-id-required", "term-range", "no-guaranteed-approval"));
    }

    @Test
    @DisplayName("A rule on the intent itself overrides the inherited rule with the same id")
    void intentRuleOverridesInheritedRule() {
        run("""
                MATCH (i:Intent {id: 'LOAN_OFFER'})
                CREATE (i)-[:REQUIRES]->(:Rule {id: 'max-interest-rate', field: 'interestRate', max: 12.0})
                """);

        ValidationResult result = connector.evaluate("LOAN_OFFER",
                "{\"clientId\":\"C-1\",\"interestRate\":\"15%\",\"termMonths\":12,\"currency\":\"USD\"}", Map.of());

        assertThat(ruleIds(result)).containsExactly("max-interest-rate");
        assertThat(result.violations().get(0).message()).contains("exceeds the maximum 12");
    }

    @Test
    void unknownIntentIsDenied() {
        assertThat(ruleIds(connector.evaluate("WIRE_TRANSFER", "{}", Map.of()))).containsExactly("unknown-intent");
    }

    @Test
    @DisplayName("A typo in a rule node blocks every proposal for that intent")
    void typoInRuleNodeBlocks() {
        run("""
                MATCH (i:Intent {id: 'LOAN_OFFER'})
                CREATE (i)-[:REQUIRES]->(:Rule {id: 'typo', field: 'termMonths', maxx: 60})
                """);

        ValidationResult result = connector.evaluate("LOAN_OFFER",
                "{\"clientId\":\"C-1\",\"interestRate\":\"6%\",\"termMonths\":12,\"currency\":\"USD\"}", Map.of());

        assertThat(ruleIds(result)).containsExactly("invalid-rule-definition");
        assertThat(result.violations().get(0).message()).contains("unknown key 'maxx'");
    }

    @Test
    void intentWithoutRulesIsBlocked() {
        run("CREATE (:Intent {id: 'EMPTY_INTENT'})");

        assertThat(ruleIds(connector.evaluate("EMPTY_INTENT", "{}", Map.of()))).containsExactly("invalid-rule-definition");
    }

    @Test
    @DisplayName("SOFT_REPAIR works with graph rules: 25% is clamped to the inherited 18% cap")
    void repairUsesGraphRules() {
        String offer = "{\"clientId\":\"C-1\",\"interestRate\":\"25%\",\"termMonths\":12,\"currency\":\"USD\"}";
        ValidationResult failed = connector.evaluate("LOAN_OFFER", offer, Map.of());

        assertThat(connector.repair("LOAN_OFFER", offer, failed, Map.of()).orElseThrow().repairedProposal())
                .contains("\"interestRate\":\"18%\"");
    }

    @Test
    @DisplayName("Rules are cached per intent: the second lookup does not query Neo4j")
    void rulesAreCached() {
        CacheStats before = loader.cacheStats();

        loader.load("LOAN_OFFER");
        loader.load("LOAN_OFFER");

        CacheStats delta = loader.cacheStats().minus(before);
        assertThat(delta.missCount()).isEqualTo(1);
        assertThat(delta.hitCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("A rule change in the graph is visible once the cache entry is gone")
    void ruleChangesAreSeenAfterEviction() {
        String offer = "{\"clientId\":\"C-1\",\"interestRate\":\"15%\",\"termMonths\":12,\"currency\":\"USD\"}";
        assertThat(connector.validate("LOAN_OFFER", offer, Map.of())).isTrue();

        run("MATCH (r:Rule {id: 'max-interest-rate'}) SET r.max = 12.0");
        assertThat(connector.validate("LOAN_OFFER", offer, Map.of())).as("still cached").isTrue();

        loader.clearCache();
        assertThat(connector.validate("LOAN_OFFER", offer, Map.of())).isFalse();
    }
}
