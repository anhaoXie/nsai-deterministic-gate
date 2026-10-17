package com.durgaprasad.nsai.local.rules;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Loads a {@link RuleSet} from YAML.
 *
 * <pre>
 * unknownIntent: deny            # deny (default) or allow
 * intents:
 *   LOAN_OFFER:
 *     description: Consumer loan offers
 *     rules:
 *       - id: max-interest-rate
 *         field: interestRate
 *         pattern: '(?i)interest rate (?:is|of)\s*(\d+(?:\.\d+)?)\s*%'
 *         max: 10
 *         repair: clamp
 *       - id: no-guaranteed-approval
 *         forbidden: '(?i)guaranteed approval'
 * </pre>
 *
 * Uses SnakeYAML's {@link SafeConstructor}, so a rule file cannot instantiate arbitrary
 * classes. Unknown keys are rejected to catch typos such as {@code maxx: 10}.
 */
public final class RuleSetLoader {

    private static final Set<String> RULE_KEYS =
            Set.of("id", "field", "pattern", "required", "min", "max", "allowed", "forbidden", "message", "repair");
    private static final Set<String> INTENT_KEYS = Set.of("description", "rules");
    private static final Set<String> ROOT_KEYS = Set.of("unknownIntent", "intents");

    private RuleSetLoader() {
    }

    public static RuleSet load(InputStream input, String sourceName) {
        Object root;
        try (InputStream in = input) {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
        } catch (IOException | RuntimeException e) {
            throw new RuleDefinitionException("Cannot read rule file " + sourceName + ": " + e.getMessage(), e);
        }
        if (root == null) {
            throw new RuleDefinitionException("Rule file " + sourceName + " is empty");
        }
        Map<String, Object> rootMap = asMap(root, sourceName);
        checkKeys(rootMap, ROOT_KEYS, sourceName);

        UnknownIntentPolicy policy = parsePolicy(rootMap.get("unknownIntent"), sourceName);

        Object intentsNode = rootMap.get("intents");
        if (intentsNode == null) {
            throw new RuleDefinitionException(sourceName + ": 'intents' is required");
        }
        Map<String, IntentRules> intents = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : asMap(intentsNode, sourceName + ".intents").entrySet()) {
            String intent = entry.getKey();
            intents.put(intent, parseIntent(intent, entry.getValue(), sourceName + ".intents." + intent));
        }
        return new RuleSet(intents, policy);
    }

    /**
     * Validates one rule given as a map of rule keys (for example the properties of a
     * Neo4j rule node), with the same checks as a rule in the YAML file.
     *
     * @param where where the rule came from, used in error messages
     * @throws RuleDefinitionException if a key is unknown or a value is invalid
     */
    public static Rule ruleFrom(Map<String, ?> properties, String where) {
        return parseRule(new LinkedHashMap<String, Object>(properties), where);
    }

    private static UnknownIntentPolicy parsePolicy(Object value, String where) {
        if (value == null) {
            return UnknownIntentPolicy.DENY;
        }
        try {
            return UnknownIntentPolicy.valueOf(value.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RuleDefinitionException(where + ": unknownIntent must be 'deny' or 'allow', got '" + value + "'");
        }
    }

    private static IntentRules parseIntent(String intent, Object node, String where) {
        Map<String, Object> map = node == null ? Map.of() : asMap(node, where);
        checkKeys(map, INTENT_KEYS, where);
        Object rulesNode = map.get("rules");
        if (!(rulesNode instanceof List<?> ruleList) || ruleList.isEmpty()) {
            throw new RuleDefinitionException(where + ": 'rules' must be a non-empty list");
        }
        List<Rule> rules = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < ruleList.size(); i++) {
            Rule rule = parseRule(ruleList.get(i), where + ".rules[" + i + "]");
            if (!ids.add(rule.id())) {
                throw new RuleDefinitionException(where + ": duplicate rule id '" + rule.id() + "'");
            }
            rules.add(rule);
        }
        Object description = map.get("description");
        return new IntentRules(intent, description == null ? "" : description.toString(), rules);
    }

    private static Rule parseRule(Object node, String where) {
        Map<String, Object> map = asMap(node, where);
        checkKeys(map, RULE_KEYS, where);

        String id = string(map.get("id"));
        if (id == null || id.isBlank()) {
            throw new RuleDefinitionException(where + ": 'id' is required");
        }
        where = where + " (" + id + ")";

        String field = string(map.get("field"));
        Pattern pattern = regex(map.get("pattern"), where + ".pattern");
        boolean required = bool(map.get("required"), where + ".required");
        Double min = number(map.get("min"), where + ".min");
        Double max = number(map.get("max"), where + ".max");
        List<String> allowed = stringList(map.get("allowed"), where + ".allowed");
        Pattern forbidden = regex(map.get("forbidden"), where + ".forbidden");
        String message = string(map.get("message"));
        RepairStrategy repair = parseRepair(map.get("repair"), where + ".repair");

        Rule rule = new Rule(id, field, pattern, required, min, max, allowed, forbidden, message, repair);

        if (!rule.hasFieldChecks() && forbidden == null) {
            throw new RuleDefinitionException(where
                    + ": a rule needs at least one of required, min, max, allowed or forbidden");
        }
        if ((rule.hasFieldChecks() || pattern != null) && (field == null || field.isBlank())) {
            throw new RuleDefinitionException(where + ": 'field' is required for required/min/max/allowed/pattern");
        }
        if (repair == RepairStrategy.CLAMP && min == null && max == null) {
            throw new RuleDefinitionException(where + ": repair: clamp needs a min or max to clamp to");
        }
        if (min != null && max != null && min > max) {
            throw new RuleDefinitionException(where + ": min (" + min + ") is greater than max (" + max + ")");
        }
        return rule;
    }

    private static RepairStrategy parseRepair(Object value, String where) {
        if (value == null) {
            return RepairStrategy.NONE;
        }
        try {
            return RepairStrategy.valueOf(value.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RuleDefinitionException(where + ": repair must be 'none' or 'clamp', got '" + value + "'");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object node, String where) {
        if (!(node instanceof Map<?, ?> map)) {
            throw new RuleDefinitionException(where + ": expected a mapping");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    private static void checkKeys(Map<String, Object> map, Set<String> allowed, String where) {
        for (String key : map.keySet()) {
            if (!allowed.contains(key)) {
                throw new RuleDefinitionException(where + ": unknown key '" + key + "' (allowed: "
                        + String.join(", ", allowed.stream().sorted().toList()) + ")");
            }
        }
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean bool(Object value, String where) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        throw new RuleDefinitionException(where + ": expected true or false, got '" + value + "'");
    }

    private static Double number(Object value, String where) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw new RuleDefinitionException(where + ": expected a number, got '" + value + "'");
    }

    private static List<String> stringList(Object value, String where) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new RuleDefinitionException(where + ": expected a non-empty list");
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static Pattern regex(Object value, String where) {
        if (value == null) {
            return null;
        }
        try {
            return Pattern.compile(value.toString());
        } catch (PatternSyntaxException e) {
            throw new RuleDefinitionException(where + ": invalid regex: " + e.getDescription(), e);
        }
    }
}
