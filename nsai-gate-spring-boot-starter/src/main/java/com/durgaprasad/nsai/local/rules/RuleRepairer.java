package com.durgaprasad.nsai.local.rules;

import com.durgaprasad.nsai.core.RepairChange;
import com.durgaprasad.nsai.core.RepairResult;
import com.durgaprasad.nsai.core.ValidationResult;
import com.durgaprasad.nsai.core.Violation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * Deterministic repair for proposals that failed validation. No model is involved.
 * <ol>
 *   <li>A proposal is repaired only if <b>every</b> failed rule declares {@code repair: clamp}.</li>
 *   <li>Each out-of-range number is moved to the nearest bound (25% with max 18 becomes 18%),
 *       in the JSON field when the proposal is JSON, otherwise at the exact spot the rule's
 *       pattern matched in the free text.</li>
 *   <li>The repaired proposal is validated again against <b>all</b> rules; if it still fails
 *       for any reason, no repair is returned and the proposal is rejected.</li>
 * </ol>
 * Only the checked field is changed. Other text that repeats the value (for example a
 * free-text message inside the JSON) is not rewritten, so structured LLM output is recommended.
 */
public final class RuleRepairer {

    private final RuleEvaluator evaluator;
    private final ObjectMapper mapper;

    public RuleRepairer(RuleEvaluator evaluator) {
        this(evaluator, new ObjectMapper());
    }

    public RuleRepairer(RuleEvaluator evaluator, ObjectMapper mapper) {
        this.evaluator = evaluator;
        this.mapper = mapper;
    }

    public Optional<RepairResult> repair(String intent, String proposal, ValidationResult failed) {
        if (proposal == null || failed == null || failed.valid()) {
            return Optional.empty();
        }
        Optional<IntentRules> intentRules = evaluator.ruleSet().forIntent(intent);
        if (intentRules.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Rule> rulesById = intentRules.get().rules().stream()
                .collect(Collectors.toMap(Rule::id, Function.identity()));

        List<Rule> toRepair = new ArrayList<>();
        for (Violation violation : failed.violations()) {
            Rule rule = rulesById.get(violation.ruleId());
            if (rule == null || rule.repair() != RepairStrategy.CLAMP) {
                return Optional.empty(); // at least one violation cannot be repaired safely
            }
            if (!toRepair.contains(rule)) {
                toRepair.add(rule);
            }
        }

        List<RepairChange> changes = new ArrayList<>();
        String repaired = proposal;
        for (Rule rule : toRepair) {
            Optional<String> next = clamp(rule, repaired, changes);
            if (next.isEmpty()) {
                return Optional.empty();
            }
            repaired = next.get();
        }
        if (changes.isEmpty()) {
            return Optional.empty();
        }
        // Never trust a repair blindly: the result must pass every rule.
        if (!evaluator.evaluate(intent, repaired).valid()) {
            return Optional.empty();
        }
        return Optional.of(new RepairResult(repaired, changes));
    }

    private Optional<String> clamp(Rule rule, String text, List<RepairChange> changes) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                JsonNode root = mapper.readTree(text.substring(start, end + 1));
                if (root != null && root.isObject()) {
                    ObjectNode parent = parentOf((ObjectNode) root, rule.field());
                    String leaf = leafName(rule.field());
                    if (parent != null && parent.has(leaf) && !parent.get(leaf).isNull()) {
                        if (!clampJson(rule, parent, leaf, changes)) {
                            return Optional.empty();
                        }
                        return Optional.of(text.substring(0, start)
                                + mapper.writeValueAsString(root) + text.substring(end + 1));
                    }
                }
            } catch (JsonProcessingException e) {
                // not JSON after all: fall through to the free-text pattern
            }
        }
        return clampText(rule, text, changes);
    }

    private boolean clampJson(Rule rule, ObjectNode parent, String leaf, List<RepairChange> changes) {
        JsonNode node = parent.get(leaf);
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                JsonNode repaired = clampNode(rule, array.get(i), changes);
                if (repaired == null) {
                    return false;
                }
                array.set(i, repaired);
            }
            return true;
        }
        JsonNode repaired = clampNode(rule, node, changes);
        if (repaired == null) {
            return false;
        }
        parent.set(leaf, repaired);
        return true;
    }

    /** Returns the clamped node, the same node if in range, or {@code null} if not a number. */
    private JsonNode clampNode(Rule rule, JsonNode node, List<RepairChange> changes) {
        String raw = node.asText().trim();
        Double value = RuleEvaluator.toNumber(raw);
        if (value == null) {
            return null;
        }
        double clamped = bounded(rule, value);
        if (clamped == value) {
            return node;
        }
        String formatted = format(clamped);
        String to = node.isNumber() ? formatted : raw.endsWith("%") ? formatted + "%" : formatted;
        changes.add(new RepairChange(rule.id(), rule.field(), raw, to));
        if (node.isNumber()) {
            return clamped == Math.rint(clamped)
                    ? mapper.getNodeFactory().numberNode((long) clamped)
                    : mapper.getNodeFactory().numberNode(clamped);
        }
        return mapper.getNodeFactory().textNode(to);
    }

    private Optional<String> clampText(Rule rule, String text, List<RepairChange> changes) {
        if (rule.pattern() == null) {
            return Optional.empty();
        }
        Matcher matcher = rule.pattern().matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        int group = matcher.groupCount() >= 1 ? 1 : 0;
        while (matcher.find()) {
            String raw = matcher.group(group);
            if (raw == null) {
                continue;
            }
            Double value = RuleEvaluator.toNumber(raw);
            if (value == null) {
                return Optional.empty();
            }
            double clamped = bounded(rule, value);
            if (clamped != value) {
                String to = format(clamped);
                out.append(text, last, matcher.start(group)).append(to);
                last = matcher.end(group);
                changes.add(new RepairChange(rule.id(), rule.field(), raw.trim(), to));
            }
        }
        out.append(text.substring(last));
        return Optional.of(out.toString());
    }

    private static double bounded(Rule rule, double value) {
        double result = value;
        if (rule.max() != null && result > rule.max()) {
            result = rule.max();
        }
        if (rule.min() != null && result < rule.min()) {
            result = rule.min();
        }
        return result;
    }

    private static ObjectNode parentOf(ObjectNode root, String dottedPath) {
        String[] parts = dottedPath.split("\\.");
        JsonNode current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            current = current.get(parts[i]);
            if (current == null || !current.isObject()) {
                return null;
            }
        }
        return (ObjectNode) current;
    }

    private static String leafName(String dottedPath) {
        int dot = dottedPath.lastIndexOf('.');
        return dot < 0 ? dottedPath : dottedPath.substring(dot + 1);
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
