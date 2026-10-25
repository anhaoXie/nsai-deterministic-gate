# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed
- `@NSDeterministicGate(symbolicSource)` now defaults to `LOCAL_RULES`; the old default pointed to a
  mock connector that has been removed.

### Deprecated
- `SymbolicConnector.logDiagnosticData(...)`: never called by the gate; every decision is already in
  the audit log.
- `@NSDeterministicGate(fallbackMethod = ...)`: never implemented; catch `ComplianceViolationException`
  to fall back.

### Removed
- The original startup demo (`DemoAiService`, `MockRdbmsConnector`), replaced by the local-mode demo.

## [1.4.0] - 2026-10-19

The gate can now be tried in five minutes with no cloud account, checks AI answers against
rules you can read, repairs what can be repaired deterministically, and reads rules from
YAML or Neo4j.

### Added
- **YAML rule engine** (`LOCAL_RULES`): `required`, `min`/`max`, `allowed`, `forbidden` and
  `message` per rule; fields read from JSON (dotted paths, arrays) or from free text with a regex,
  checking every occurrence. Safe YAML loading; unknown keys and invalid values stop the
  application at startup. (#20, #21)
- **Local mode**: `docker compose up` runs a loan-offer demo with a mock LLM, or a real local
  model through Ollama (`compose.ollama.yaml`). No cloud account, database or API key. (#23, #24)
- **Vendor-neutral `LlmClient`** with mock and Ollama implementations. (#23)
- **Deterministic repair** in `SOFT_REPAIR` mode: rules marked `repair: clamp` move an
  out-of-range number to the nearest bound; the result is validated again against all rules and
  logged as `verdict=REPAIRED` with each change. `@NSDeterministicGate(allowRepair = false)`
  turns it off per method. (#25)
- **Neo4j rule source** (`NEO4J_RULES`): rules stored as a graph, with intents inheriting the
  rules of other intents (nearest definition wins), the same rule keys and validation as YAML,
  and a per-intent cache (`nsai.gateway.graph.cache-ttl-seconds`). `compose.neo4j.yaml` runs the
  demo against a seeded Neo4j 5. (#28)
- `symbolicSource` accepts property placeholders, e.g. `${app.rule-source:LOCAL_RULES}`. (#28)
- Every gate decision is written as one audit log line:
  `intent=... source=... verdict=ALLOWED|BLOCKED|REPAIRED`. (#19)
- Apache 2.0 license, contributing guide, security policy, code of conduct and issue templates. (#5)
- CI on Java 17 and 21 with coverage, plus smoke tests of both Docker demos. (#6, #28)

### Changed
- **Fail closed**: a `symbolicSource` with no registered connector now throws instead of
  skipping validation (#19); intents with no rules are denied by default (`unknownIntent: deny`). (#20)
- Violations name every failed rule (`ComplianceViolationException.getViolations()`). (#19)
- In `SOFT_REPAIR` mode a proposal that cannot be repaired now throws
  `ComplianceViolationException`, as in `HARD_REJECT`, instead of returning a `"BLOCK: ..."` string. (#25)
- Spring Boot 3.3.0 → 3.5.16, Gradle 8.7 → 8.14.6. (#15, #16, #22)
- README rewritten to describe only what the code does; planned work moved to a roadmap. (#27)

### Deprecated
- `@NSDeterministicGate(enableMLDiagnostics = ...)`: use `allowRepair`. Still honored; will be
  removed in a later release. (#25)

### Removed
- The placeholder ML diagnostic layer (`MlDiagnosticLayer`), which returned a fixed value. (#25)
- The placeholder `Neo4jGraphConnector`, `ComplianceRuleNode` and `ComplianceRuleRepository`,
  and the Spring Data Neo4j dependency (replaced by the Neo4j Java driver). (#28)

### Fixed
- The cache test measured a Mockito stub instead of the cache; it now checks Caffeine's
  hit/miss counters on the real Spring proxy. (#6)

[1.4.0]: https://github.com/durgadasepalli/nsai-deterministic-gate/compare/v1.3.0...v1.4.0
