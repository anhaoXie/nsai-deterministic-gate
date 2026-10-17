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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeterministicGateAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private NSDeterministicGate gateConfig;

    @Mock
    private SymbolicConnector mockConnector;

    @Spy
    private List<SymbolicConnector> connectors = new ArrayList<>();

    @Mock
    private EnforcementConfig enforcementConfig;

    @InjectMocks
    private DeterministicGateAspect gateAspect;

    private final String sampleIntent = "LOAN_OFFER";
    private final String mockSourceId = "MOCK_RDBMS";
    private final String invalidNeuralProposal = "{\"interestRate\": \"25%\"}";
    private final ValidationResult rateViolation =
            ValidationResult.fail("max-interest-rate", "interestRate = 25 exceeds the maximum 18");

    @BeforeEach
    void setUp() {
        connectors.clear();
        connectors.add(mockConnector);

        lenient().when(gateConfig.symbolicSource()).thenReturn(mockSourceId);
        lenient().when(gateConfig.intent()).thenReturn(sampleIntent);
        lenient().when(mockConnector.getSourceIdentifier()).thenReturn(mockSourceId);
    }

    private void givenViolatingProposal(EnforcementMode mode) throws Throwable {
        when(joinPoint.proceed()).thenReturn(invalidNeuralProposal);
        when(mockConnector.evaluate(eq(sampleIntent), eq(invalidNeuralProposal), anyMap())).thenReturn(rateViolation);
        when(enforcementConfig.getMode()).thenReturn(mode);
    }

    @SuppressWarnings("removal")
    private void givenRepairAllowed() {
        when(gateConfig.allowRepair()).thenReturn(true);
        when(gateConfig.enableMLDiagnostics()).thenReturn(true);
    }

    @Test
    @DisplayName("Passes a compliant proposal through unchanged")
    void testSuccessfulPassThrough() throws Throwable {
        String validProposal = "{\"interestRate\": \"4.5%\"}";
        when(joinPoint.proceed()).thenReturn(validProposal);
        when(mockConnector.evaluate(eq(sampleIntent), eq(validProposal), anyMap())).thenReturn(ValidationResult.pass());

        Object result = gateAspect.validateAIPipeline(joinPoint, gateConfig);

        assertThat(result).isEqualTo(validProposal);
        verifyNoInteractions(enforcementConfig);
        verify(mockConnector, never()).repair(anyString(), anyString(), any(), anyMap());
    }

    @Test
    @DisplayName("Passes the validation context to the connector")
    void passesContextToConnector() throws Throwable {
        when(joinPoint.proceed()).thenReturn("ok");
        when(mockConnector.evaluate(eq(sampleIntent), eq("ok"), eq(Map.of("origin", "NSAI-GATEWAY"))))
                .thenReturn(ValidationResult.pass());

        assertThat(gateAspect.validateAIPipeline(joinPoint, gateConfig)).isEqualTo("ok");
    }

    @Test
    @DisplayName("HARD_REJECT throws with the failed rules and never attempts a repair")
    void testHardRejectEnforcement() throws Throwable {
        givenViolatingProposal(EnforcementMode.HARD_REJECT);

        assertThatThrownBy(() -> gateAspect.validateAIPipeline(joinPoint, gateConfig))
                .isInstanceOfSatisfying(ComplianceViolationException.class, e -> {
                    assertThat(e.getIntent()).isEqualTo(sampleIntent);
                    assertThat(e.getViolations()).extracting(Violation::ruleId).containsExactly("max-interest-rate");
                    assertThat(e.getMessage()).contains("max-interest-rate").doesNotContain("25%\"}");
                });

        verify(mockConnector, never()).repair(anyString(), anyString(), any(), anyMap());
    }

    @Test
    @DisplayName("SOFT_REPAIR returns the repaired proposal when the connector can repair it")
    void softRepairReturnsRepairedProposal() throws Throwable {
        givenViolatingProposal(EnforcementMode.SOFT_REPAIR);
        givenRepairAllowed();
        String repaired = "{\"interestRate\":\"18%\"}";
        when(mockConnector.repair(eq(sampleIntent), eq(invalidNeuralProposal), eq(rateViolation), anyMap()))
                .thenReturn(Optional.of(new RepairResult(repaired,
                        List.of(new RepairChange("max-interest-rate", "interestRate", "25%", "18%")))));

        Object result = gateAspect.validateAIPipeline(joinPoint, gateConfig);

        assertThat(result).isEqualTo(repaired);
    }

    @Test
    @DisplayName("SOFT_REPAIR rejects a proposal the connector cannot repair")
    void softRepairRejectsWhenNotRepairable() throws Throwable {
        givenViolatingProposal(EnforcementMode.SOFT_REPAIR);
        givenRepairAllowed();
        when(mockConnector.repair(eq(sampleIntent), eq(invalidNeuralProposal), eq(rateViolation), anyMap()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> gateAspect.validateAIPipeline(joinPoint, gateConfig))
                .isInstanceOfSatisfying(ComplianceViolationException.class, e -> {
                    assertThat(e.getMessage()).contains("could not be repaired").contains("max-interest-rate");
                    assertThat(e.getViolations()).extracting(Violation::ruleId).containsExactly("max-interest-rate");
                });
    }

    @Test
    @DisplayName("SOFT_REPAIR with allowRepair=false rejects without asking for a repair")
    void softRepairDisabledRejects() throws Throwable {
        givenViolatingProposal(EnforcementMode.SOFT_REPAIR);
        when(gateConfig.allowRepair()).thenReturn(false);

        assertThatThrownBy(() -> gateAspect.validateAIPipeline(joinPoint, gateConfig))
                .isInstanceOf(ComplianceViolationException.class);

        verify(mockConnector, never()).repair(anyString(), anyString(), any(), anyMap());
    }

    @Test
    @SuppressWarnings("removal")
    @DisplayName("The deprecated enableMLDiagnostics=false still turns repair off")
    void deprecatedDiagnosticsFlagStillDisablesRepair() throws Throwable {
        givenViolatingProposal(EnforcementMode.SOFT_REPAIR);
        when(gateConfig.allowRepair()).thenReturn(true);
        when(gateConfig.enableMLDiagnostics()).thenReturn(false);

        assertThatThrownBy(() -> gateAspect.validateAIPipeline(joinPoint, gateConfig))
                .isInstanceOf(ComplianceViolationException.class);

        verify(mockConnector, never()).repair(anyString(), anyString(), any(), anyMap());
    }

    @Test
    @DisplayName("Fails closed when symbolicSource names no registered connector")
    void failsClosedOnUnknownSource() throws Throwable {
        when(gateConfig.symbolicSource()).thenReturn("TYPO_SOURCE");
        when(joinPoint.proceed()).thenReturn(invalidNeuralProposal);

        assertThatThrownBy(() -> gateAspect.validateAIPipeline(joinPoint, gateConfig))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TYPO_SOURCE")
                .hasMessageContaining(mockSourceId);

        verify(mockConnector, never()).evaluate(eq(sampleIntent), eq(invalidNeuralProposal), anyMap());
    }

    @Test
    @DisplayName("Non-text results are returned without validation")
    void nonStringResultIsNotValidated() throws Throwable {
        Map<String, Object> structured = Map.of("interestRate", 25);
        when(joinPoint.proceed()).thenReturn(structured);

        assertThat(gateAspect.validateAIPipeline(joinPoint, gateConfig)).isSameAs(structured);
        verifyNoInteractions(enforcementConfig);
    }
}
