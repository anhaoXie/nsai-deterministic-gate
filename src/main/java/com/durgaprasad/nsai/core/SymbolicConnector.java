package com.durgaprasad.nsai.core;

import java.util.Map;
import java.util.Optional;

/**
 * Core interface for connecting Neural outputs to Symbolic Truth sources.
 * Implementations facilitate deterministic validation via Cloud Orchestrators 
 * like Google Cloud Workflows or AWS Step Functions.
 * * @author Durga Prasad Dasepalli
 */
public interface SymbolicConnector {

    /**
     * Validates a neural proposal against the symbolic layer.
     * * @param intent The business intent being validated.
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
     * Retrieves the specific symbolic source identifier (e.g., "GCP_KNOWLEDGE_GRAPH").
     */
    String getSourceIdentifier();

    /**
     * Optional: Triggers the ML Diagnostic layer if a validation failure occurs.
     */
    default void logDiagnosticData(String neuralProposal, String failureReason) {
        // Implementation for ML-driven Hallucination Classification
    }
}
