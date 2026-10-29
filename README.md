# nsai-deterministic-gate

[![CI](https://github.com/durgadasepalli/nsai-deterministic-gate/actions/workflows/ci.yml/badge.svg)](https://github.com/durgadasepalli/nsai-deterministic-gate/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
![Java 17 | 21](https://img.shields.io/badge/Java-17%20%7C%2021-orange)

**Deterministic guardrails for LLM output in Spring Boot.** Check AI answers against YAML business rules before users see them. The gate fails closed, reports the exact rule that was broken, and runs locally with `docker compose`.

LLMs are probabilistic: the same prompt can give a compliant answer one time and a non-compliant one the next. In lending, insurance or healthcare that is not acceptable. nsai-deterministic-gate keeps the model for what it is good at (understanding the request and drafting the answer) and moves the final yes/no decision to explicit, testable rules.

## Quick start (no cloud account)
Try the gate in under 5 minutes with the YAML rule engine and a mock LLM. No cloud account, database or API key needed.

**With Docker**
```bash
git clone https://github.com/durgadasepalli/nsai-deterministic-gate.git
cd nsai-deterministic-gate
docker compose up --build
```

**With Java 17+**
```bash
./gradlew :nsai-gate-demo:bootRun --args='--spring.profiles.active=local'
# Windows (PowerShell): .\gradlew.bat :nsai-gate-demo:bootRun --args='--spring.profiles.active=local'
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

**Try repair mode.** `NSAI_MODE=SOFT_REPAIR docker compose up` runs the same demo in `SOFT_REPAIR` mode: request [2] comes back with the rate clamped to 18% and an audit line `verdict=REPAIRED ... changes=[interestRate: 25% -> 18% (max-interest-rate)]`. Request [3] is still blocked, because none of its failed rules allows a repair.

**Use a real local model** with [Ollama](https://ollama.com) (the first run downloads about 2 GB):
```bash
docker compose -f compose.yaml -f compose.ollama.yaml run --rm --build nsai-demo
```

## How it works

```text
caller ──> @NSDeterministicGate method ──> LLM draft
                     │
                     ▼
          SymbolicConnector.evaluate(intent, draft)
                     │
     ┌───────────────┼──────────────────────────┐
     ▼               ▼                          ▼
  ALLOWED       BLOCKED (HARD_REJECT,       REPAIRED (SOFT_REPAIR, every
  draft is      or not repairable):         failed rule has repair: clamp):
  returned      ComplianceViolationException clamped draft, re-validated
                lists every failed rule      against all rules, is returned
```

1. You annotate the method that calls the model with `@NSDeterministicGate`.
2. A Spring AOP aspect runs the method, then hands the answer to the `SymbolicConnector` named by `symbolicSource`.
3. The connector checks the answer against the rules for the `intent` and returns every violation.
4. The configured enforcement mode decides what happens next. Every decision is written as one audit log line (`intent=... source=... verdict=ALLOWED|BLOCKED|REPAIRED`).

**Fail-closed by design.** If `symbolicSource` names no registered connector, the call fails with an exception instead of skipping the check. Intents with no rules are denied unless you set `unknownIntent: allow`. A typo in the rule file (for example `maxx:`) stops the application at startup instead of silently weakening a rule.

More detail: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Use it in your own project
The library is the `nsai-gate-spring-boot-starter` module; `nsai-gate-demo` is the demo application above. Adding the starter to a Spring Boot 3 application is enough: auto-configuration turns the gate on, and properties choose the rule source.

Add it from Maven Central:
```groovy
dependencies { implementation 'io.github.durgadasepalli:nsai-gate-spring-boot-starter:1.5.0' }
```
```xml
<dependency>
    <groupId>io.github.durgadasepalli</groupId>
    <artifactId>nsai-gate-spring-boot-starter</artifactId>
    <version>1.5.0</version>
</dependency>
```
```yaml
nsai:
  gateway:
    enforcement:
      mode: HARD_REJECT                  # or SOFT_REPAIR
    local:
      enabled: true
      rules: classpath:my-rules.yaml     # your rule file
```
For graph mode, also add `org.neo4j.driver:neo4j-java-driver` and set `nsai.gateway.graph.enabled=true` (see below).

## Guard your own method
```java
@NSDeterministicGate(intent = "LOAN_OFFER", symbolicSource = "LOCAL_RULES")
public String draftOffer(String request) {
    return llmClient.complete(request);
}
```

```java
try {
    String offer = assistant.draftOffer(request);
} catch (ComplianceViolationException e) {
    e.getViolations().forEach(v -> log.warn("{}: {}", v.ruleId(), v.message()));
}
```

## Writing rules
In the demo, rules live in [`nsai-gate-demo/src/main/resources/nsai-rules.yaml`](nsai-gate-demo/src/main/resources/nsai-rules.yaml). With Docker the file is mounted, so a rule change needs no rebuild.

```yaml
unknownIntent: deny          # intents with no rules are rejected

intents:
  LOAN_OFFER:
    rules:
      - id: max-interest-rate
        field: interestRate
        pattern: '(?i)\b(?:interest rate|apr|rate)\b\D{0,20}?(\d+(?:\.\d+)?)\s*%'
        required: true
        min: 3.0
        max: 18.0
        repair: clamp
        message: Interest rate must be between 3% and 18% under the lending policy

      - id: no-guaranteed-approval
        forbidden: '(?i)\bguarantee(?:d)?\s+(?:approval|approved)\b'
```

| Key | Meaning |
|---|---|
| `id` | Stable name shown in violations and logs (required) |
| `field` | Value to check, read from JSON (`offer.amount` for nested fields) or from free text via `pattern` |
| `pattern` | Java regex that extracts the field from free text; group 1 is the value. Every match is checked. Case-sensitive unless the expression starts with `(?i)` |
| `required` | The field must be present |
| `min` / `max` | Inclusive numeric bounds; values like `25%` and `$1,500` are understood |
| `allowed` | List of permitted values |
| `forbidden` | Java regex that must not match anywhere in the answer. Case-sensitive unless the expression starts with `(?i)` |
| `message` | Text shown when the rule fails |
| `repair` | `clamp` lets `SOFT_REPAIR` move an out-of-range number to the nearest bound; default `none` |

Both regex keys use Java `Pattern` syntax and are **case-sensitive by default**: `forbidden: 'guaranteed approval'` misses "Guaranteed Approval", and `pattern: 'client id (\w+)'` misses "Client ID: 42". Prefix the expression with `(?i)` to match either casing — `forbidden: '(?i)guaranteed approval'`, `pattern: '(?i)client id:?\s*(\w+)'`. The demo rules above, and the ones in `nsai-rules.yaml`, all start with `(?i)` for this reason.

## Rules in Neo4j (graph mode)
The same rules can live in a Neo4j graph instead of the YAML file. The graph adds inheritance: an intent picks up the rules of the intents it `INHERITS` from, so a shared policy is changed in one place.

```text
(:Intent {id:'LOAN_OFFER'}) -[:INHERITS]-> (:Intent {id:'CONSUMER_LENDING'}) -[:INHERITS]-> (:Intent {id:'ALL_CUSTOMER_COMMS'})
(:Intent)-[:REQUIRES]->(:Rule {id, field, pattern, required, min, max, allowed, forbidden, message, repair})
```

Try it with Docker; it starts Neo4j, loads [`graph-seed.cypher`](nsai-gate-demo/src/main/resources/graph-seed.cypher) and runs the same demo against the graph:
```bash
docker compose -f compose.yaml -f compose.neo4j.yaml up --build
```
While it runs, the Neo4j Browser at http://localhost:7474 shows the policy graph (user `neo4j`, password `nsai-demo-password`, demo only).

- Rule nodes use exactly the YAML keys and the same validation: a typo such as `maxx` blocks the intent instead of weakening it.
- When a rule id appears at several levels, the definition nearest to the intent wins.
- Fail-closed: if Neo4j is unreachable, the intent is unknown or a rule node is invalid, the answer is blocked (`rule-source-unavailable`, `unknown-intent`, `invalid-rule-definition`).
- Rules are cached per intent for `nsai.gateway.graph.cache-ttl-seconds` (default 600).
- Enable it in your own app with `nsai.gateway.graph.enabled=true` and the standard `spring.neo4j.*` settings, then use `symbolicSource = "NEO4J_RULES"`.

## Enforcement modes
Set `nsai.gateway.enforcement.mode` (or `NSAI_MODE` with Docker Compose).

- **`HARD_REJECT`**: any violation throws `ComplianceViolationException` with every failed rule.
- **`SOFT_REPAIR`**: the gate first tries a deterministic repair. The answer is repaired only if every failed rule has `repair: clamp`. Out-of-range numbers are moved to the nearest bound (25% with `max: 18` becomes 18%), the result is validated again against all rules, and the change is logged as `verdict=REPAIRED`. Anything else is rejected exactly as in `HARD_REJECT`. No model is involved in the repair. Use `@NSDeterministicGate(allowRepair = false)` to turn repair off for one method.

## Connectors and LLM clients
| Component | Status |
|---|---|
| `LOCAL_RULES`: YAML rule engine | Available |
| `MockLlmClient`, `OllamaLlmClient` (any local Ollama model) | Available |
| `NEO4J_RULES`: rules stored in Neo4j, with inheritance between intents and a Caffeine cache | Available |
| Your own source | Implement `SymbolicConnector` and register it as a Spring bean |

## Status and roadmap
The library is published to Maven Central (see [Use it in your own project](#use-it-in-your-own-project)); releases are listed in [CHANGELOG.md](CHANGELOG.md).

Planned:
- Spring Boot 4 support
- JMH benchmarks of the gate's overhead
- Cloud LLM clients (Vertex AI, Amazon Bedrock) behind the same `LlmClient` interface
- Analysis of blocked answers to show which rules fail most often

Ideas and pull requests are welcome; please open an issue first for larger changes.

## Build and test
```bash
./gradlew build   # compiles both modules, runs all tests, writes coverage to <module>/build/reports/jacoco
```
CI runs the tests on Java 17 and 21 and smoke-tests both Docker demos (YAML rules and Neo4j) on every pull request and push to `main`. The graph-mode tests start a real Neo4j with [Testcontainers](https://testcontainers.com); they are skipped when Docker is not running.

## Contributing
See [CONTRIBUTING](CONTRIBUTING.md). To report a vulnerability, see [SECURITY](SECURITY.md). This project follows a [code of conduct](CODE_OF_CONDUCT.md).

## Maintainer
Durga Prasad Dasepalli, [LinkedIn](https://www.linkedin.com/in/durgaprasaddasepalli/)

## License
Licensed under the [Apache License, Version 2.0](LICENSE).
