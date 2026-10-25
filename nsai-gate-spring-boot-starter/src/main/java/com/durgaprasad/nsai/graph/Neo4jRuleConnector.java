package com.durgaprasad.nsai.graph;

import com.durgaprasad.nsai.core.RepairResult;
import com.durgaprasad.nsai.core.SymbolicConnector;
import com.durgaprasad.nsai.core.ValidationResult;
import com.durgaprasad.nsai.local.rules.IntentRules;
import com.durgaprasad.nsai.local.rules.RuleDefinitionException;
import com.durgaprasad.nsai.local.rules.RuleEvaluator;
import com.durgaprasad.nsai.local.rules.RuleRepairer;
import com.durgaprasad.nsai.local.rules.RuleSet;
import com.durgaprasad.nsai.local.rules.UnknownIntentPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Symbolic connector backed by rules stored in Neo4j. Uses the same rule language,
 * evaluation and repair as the YAML connector; only the source of the rules differs.
 * <p>
 * Fails closed: if the rules cannot be loaded (Neo4j unreachable, invalid rule node,
 * unknown intent), the proposal is blocked with a violation that says why.
 * <p>
 * Use it with {@code @NSDeterministicGate(symbolicSource = "NEO4J_RULES")}.
 */
public class Neo4jRuleConnector implements SymbolicConnector {

    public static final String SOURCE_ID = "NEO4J_RULES";

    private static final Logger log = LoggerFactory.getLogger(Neo4jRuleConnector.class);

    private final GraphRuleLoader loader;

    public Neo4jRuleConnector(GraphRuleLoader loader) {
        this.loader = loader;
    }

    @Override
    public boolean validate(String intent, String neuralProposal, Map<String, Object> context) {
        return evaluate(intent, neuralProposal, context).valid();
    }

    @Override
    public ValidationResult evaluate(String intent, String neuralProposal, Map<String, Object> context) {
        Loaded loaded = load(intent);
        if (loaded.failure() != null) {
            return loaded.failure();
        }
        return loaded.evaluator().evaluate(intent, neuralProposal);
    }

    @Override
    public Optional<RepairResult> repair(String intent, String neuralProposal, ValidationResult failed,
                                         Map<String, Object> context) {
        Loaded loaded = load(intent);
        if (loaded.failure() != null) {
            return Optional.empty();
        }
        return new RuleRepairer(loaded.evaluator()).repair(intent, neuralProposal, failed);
    }

    @Override
    public String getSourceIdentifier() {
        return SOURCE_ID;
    }

    private Loaded load(String intent) {
        try {
            Optional<IntentRules> rules = loader.load(intent);
            Map<String, IntentRules> byIntent = rules.map(r -> Map.of(intent, r)).orElse(Map.of());
            return new Loaded(new RuleEvaluator(new RuleSet(byIntent, UnknownIntentPolicy.DENY)), null);
        } catch (RuleDefinitionException e) {
            log.error("[Neo4jRules] Invalid rule definition for intent={}: {}", intent, e.getMessage());
            return new Loaded(null, ValidationResult.fail("invalid-rule-definition",
                    "Rules for intent '" + intent + "' in Neo4j are invalid; denied by default (" + e.getMessage() + ")"));
        } catch (RuntimeException e) {
            log.error("[Neo4jRules] Could not load rules for intent={} from Neo4j", intent, e);
            return new Loaded(null, ValidationResult.fail("rule-source-unavailable",
                    "Rules for intent '" + intent + "' could not be loaded from Neo4j; denied by default"));
        }
    }

    private record Loaded(RuleEvaluator evaluator, ValidationResult failure) {
    }
}
