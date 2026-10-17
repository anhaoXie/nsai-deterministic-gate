package com.durgaprasad.nsai.local;

import com.durgaprasad.nsai.demo.LoanOfferAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "nsai.demo.run-on-startup=false",
        "nsai.gateway.enforcement.mode=SOFT_REPAIR"
})
@ActiveProfiles("local")
class LocalModeSoftRepairIntegrationTest {

    @Autowired
    private LoanOfferAssistant assistant;

    @Test
    void violatingDraftIsRoutedToRepairInsteadOfThrowing() {
        String result = assistant.draftOffer("Subprime customer wants a fast loan");

        assertThat(result).startsWith("ACCEPTED WITH ML DIAGNOSTIC REPAIR");
    }

    @Test
    void compliantDraftIsUntouched() {
        assertThat(assistant.draftOffer("premium")).contains("CUST-1001");
    }
}
