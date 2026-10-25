package com.durgaprasad.nsai.core;

import java.util.Map;
import java.util.Optional;

/**
 * A source of deterministic rules that the gate checks LLM output against.
 * <p>
 * Built-in sources: {@code LOCAL_RULES} (YAML file) and {@code NEO4J_RULES} (Neo4j graph).
 * To add your own, implement this interface, register it as a Spring bean and name its
 * {@link #getSourceIdentifier() identifier} in {@code @NSDeterministicGate(symbolicSource = ...)}.
 *
 * @author Durga Prasad Dasepalli
 */
public interface SymbolicConnector {

    /**
     * Validates a neural proposal against the symbolic layer.
     *
     * @param intent The business intent being validated.
     * @param neuralProposal The raw output from the LLM.
     * @param context Additional metadata for validation (e.g., user ID, region).
     * @return boolean True if the proposal meets deterministic constraints.
     */
    boolean validate(String intent, String neuralProposal, Map<String, Object> context);

    /**
     * Validates a neural proposal and explains which rules failed.
     * <p>
     * The default implementation delegates to {@link #validate} and reports one generic
     * violation, so existing connectors keep working unchanged. Connectors that know
     * which rule failed should override it.
     */
    default ValidationResult evaluate(String intent, String neuralProposal, Map<String, Object> context) {
        return validate(intent, neuralProposal, context)
                ? ValidationResult.pass()
                : ValidationResult.fail(getSourceIdentifier(),
                        "Proposal violated deterministic constraints for intent " + intent);
    }

    /**
     * Attempts a deterministic repair of a proposal that failed {@link #evaluate}.
     * Used only in SOFT_REPAIR mode. Implementations must return a result only when
     * the repaired proposal passes every rule; otherwise {@link Optional#empty()}.
     * <p>
     * The default implementation never repairs, so the proposal is rejected.
     */
    default Optional<RepairResult> repair(String intent, String neuralProposal, ValidationResult failed,
                                          Map<String, Object> context) {
        return Optional.empty();
    }

    /**
     * The identifier used in {@code @NSDeterministicGate(symbolicSource = ...)}, e.g. {@code "LOCAL_RULES"}.
     */
    String getSourceIdentifier();

    /**
     * @deprecated Left over from an earlier design; the gate never calls it. Every decision is
     * already written to the audit log. Will be removed in a later release.
     */
    @Deprecated(since = "1.5.0", forRemoval = true)
    default void logDiagnosticData(String neuralProposal, String failureReason) {
        // no-op
    }
}
