package com.durgaprasad.nsai.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SymbolicConnectorTest {

    /** A connector that only implements the original boolean contract. */
    private static final SymbolicConnector LEGACY = new SymbolicConnector() {
        @Override
        public boolean validate(String intent, String neuralProposal, Map<String, Object> context) {
            return !neuralProposal.contains("25%");
        }

        @Override
        public String getSourceIdentifier() {
            return "LEGACY";
        }
    };

    @Test
    @DisplayName("Default evaluate() keeps boolean-only connectors working")
    void defaultEvaluateWrapsValidate() {
        assertTrue(LEGACY.evaluate("LOAN_OFFER", "rate 5%", Map.of()).valid());

        ValidationResult failed = LEGACY.evaluate("LOAN_OFFER", "rate 25%", Map.of());
        assertFalse(failed.valid());
        assertEquals(1, failed.violations().size());
        assertEquals("LEGACY", failed.violations().get(0).ruleId());
        assertTrue(failed.violations().get(0).message().contains("LOAN_OFFER"));
    }

    @Test
    @DisplayName("ValidationResult rejects contradictory states")
    void validationResultInvariants() {
        assertTrue(ValidationResult.pass().violations().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ValidationResult.fail(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ValidationResult(true, List.of(new Violation("r", "m"))));
        assertEquals("r: m", new Violation("r", "m").toString());
    }
}
