# Verification record

Verified locally on September 22, 2026 with Java 21.0.10 and Node 24.14.1.

| Check | Result |
|---|---|
| Java 21 compilation | All 28 project Java source files, including tests, compiled successfully |
| Portfolio tests | 8 passed: validation/rounding, buy/sell arithmetic, duplicate commands/events, insufficient cash, overselling, concurrent sells, stale edits, REST/GraphQL |
| Risk tests | 6 passed: boundary limits, stress arithmetic, duplicate decisions, fail-closed policy behavior, REST/GraphQL, real embedded Kafka listener/outbox |
| Accounting tests | 6 passed: debit/credit directions, balanced amounts, invalid inputs, duplicate posting, protected accounts, REST/GraphQL |
| Frontend unit/component tests | 6 passed with Vitest and Testing Library |
| Browser UI contract tests | 2 passed: dashboard/order/risk/accounting/mobile layout and idempotency-key preservation after a failed request |
| Browser full workflow | 1 passed: UI → Portfolio → Kafka → Risk → Kafka → Portfolio → Kafka → Accounting |
| Cross-service smoke script | Passed with three separate service JVMs, embedded Kafka, and H2 databases |
| TypeScript / Vite production build | Passed |
| npm dependency audit | Zero reported vulnerabilities following build-tool updates; production-only audit also passed |
| Docker Compose configuration | `docker compose --profile full config --quiet` passed |
| Oracle/Kafka image references | Registry manifests resolved for `gvenzl/oracle-free:23.26.3-slim` and `apache/kafka:3.9.1` |

## What was not verified

**The Docker/Oracle runtime and normal Maven `clean verify` command could not be completed in this restricted host environment.** Docker had no running engine. The installed JDK could read project JARs but its Windows canonical-path operation threw AccessDeniedException under the user profile, including during compiler file-manager cleanup and Maven clean.

To verify the code despite that restriction, a temporary Java Compiler API harness compiled the same checked-in sources with `--release 21` and `-parameters`. JUnit Platform 1.12.2 then ran the checked-in test classes against the Maven-resolved dependencies. The harness avoided the failing compiler file-manager close operation; it did not modify application logic or tests. This is evidence that compilation and tests pass, **not** a claim that the normal Maven lifecycle passed here.

The live three-service test used the checked-in test configuration with separate in-memory H2 databases in Oracle compatibility mode and an actual Kafka broker. No Kafka calls were mocked in that workflow. H2 does not substitute for Oracle dialect/runtime verification. Run the Docker smoke CI job or `node scripts/smoke.mjs` against the Oracle stack to complete that check on a machine with Docker running.

## Screenshots

- `screenshots/live-h2-dashboard.png`: actual running services after the browser workflow test, using H2 and embedded Kafka.
- `screenshots/dashboard-fixture.png` and `screenshots/mobile-fixture.png`: browser contract fixtures used to verify populated desktop/mobile layouts; these are explicitly fixture data.

The normal developer workflow remains the Maven commands in the README. The temporary host-specific compiler harness is not part of the application or its runtime requirements.
