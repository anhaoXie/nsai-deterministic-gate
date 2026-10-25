package com.durgaprasad.nsai.local.rules;

import com.durgaprasad.nsai.core.ValidationResult;
import com.durgaprasad.nsai.core.Violation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;

/**
 * Evaluates a neural proposal against a {@link RuleSet}. Pure and deterministic:
 * the same proposal and rules always produce the same {@link ValidationResult}.
 * <p>
 * Field values are read from JSON when the proposal is (or contains) a JSON object,
 * otherwise from free text using each rule's {@code pattern}. Every occurrence is
 * checked, so a proposal cannot hide a violating value behind a compliant one.
 */
public final class RuleEvaluator {

    private final RuleSet ruleSet;
    private final ObjectMapper mapper;

    public RuleEvaluator(RuleSet ruleSet) {
        this(ruleSet, new ObjectMapper());
    }

    public RuleEvaluator(RuleSet ruleSet, ObjectMapper mapper) {
        this.ruleSet = ruleSet;
        this.mapper = mapper;
    }

    public RuleSet ruleSet() {
        return ruleSet;
    }

    public ValidationResult evaluate(String intent, String proposal) {
        Optional<IntentRules> intentRules = ruleSet.forIntent(intent);
        if (intentRules.isEmpty()) {
            return ruleSet.unknownIntentPolicy() == UnknownIntentPolicy.ALLOW
                    ? ValidationResult.pass()
                    : ValidationResult.fail("unknown-intent",
                            "No rules are defined for intent '" + intent + "'; denied by default");
        }
        String text = proposal == null ? "" : proposal;
        JsonNode json = parseJsonObject(text);

        List<Violation> violations = new ArrayList<>();
        for (Rule rule : intentRules.get().rules()) {
            check(rule, text, json, violations);
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    private void check(Rule rule, String text, JsonNode json, List<Violation> out) {
        if (rule.forbidden() != null) {
            Matcher forbidden = rule.forbidden().matcher(text);
            if (forbidden.find()) {
                out.add(violation(rule, "proposal contains forbidden text '" + forbidden.group() + "'"));
            }
        }
        if (!rule.hasFieldChecks()) {
            return;
        }
        List<String> values = extract(rule, text, json);
        if (values.isEmpty()) {
            if (rule.required()) {
                out.add(violation(rule, "required field '" + rule.field() + "' is missing"));
            }
            return;
        }
        for (String value : values) {
            if (!rule.allowed().isEmpty() && !rule.allowed().contains(value)) {
                out.add(violation(rule, rule.field() + " = '" + value + "' is not one of " + rule.allowed()));
            }
            if (rule.min() == null && rule.max() == null) {
                continue;
            }
            Double number = toNumber(value);
            if (number == null) {
                out.add(violation(rule, rule.field() + " = '" + value + "' is not a number"));
                continue;
            }
            if (rule.min() != null && number < rule.min()) {
                out.add(violation(rule, rule.field() + " = " + format(number) + " is below the minimum " + format(rule.min())));
            }
            if (rule.max() != null && number > rule.max()) {
                out.add(violation(rule, rule.field() + " = " + format(number) + " exceeds the maximum " + format(rule.max())));
            }
        }
    }

    private List<String> extract(Rule rule, String text, JsonNode json) {
        List<String> values = new ArrayList<>();
        if (json != null) {
            JsonNode node = lookup(json, rule.field());
            if (node != null && !node.isNull() && !node.isMissingNode()) {
                if (node.isArray()) {
                    node.forEach(element -> addScalar(element, values));
                } else {
                    addScalar(node, values);
                }
                return values;
            }
        }
        if (rule.pattern() != null) {
            Matcher matcher = rule.pattern().matcher(text);
            while (matcher.find()) {
                String value = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
                if (value != null) {
                    values.add(value.trim());
                }
            }
        }
        return values;
    }

    private static void addScalar(JsonNode node, List<String> values) {
        if (node.isValueNode() && !node.isNull()) {
            values.add(node.asText().trim());
        } else if (!node.isNull()) {
            values.add(node.toString());
        }
    }

    private static JsonNode lookup(JsonNode root, String dottedPath) {
        JsonNode current = root;
        for (String part : dottedPath.split("\\.")) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(part);
        }
        return current;
    }

    /**
     * Parses the proposal as a JSON object. Tolerates Markdown code fences and prose
     * around the object, which LLMs often add.
     */
    private JsonNode parseJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(text.substring(start, end + 1));
            return node != null && node.isObject() ? node : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    static Double toNumber(String raw) {
        String cleaned = raw.replace("%", "").replace("$", "").replace(",", "").replace("_", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(cleaned);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static Violation violation(Rule rule, String detail) {
        return new Violation(rule.id(), rule.message() == null ? detail : rule.message() + " (" + detail + ")");
    }
}
