# Holdfast

🚧 **Actively under construction — Phase 1 of 8.** This is a technical portfolio project,
documented publicly from the very first commit. The commit history, ADRs and issues are an
intentional part of the presentation: the goal isn't just to show a finished result, but the
engineering process behind it.

## The problem

Sell exactly N units of limited stock (e.g. tickets for an event sector) to M people competing at
the same time, without ever selling the same unit twice and without losing valid sales to
concurrency bugs.

## What this project demonstrates

- Correctness under real concurrency, validated by an integration test running against a real
  Oracle database (via Testcontainers) — not mocks
- Architectural decisions documented as ADRs (`docs/adr`), not just code
- Incremental, public evolution: REST API → Clean Architecture/DDD → performance (Redis, Oracle
  partitioning) → distributed systems → resilience → cloud-native → CI/CD → observability

The full 8-phase roadmap is described in `docs/` (work in progress).

## Stack

Java 25 (LTS) · Spring Boot 3.5 · Oracle (Oracle Free, containerized) · Flyway · Docker ·
Testcontainers · JUnit 5

## Technical decisions

| ADR | Decision |
|---|---|
| [0000](docs/adr/0000-stack-base-java-oracle-docker.md) | Base stack: Java 25, Oracle Free, Docker |
| [0001](docs/adr/0001-estrategia-concorrencia-baixa-estoque.md) | Concurrency strategy for stock deduction |

## Please note! The first two ADR documents mentioned above are in Brazilian Portuguese. From ADR 0002 onwards, the content will be in English.

See also: [environment troubleshooting (Testcontainers + Docker + Oracle on Windows)](docs/troubleshooting.md).

## Running locally

```bash
# Start the Oracle database
docker compose up -d

# Run the tests (includes the concurrency test, via Testcontainers)
./mvnw test
```

## Status

| Phase | Status |
|---|---|
| 1 — MVP (REST API, data modeling, tests) | 🔄 In progress |
| 2 — Clean Architecture + DDD | ⬜ Not started |
| 3 — Performance (Redis, partitioning) | ⬜ Not started |
| 4 — Distributed systems | ⬜ Not started |
| 5 — Resilience | ⬜ Not started |
| 6 — Cloud native | ⬜ Not started |
| 7 — CI/CD | ⬜ Not started |
| 8 — Observability | ⬜ Not started |

## License

Apache 2.0 — see [LICENSE](LICENSE).
