package com.durgaprasad.nsai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Runs the demo application. Start it with the {@code local} profile to see the gate in action:
 * {@code ./gradlew bootRun --args='--spring.profiles.active=local'} (see the README).
 */
@SpringBootApplication
@EnableAspectJAutoProxy
public class NSAIDeterministcApplication {

    public static void main(String[] args) {
        SpringApplication.run(NSAIDeterministcApplication.class, args);
    }
}
