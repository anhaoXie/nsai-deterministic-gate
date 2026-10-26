# Press & citations

Facts on this page can be checked in the repository, its releases and its CI runs.

## In one sentence

**nsai-deterministic-gate** is an open-source Spring Boot library that checks the output of large language models (LLMs) against deterministic business rules before it reaches users or downstream systems.

## About the project

Large language models are probabilistic: the same prompt can produce a compliant answer one time and a non-compliant one the next. In regulated settings such as lending, insurance and healthcare, that is not acceptable. nsai-deterministic-gate adds a guardrail around the AI call itself. A Java developer annotates a method with `@NSDeterministicGate`; the library runs the model, validates its answer against explicit rules, and then allows it, blocks it with the exact rule that failed, or repairs it deterministically when the rule allows that. The gate fails closed: if it cannot check an answer, it refuses it.

Rules are written in YAML or stored in a Neo4j graph, where one policy can be shared by many intents. The whole gate runs locally with `docker compose up`, with no cloud account.

## Key facts

| | |
|---|---|
| Repository | https://github.com/durgadasepalli/nsai-deterministic-gate |
| License | Apache License 2.0 |
| Language / framework | Java 17 and 21, Spring Boot 3.5 |
| Rule sources | YAML file; Neo4j graph with rule inheritance |
| LLM clients | Mock (for tests and demos); any local model via Ollama |
| First public commit | April 13, 2026 |
| Latest release | [v1.5.0](https://github.com/durgadasepalli/nsai-deterministic-gate/releases/tag/v1.5.0), October 25, 2026 |
| Maven Central | [`io.github.durgadasepalli:nsai-gate-spring-boot-starter`](https://central.sonatype.com/artifact/io.github.durgadasepalli/nsai-gate-spring-boot-starter) |
| Automated tests | 139, including integration tests against a real Neo4j, run on Java 17 and 21 for every change |
| Maintainer | Durga Prasad Dasepalli |

## Maintainer

Durga Prasad Dasepalli is a senior technical architect with 18 years of experience building enterprise platforms in Java, Spring Boot and the cloud (Google Cloud and AWS), and a Google Cloud Professional Cloud Architect. He created nsai-deterministic-gate as an independent open-source project and maintains it in his own time.

## Assets

- Demo output, one answer allowed and two blocked: [`docs/images/demo-blocked.png`](images/demo-blocked.png)
- Rule inheritance in Neo4j: [`docs/images/policy-graph.png`](images/policy-graph.png)
- Architecture overview: [`docs/images/architecture-overview.png`](images/architecture-overview.png)
- Technical design: [`docs/ARCHITECTURE.md`](ARCHITECTURE.md)
- Release notes: [`CHANGELOG.md`](../CHANGELOG.md)
<!-- Add the demo video link here once it is published. -->

Images in this repository may be used in articles about the project, with credit to "nsai-deterministic-gate".

## How to cite

```bibtex
@software{dasepalli_nsai_deterministic_gate,
  author  = {Dasepalli, Durga Prasad},
  title   = {nsai-deterministic-gate: deterministic guardrails for LLM output in Spring Boot},
  year    = {2026},
  version = {1.5.0},
  url     = {https://github.com/durgadasepalli/nsai-deterministic-gate},
  license = {Apache-2.0}
}
```

## Coverage

Independent articles, podcasts and talks about the project, with outlet, author and date.

_None yet._

## Writing by the maintainer

_None yet._

## Press contact

Durga Prasad Dasepalli: [ddasepalli@gmail.com](mailto:ddasepalli@gmail.com)
