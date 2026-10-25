package com.durgaprasad.nsai.graph;

import com.durgaprasad.nsai.local.rules.IntentRules;
import com.durgaprasad.nsai.local.rules.Rule;
import com.durgaprasad.nsai.local.rules.RuleDefinitionException;
import com.durgaprasad.nsai.local.rules.RuleSetLoader;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.types.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the rules for an intent from Neo4j, including the rules it inherits.
 *
 * <pre>
 * (:Intent {id:'LOAN_OFFER'})-[:INHERITS]->(:Intent {id:'CONSUMER_LENDING'})
 * (:Intent)-[:REQUIRES]->(:Rule {id, field, pattern, required, min, max, allowed, forbidden, message, repair})
 * </pre>
 *
 * Rule nodes use exactly the keys of the YAML rule file and are validated by the same code
 * ({@link RuleSetLoader#ruleFrom}), so a typo such as {@code maxx} is an error here too.
 * Inheritance is followed up to 5 levels. When the same rule id
 * appears at several levels, the definition nearest to the intent wins.
 * <p>
 * Loaded rules are cached per intent in a private Caffeine cache, so the library never
 * replaces the application's own {@code CacheManager}. Failures and unknown intents are not cached.
 */
public class GraphRuleLoader {

    static final int MAX_INHERITANCE_DEPTH = 5;

    static final String QUERY = """
            MATCH (i:Intent {id: $intent})
            OPTIONAL MATCH path = (i)-[:INHERITS*0..5]->(:Intent)-[:REQUIRES]->(r:Rule)
            RETURN i.description AS description, r, length(path) AS depth
            ORDER BY depth, r.id
            """;

    private static final Logger log = LoggerFactory.getLogger(GraphRuleLoader.class);

    private final Driver driver;
    private final Cache<String, IntentRules> cache;

    public GraphRuleLoader(Driver driver) {
        this(driver, Duration.ofMinutes(10));
    }

    public GraphRuleLoader(Driver driver, Duration cacheTtl) {
        this.driver = driver;
        this.cache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(cacheTtl)
                .recordStats()
                .build();
    }

    /**
     * Returns the intent's rules, or empty if the intent does not exist in the graph.
     *
     * @throws RuleDefinitionException if a rule node is invalid or the intent has no rules
     * @throws RuntimeException        if Neo4j cannot be reached or the query fails
     */
    public Optional<IntentRules> load(String intent) {
        return Optional.ofNullable(cache.get(intent, this::query));
    }

    /** Hit and miss counters of the rule cache. */
    public CacheStats cacheStats() {
        return cache.stats();
    }

    /** Drops all cached rules, so the next lookup reads the graph again. */
    public void clearCache() {
        cache.invalidateAll();
    }

    /** Reads the intent from Neo4j; {@code null} (not cached) when the intent does not exist. */
    private IntentRules query(String intent) {
        List<Record> rows;
        try (Session session = driver.session()) {
            rows = session.executeRead(tx -> tx.run(QUERY, Map.<String, Object>of("intent", intent)).list());
        }
        if (rows.isEmpty()) {
            return null;
        }
        String description = rows.get(0).get("description").isNull() ? "" : rows.get(0).get("description").asString();
        List<RuleNode> nodes = new ArrayList<>();
        for (Record row : rows) {
            if (!row.get("r").isNull()) {
                Node node = row.get("r").asNode();
                nodes.add(new RuleNode(node.elementId(), row.get("depth").asInt(), node.asMap()));
            }
        }
        IntentRules rules = assemble(intent, description, nodes);
        log.info("[Neo4jRules] Loaded {} rule(s) for intent={}: {}", rules.rules().size(), intent,
                rules.rules().stream().map(Rule::id).toList());
        return rules;
    }

    /** Builds the intent's rule list from the rule nodes found in the graph. */
    static IntentRules assemble(String intent, String description, List<RuleNode> nodes) {
        if (nodes.isEmpty()) {
            throw new RuleDefinitionException("Intent '" + intent
                    + "' exists in Neo4j but has no rules (no REQUIRES relationship within "
                    + MAX_INHERITANCE_DEPTH + " INHERITS levels)");
        }
        List<RuleNode> ordered = new ArrayList<>(nodes);
        ordered.sort(Comparator.comparingInt(RuleNode::depth));

        Map<String, RuleNode> chosen = new LinkedHashMap<>();
        List<Rule> rules = new ArrayList<>();
        for (RuleNode node : ordered) {
            String where = "Neo4j rule node " + node.elementId();
            Rule rule = RuleSetLoader.ruleFrom(node.properties(), where);
            RuleNode existing = chosen.get(rule.id());
            if (existing == null) {
                chosen.put(rule.id(), node);
                rules.add(rule);
            } else if (existing.elementId().equals(node.elementId())) {
                // the same rule node reached through two inheritance paths
                continue;
            } else if (existing.depth() == node.depth()) {
                throw new RuleDefinitionException(where + ": rule id '" + rule.id()
                        + "' is defined twice at the same level for intent " + intent);
            }
            // otherwise a nearer definition already overrides this inherited one
        }
        return new IntentRules(intent, description, rules);
    }

    /** A rule node as returned by the query: its id, its distance from the intent and its properties. */
    record RuleNode(String elementId, int depth, Map<String, Object> properties) {
    }
}
