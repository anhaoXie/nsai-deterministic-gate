package com.durgaprasad.nsai.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods for Neuro-Symbolic (NSAI) validation.
 * Intercepts LLM outputs and validates them against a deterministic symbolic layer.
 * * @author Durga Prasad Dasepalli
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NSDeterministicGate {

    /**
     * The business intent for the transaction (e.g., "LOAN_APPROVAL").
     */
    String intent() default "";

    /**
     * The symbolic data source to use for validation (e.g., "GCP_KNOWLEDGE_GRAPH").
     */
    String symbolicSource() default "DEFAULT_RDBMS";

    /**
     * In SOFT_REPAIR mode, allows the symbolic source to repair a failing proposal
     * deterministically (for example, clamping a rate to its allowed maximum).
     * When {@code false}, a failing proposal is always rejected.
     */
    boolean allowRepair() default true;

    /**
     * @deprecated There is no machine-learning step; use {@link #allowRepair()}.
     * Setting this to {@code false} still disables repair, for backward compatibility.
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    boolean enableMLDiagnostics() default true;

    /**
     * Fallback method name if the symbolic layer blocks the neural proposal.
     */
    String fallbackMethod() default "";
}
