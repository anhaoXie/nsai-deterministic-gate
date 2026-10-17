package com.durgaprasad.nsai.gate.aspect;

import com.durgaprasad.nsai.annotation.NSDeterministicGate;
import com.durgaprasad.nsai.core.RepairChange;
import com.durgaprasad.nsai.core.RepairResult;
import com.durgaprasad.nsai.core.SymbolicConnector;
import com.durgaprasad.nsai.core.ValidationResult;
import com.durgaprasad.nsai.core.Violation;
import com.durgaprasad.nsai.gate.config.EnforcementConfig;
import com.durgaprasad.nsai.gate.config.EnforcementMode;
import com.durgaprasad.nsai.gate.exception.ComplianceViolationException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The core Neuro-Symbolic interceptor.
 * Runs the neural layer (the annotated method), validates its output against the
 * requested symbolic source, and applies the configured enforcement mode
 * (HARD_REJECT or SOFT_REPAIR). Every decision is written as one audit log line.
 */
@Aspect
@Component
public class DeterministicGateAspect {

    private static final Logger log = LoggerFactory.getLogger(DeterministicGateAspect.class);

    @Autowired
    private List<SymbolicConnector> connectors;

    @Autowired
    private EnforcementConfig enforcementConfig;

    @Around("@annotation(gateConfig)")
    public Object validateAIPipeline(ProceedingJoinPoint joinPoint, NSDeterministicGate gateConfig) throws Throwable {
        String intent = gateConfig.intent();
        log.debug("[NSAI-Gate] Intercepting neural proposal for intent={}", intent);

        // 1. Execute the neural layer (the annotated LLM method)
        Object neuralProposal = joinPoint.proceed();

        if (!(neuralProposal instanceof String proposalText)) {
            return neuralProposal; // Only text/JSON output is validated
        }

        // 2. Locate the requested symbolic source.
        //    Fail closed: a typo in symbolicSource must never silently switch the gate off.
        SymbolicConnector connector = connectors.stream()
            .filter(c -> c.getSourceIdentifier().equals(gateConfig.symbolicSource()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No SymbolicConnector registered for symbolicSource '" + gateConfig.symbolicSource()
                + "' (intent " + intent + "). Registered sources: "
                + connectors.stream().map(SymbolicConnector::getSourceIdentifier).toList()));

        Map<String, Object> context = new HashMap<>();
        context.put("origin", "NSAI-GATEWAY");

        // 3. Deterministic validation via the symbolic source
        ValidationResult result = connector.evaluate(intent, proposalText, context);
        String source = connector.getSourceIdentifier();

        if (result.valid()) {
            log.info("[NSAI-Gate] intent={} source={} verdict=ALLOWED", intent, source);
            return neuralProposal;
        }

        String reasons = result.violations().stream()
                .map(Violation::toString)
                .collect(Collectors.joining("; "));
        // The raw proposal may contain personal data, so it is logged only at DEBUG.
        log.debug("[NSAI-Gate] Rejected proposal for intent={}: {}", intent, proposalText);

        // 4. Apply the configured enforcement mode
        if (enforcementConfig.getMode() == EnforcementMode.HARD_REJECT) {
            log.info("[NSAI-Gate] intent={} source={} verdict=BLOCKED mode=HARD_REJECT violations=[{}]",
                    intent, source, reasons);
            throw new ComplianceViolationException(
                    "Execution halted: Neural payload violated strict deterministic constraints for intent: "
                            + intent + " [" + reasons + "]",
                    intent, result.violations());
        }

        // SOFT_REPAIR: let the symbolic source repair the proposal deterministically, if allowed.
        if (repairAllowed(gateConfig)) {
            Optional<RepairResult> repair = connector.repair(intent, proposalText, result, context);
            if (repair.isPresent()) {
                String changes = repair.get().changes().stream()
                        .map(RepairChange::toString)
                        .collect(Collectors.joining("; "));
                log.info("[NSAI-Gate] intent={} source={} verdict=REPAIRED mode=SOFT_REPAIR changes=[{}]",
                        intent, source, changes);
                return repair.get().repairedProposal();
            }
        }

        // Not repairable: reject, exactly as in HARD_REJECT.
        log.info("[NSAI-Gate] intent={} source={} verdict=BLOCKED mode=SOFT_REPAIR reason=not-repairable violations=[{}]",
                intent, source, reasons);
        throw new ComplianceViolationException(
                "Neural payload violated deterministic constraints for intent " + intent
                        + " and could not be repaired [" + reasons + "]",
                intent, result.violations());
    }

    @SuppressWarnings("removal")
    private static boolean repairAllowed(NSDeterministicGate gateConfig) {
        return gateConfig.allowRepair() && gateConfig.enableMLDiagnostics();
    }
}
