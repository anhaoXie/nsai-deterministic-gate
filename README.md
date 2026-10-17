# nsai-deterministic-gate: A Self-Optimizing Neuro-Symbolic Gateway

[![CI](https://github.com/durgadasepalli/nsai-deterministic-gate/actions/workflows/ci.yml/badge.svg)](https://github.com/durgadasepalli/nsai-deterministic-gate/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
![Java 17 | 21](https://img.shields.io/badge/Java-17%20%7C%2021-orange)

Eliminating the "Hallucination Tax" via Deterministic Logic and ML-Driven Diagnostics.
nsai-deterministic-gate is a specialized Spring Boot framework designed for Senior Architects implementing Generative AI in mission-critical environments (Finance, Healthcare, Cloud Infrastructure). It enforces a Neuro-Symbolic (NSAI) architecture—separating "Perception" (LLMs) from "Truth" (Symbolic Logic)—while utilizing Machine Learning to classify and optimize the interaction between these layers.

## Quick start: local mode (no cloud account)
Try the gate in under 5 minutes with a YAML rule engine and a mock LLM. No cloud account, graph database or API key needed.

**With Docker**
```bash
git clone https://github.com/durgadasepalli/nsai-deterministic-gate.git
cd nsai-deterministic-gate
docker compose up --build
```

**With Java 17+**
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
# Windows (PowerShell): .\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

The demo sends three loan requests for a fictional bank through the gate:

```text
[1] Premium customer with excellent credit wants a 36-month personal loan of $15,000.
    ALLOWED -> {"clientId": "CUST-1001", "interestRate": "6.9%", "termMonths": 36, ...}

[2] Subprime customer with recent late payments wants a fast 12-month loan.
    BLOCKED
      - max-interest-rate: Interest rate must be between 3% and 18% under the lending policy (interestRate = 25 exceeds the maximum 18)

[3] Customer asks us to guarantee approval for a 3-month loan.
    BLOCKED
      - client-id-required: Every offer must reference a client (required field 'clientId' is missing)
      - term-range: Loan term must be 6 to 84 months (termMonths = 3 is below the minimum 6)
      - no-guaranteed-approval: Offers must not promise guaranteed approval (proposal contains forbidden text 'guaranteed approval')
```

**Change the rules.** Edit [`src/main/resources/nsai-rules.yaml`](src/main/resources/nsai-rules.yaml) and run again. Rules support `required`, `min`/`max`, `allowed` values and `forbidden` patterns, and read fields from JSON or free text:

```yaml
intents:
  LOAN_OFFER:
    rules:
      - id: max-interest-rate
        field: interestRate
        pattern: '(?i)\b(?:interest rate|apr|rate)\b\D{0,20}?(\d+(?:\.\d+)?)\s*%'
        max: 18.0
      - id: no-guaranteed-approval
        forbidden: '(?i)\bguarantee(?:d)?\s+(?:approval|approved)\b'
```

**Use a real local model** with [Ollama](https://ollama.com) (first run downloads about 2 GB):
```bash
docker compose -f compose.yaml -f compose.ollama.yaml run --rm --build nsai-demo
```

**Guard your own method** in local mode:
```java
@NSDeterministicGate(intent = "LOAN_OFFER", symbolicSource = "LOCAL_RULES")
public String draftOffer(String request) {
    return llmClient.complete(request);
}
```
In `HARD_REJECT` mode a violating answer throws `ComplianceViolationException`, and `getViolations()` lists every failed rule.

In `SOFT_REPAIR` mode the gate first tries a deterministic repair. If every failed rule is marked `repair: clamp`, out-of-range numbers are moved to the nearest bound (an `interestRate` of 25% with `max: 18` becomes 18%), the result is validated again against all rules, and the change is logged as `verdict=REPAIRED`. Anything else is rejected exactly as in `HARD_REJECT`. No model is involved in the repair. Set `allowRepair = false` on the annotation to turn it off for one method.

## The Architecture: NSAI + ML
This framework implements a three-tier governance model:

[View Full Architecture Documentation](docs/ARCHITECTURE.md). -- Deep dive into the Neuro-Symbolic bridge and deterministic logic flow.

**Neural Layer (Inference):** Vertex AI or Amazon Bedrock handles intent extraction and conversational logic.

**Symbolic Layer (Enforcement):** GCP Enterprise Knowledge Graph or AWS Neptune enforces deterministic business rules via Google Cloud Workflows.

**ML Diagnostic Layer (Optimization):** A classification model analyzes every "blocked" proposal to identify patterns of Logic Drift and provide an auditable report on Hallucination Risk.

## Key Features
**@NSDeterministicGate Interceptor:** A Spring AOP-based annotation to wrap AI services in a deterministic safety layer.

**Symbolic Connectors:** Native integration with Graph Databases to serve as a Single Source of Truth (SSOT).

**ML-Feedback Module:** An integrated Machine Learning classifier that categorizes "Hallucinations" (e.g., Policy Violation vs. Calculation Error) to refine system prompts automatically.

**Cloud Orchestration Adapters:** Ready-to-use templates for Google Cloud Workflows and AWS Step Functions.

## Getting Started
**Maven Dependency** (planned for the 1.4.0 release; not yet published to Maven Central — build from source for now)
```xml
<dependency>
    <groupId>com.durgaprasad.ai</groupId>
    <artifactId>nsai-deterministic-gate-starter</artifactId>
    <version>1.0.0-BETA</version>
</dependency>
```

**Example Usage**
```java
@Service
public class FinancialService {

    @NSDeterministicGate(
        intent = "LOAN_APPROVAL",
        symbolicSource = "GCP_KNOWLEDGE_GRAPH"
    )
    public String processLoanRequest(String userPrompt) {
        // The gate intercepts this call and validates the response 
        // against the Knowledge Graph before returning to the user.
        return vertexAiClient.prompt(userPrompt);
    }
}
```

## Significance & Research Goals
The nsai-deterministic-gate framework addresses a critical gap in current Enterprise AI patterns: the lack of a formal bridge between probabilistic model outputs and deterministic business requirements.

### Primary Objectives:
**Optimization of the "Hallucination Tax":** Developing architectural patterns that reduce computational overhead and token waste by shifting validation logic to a symbolic layer.

**Deterministic AI Governance:** Providing a standardized interface for high-stakes industries (Finance, Cloud Infrastructure, Healthcare) where non-deterministic responses are not an option.

**ML-Driven Diagnostics:** Advancing the study of Neuro-Symbolic AI (NSAI) by using Machine Learning classifiers to audit and refine the boundary between neural "perception" and symbolic "truth."

## Research & Scholarly Development
The nsai-deterministic-gate is the central implementation of an ongoing research series focused on Deterministic AI Logic.

**Objective::** To establish standardized architectural patterns that bridge probabilistic neural outputs with rigid symbolic business rules.

**Current Focus:** Investigating real-time logic interception and the "Hallucination Tax" in regulated cloud environments.

**Status:** Currently being documented for submission to upcoming international AI and Cloud Architecture conferences.

## Build and test
```bash
./gradlew build   # compiles, runs all tests, writes coverage to build/reports/jacoco
```

## Contribution & Peer Review
I am actively seeking feedback from fellow Association for Computing Machinery (ACM) members and Lead Architects. If you are implementing high-stakes AI and want to collaborate on the Deterministic Gateway pattern, please open an issue or reach out on LinkedIn.

**Architect:** Durga Prasad Dasepalli

**Focus:** Cloud Infrastructure, NSAI, & ML Governance

**LinkedIn:** https://www.linkedin.com/in/durgaprasaddasepalli/

## License
Licensed under the [Apache License, Version 2.0](LICENSE). See [CONTRIBUTING](CONTRIBUTING.md) to get involved and [SECURITY](SECURITY.md) to report a vulnerability.
