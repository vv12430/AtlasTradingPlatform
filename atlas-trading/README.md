# Atlas Trading Workspace

A Java 21 / Maven / Spring Boot learning platform inspired by the **portfolio → risk → execution → accounting** workflow found in institutional investment systems. It is independent of BlackRock and is not an Aladdin implementation or production trading system.

## Start in IntelliJ IDEA

Requirements: JDK **21**, Maven **3.9+**, Node **22.12+** (24 also works), Docker Desktop with Linux containers. Allocate approximately 8 GB of memory to Docker; Oracle's first startup and image download can take several minutes.

1. Open this folder's **pom.xml** as a Maven project. Set **Project SDK**, **Maven importer JDK**, and **Maven runner JRE** to Java 21. Reload Maven projects.
2. Start Docker Desktop. In a terminal at this folder, run:

   ```shell
   docker compose up -d --wait oracle kafka
   mvn clean verify
   ```

3. Run the three supplied IntelliJ configurations: **Atlas portfolio**, **Atlas risk**, **Atlas accounting**. If your IDE does not import shared configurations, run each service's `com.atlas.<service>.Application` main class directly. Community Edition works: these are ordinary Java Application configurations.
4. In a second terminal:

   ```shell
   cd frontend
   npm ci
   npm run dev
   ```

5. Open **http://localhost:5173**. The seeded `Global Opportunities` portfolio has USD 1,000,000 of simulated cash. Submit BUY 10 AAPL at 185. Its state should become EXECUTED, a position appears, and Accounting receives a USD 1,850 balanced journal. Updates poll every three seconds.

On Windows PowerShell, use `npm.cmd` if execution policy blocks `npm.ps1`.

### Everything in Docker

```shell
docker compose --profile full up -d --build
docker compose logs -f portfolio risk accounting
```

Open http://localhost:5173 once services finish starting. Do not simultaneously run the IntelliJ applications on the same ports. `docker compose --profile full down` stops containers and preserves data. Oracle users and passwords are seeded on first initialization only. Removing named volumes would delete local data; it is not part of the normal stop command.

### Run packaged Java applications without IntelliJ

```shell
mvn clean package -DskipTests
java -jar portfolio-service/target/portfolio-service-1.0.0.jar
java -jar risk-service/target/risk-service-1.0.0.jar
java -jar accounting-service/target/accounting-service-1.0.0.jar
```

Run the three `java` commands in separate terminals after starting Oracle and Kafka.

## What is implemented

| Service | Port | Owned data | Responsibilities |
|---|---:|---|---|
| Portfolio | 8081 | portfolios, trades, positions, inbox, outbox | Portfolio CRUD, order submission, idempotency, execution simulation, cash, weighted-average cost, realized P&L |
| Risk | 8082 | policies, decisions, inbox, outbox | Versioned order limits, fail-closed risk decisions, audit records, deterministic stress scenarios |
| Accounting | 8083 | accounts, journals, journal_lines, inbox, outbox | Protected system accounts, immutable double-entry trade clearing, trial balance |

All services use independent Oracle users/schemas in one local Oracle Free instance. Flyway migrates each schema independently. Services never query one another's tables. Each has more than six REST endpoints, GraphQL queries and mutations, bean validation, health endpoints, and unit/integration tests.

REST covers **GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS** in each service. Spring MVC provides HEAD and OPTIONS on mapped resources. TRACE and CONNECT are deliberately not business APIs. PUT replaces the editable configuration fields; balances and IDs are server-managed. GraphQL uses POST `/graphql`; GraphiQL is at `/graphiql` on each service.

The frontend uses GraphQL for dashboard reads and REST for writes. It has portfolio creation/renaming/activation, an order ticket, positions, a trade blotter, policy management, stress scenarios, trial balance, journals, and account creation. Loading, offline, validation, and submission states are visible. It displays real service data, not fabricated quotes.

## Event workflow

```mermaid
sequenceDiagram
    participant UI as React UI
    participant P as Portfolio
    participant K as Kafka
    participant R as Risk
    participant A as Accounting
    UI->>P: POST /api/trades + clientKey
    P->>P: Transaction: trade + outbox
    P-->>UI: 202 PENDING_RISK
    P->>K: trade.submitted.v1
    K->>R: Consume by portfolio key
    R->>R: Transaction: inbox + decision + outbox
    R->>K: risk.assessed.v1
    K->>P: APPROVED or REJECTED
    P->>P: Lock portfolio; check cash/quantity; fill + outbox
    P->>K: trade.executed.v1
    K->>A: Executed trade
    A->>A: Transaction: inbox + journal + two lines
    UI->>P: Refresh GraphQL dashboard
```

- **Transactional outbox:** the domain state and event payload commit together. A scheduled publisher waits for Kafka acknowledgement before marking a row sent. A crash after sending may cause redelivery, so this is at-least-once delivery, not global exactly-once processing.
- **Idempotent consumers:** an inbox event ID and business effects commit in the same database transaction. Unique trade IDs also prevent duplicate risk decisions or journals even if a duplicate business event has a new event ID.
- **Aggregate locking:** execution locks the portfolio row, serializing changes to its cash and positions. Concurrent sells cannot jointly create a short position.
- **Optimistic edits:** portfolios, policies, and accounts require the current `version`. A stale update fails rather than overwriting newer state.
- **Retries and dead letters:** consumers retry four times at one-second intervals before publishing to a corresponding `.DLT` topic. Failed DLT publication throws so the record is not silently considered recovered. Outbox publication keeps retrying unsent rows.
- **Schema contract:** events carry `version: 1`, stable trade/portfolio IDs, decimal quantities/prices, and an event ID. Unknown event versions are rejected.

See [architecture and learning guide](docs/ARCHITECTURE.md) for tradeoffs, recovery exercises, and extension paths, and [API reference](docs/API.md) for requests.

## Testing

```shell
mvn test                 # Unit tests
mvn verify               # Unit + Spring/JDBC/HTTP/GraphQL integration + embedded Kafka
cd frontend
npm test                 # Vitest + Testing Library
npm run build            # TypeScript + production bundle
```

Backend integration tests use H2 in Oracle compatibility mode and real Flyway migrations; an embedded Kafka broker exercises the Risk listener and outbox. They need no Docker. H2 is not proof of Oracle compatibility, so the repository also includes a real Oracle/Kafka workflow smoke test:

```shell
# With all services running against Docker Oracle and Kafka:
node scripts/smoke.mjs
# Or Windows:
powershell -File scripts/smoke.ps1

cd frontend
npx playwright install chromium
npm run test:e2e
```

The browser E2E test uses the running stack and creates a trade. Start with the default risk policy enabled and at least USD 101.23 in the selected portfolio. The smoke script creates its own funded portfolio. CI contains a JVM/frontend job and an independent Docker Oracle/Kafka smoke job. Reports are under each module's `target/surefire-reports` and `target/failsafe-reports`.

For UI-only browser tests with contract fixtures, start the Vite dev server and run `npx playwright test e2e/ui.spec.ts`. For the real backend workflow only, run `npx playwright test e2e/trade.spec.ts`. See [verification record](docs/VERIFICATION.md) for the checks actually run and the remaining Oracle/Maven environment limitations.

## Local connections

| Connection | Value |
|---|---|
| Oracle JDBC | `jdbc:oracle:thin:@//localhost:1521/FREEPDB1` |
| Oracle users | `portfolio`, `risk`, `accounting` |
| Local application passwords | `AtlasLocal123` |
| Oracle administrative password | `LocalAdmin123` |
| Kafka host address | `localhost:9092` |
| Kafka address inside Compose | `kafka:29092` |
| Health | `http://localhost:8081/actuator/health` (also 8082/8083) |

`DB_URL`, `DB_USER`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP`, and `PORT` override application defaults. Credentials are intentionally local demo credentials. Compose exposes ports only on loopback. There is no authentication/authorization layer in this learning version; do not expose it publicly.

## Troubleshooting

- **Docker engine unavailable:** start Docker Desktop and wait until `docker info` succeeds.
- **Oracle still starting:** check `docker compose logs oracle` and `docker compose ps`. Applications need a healthy database before Flyway starts.
- **ORA-01017:** ensure users/passwords match the initialized volume. Changing the SQL file does not rerun it on an existing volume.
- **Pending trade:** inspect the services' logs and unsent outbox rows; check Kafka connectivity, consumer groups, and `.DLT` topics.
- **GraphQL HTTP 200 with errors:** inspect the response `errors` field; the UI handles this explicitly.
- **Port occupied:** stop duplicate JVMs/containers or change `PORT` and frontend proxies together.
- **IDE module missing:** reload the root Maven project; confirm all four modules imported.

## Boundaries

This is a runnable reference implementation of a distributed trading workflow, not a full institutional platform. It has no exchange connectivity, live market data, multi-currency support, settlement calendar, corporate actions, derivative pricing, factor risk/VaR, regulatory reporting, tax accounting, authentication, or disaster-recovery guarantees. Prices are entered by the user; fills are simulated and immediate after approval. Risk limits are per-order, not aggregate exposure limits. Accounting records trade clearing at consideration amounts; opening funding and realized P&L are not posted to the ledger. UI polling gives near-real-time visibility rather than a WebSocket market feed.

Versions are pinned in Maven, package-lock.json, and Compose. Compatibility references: [Spring Boot 3.5 requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Oracle Free container documentation](https://github.com/gvenzl/oci-oracle-free), and [Apache Kafka Docker guide](https://kafka.apache.org/39/getting-started/docker/).
