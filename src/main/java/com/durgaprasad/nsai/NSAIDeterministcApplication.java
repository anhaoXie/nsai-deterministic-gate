package com.durgaprasad.nsai;

import com.durgaprasad.nsai.gate.exception.ComplianceViolationException;
import com.durgaprasad.nsai.service.DemoAiService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
public class NSAIDeterministcApplication {

    public static void main(String[] args) {
        SpringApplication.run(NSAIDeterministcApplication.class, args);
    }

    /**
     * Original startup demo using the mock RDBMS connector. Disabled with
     * {@code nsai.demo.run-on-startup=false}, and replaced by {@code LocalDemoRunner} in local mode.
     */
    @Bean
    @ConditionalOnExpression("${nsai.demo.run-on-startup:true} and !${nsai.gateway.local.enabled:false}")
    public CommandLineRunner runDemo(DemoAiService aiService) {
        return args -> {
            System.out.println("\n--- STARTING NEURO-SYMBOLIC VALIDATION TEST ---");
            try {
                // This will trigger the @NSDeterministicGate in DemoAiService
                String result = aiService.simulateAiResponse("Requesting high interest loan...");
                System.out.println("FINAL SYSTEM OUTPUT: " + result);
            } catch (ComplianceViolationException e) {
                // HARD_REJECT mode: the gate blocked the output instead of letting it through
                System.out.println("BLOCKED: " + e.getViolations());
            }
            System.out.println("--- TEST COMPLETE ---\n");
        };
    }
}
