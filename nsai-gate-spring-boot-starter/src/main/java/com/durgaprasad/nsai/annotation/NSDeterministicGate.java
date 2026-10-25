package com.durgaprasad.nsai.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method whose return value (LLM output) is checked against deterministic rules
 * before it is returned. The gate fails closed: an answer it cannot check is refused.
 *
 * @author Durga Prasad Dasepalli
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NSDeterministicGate {

    /**
     * The business intent for the transaction (e.g., "LOAN_APPROVAL").
     */
    String intent() default "";

    /**
     * The symbolic source to validate against, e.g. {@code "LOCAL_RULES"} or {@code "NEO4J_RULES"}.
     * Property placeholders are resolved, e.g. {@code "${app.rule-source:LOCAL_RULES}"}.
     */
    String symbolicSource() default "LOCAL_RULES";

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
     * @deprecated Never implemented; the value is ignored. To fall back when an answer is
     * blocked, catch {@code ComplianceViolationException}. Will be removed in a later release.
     */
    @Deprecated(since = "1.5.0", forRemoval = true)
    String fallbackMethod() default "";
}
