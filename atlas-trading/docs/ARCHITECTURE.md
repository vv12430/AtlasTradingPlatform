# Architecture and learning guide

## Repository map

```
platform-common/       Event envelope, inbox/outbox, Kafka error handling, API errors
portfolio-service/     Portfolio aggregate, position accounting, execution simulator
risk-service/          Order policies, risk audit, scenario calculation
accounting-service/    Chart of accounts, immutable clearing journals
frontend/              React + TypeScript, Vite, Vitest, Playwright, nginx
infra/oracle/          Independent schema initialization
scripts/               Cross-service workflow smoke tests
.run/                  IntelliJ Java application configurations
.github/workflows/     Unit/integration checks and Docker smoke pipeline
```

## Transaction boundaries and consistency

A single request never starts a distributed database transaction. Submission commits only in the Portfolio schema and returns 202. Each later event creates a separate local transaction. The user observes intermediate states. Risk approval does not guarantee execution: cash may have been spent, positions sold, or the portfolio deactivated while risk was evaluating. Portfolio rechecks these invariants under its row lock and may reject the trade after risk approves it. Risk records therefore represent risk decisions, not final fills.

The publisher is intentionally a simple single-instance polling worker. Each batch contains at most 100 unsent rows. Kafka messages are keyed by portfolio ID. Database locks preserve safety even with duplicate publication, but submission order is not guaranteed across multiple producers or equal timestamps. This version assumes one running instance of each service and does not promise FIFO execution. Scaling the outbox requires row claims/leases, explicit aggregate sequence numbers, ordered delivery, and a partition-aware relay (or a CDC implementation).

The inbox table uses event ID uniqueness. Concurrent duplicate deliveries can race on the pre-check; the unique constraint rolls back one transaction and Kafka retries it. A second business-key constraint on trade ID prevents a new envelope ID from duplicating an existing risk decision or journal. An executed/rejected trade ignores subsequent decisions. No failure path records the inbox independently of its domain write.

## Financial arithmetic

Amounts are USD decimals. Quantity and price accept six fractional digits. Execution notional is rounded to two decimals with HALF_EVEN. A notional that rounds to zero is rejected. Cash and cost are stored as NUMBER(19,2); quantities as NUMBER(19,6). UI calculations and GraphQL Float serialization are display-oriented; Java BigDecimal remains authoritative. A production financial GraphQL contract should use a Decimal scalar or decimal strings.

For a BUY, cash falls by rounded notional and position cost rises by the same amount. For a SELL, removed cost = old cost × sold quantity ÷ old quantity, rounded to cents. Realized P&L = sale proceeds − removed cost. Selling the full position removes the full remaining cost. The platform disallows short selling and margin.

The accounting service posts two clearing lines: BUY debits securities clearing and credits cash clearing; SELL reverses those accounts. These journals reconcile trade consideration and always balance. They intentionally do not represent a complete investment general ledger: opening capital, inventory cost relief, realized gain accounts, fees, and settlement receivables/payables are extension work. Portfolio cash is not expected to equal the accounting cash-clearing net debit.

## Failure handling

| Failure | Expected behavior |
|---|---|
| Kafka unavailable at submission | Trade and outbox persist; order stays pending; publication retries |
| Publisher crashes after Kafka acknowledges | Same event may be published again; inbox prevents repeated effects |
| Risk rejects the amount | Trade transitions to REJECTED; no position or journal |
| Insufficient cash after approval | Portfolio rejects during execution; no execution event |
| Accounting is offline | Execution succeeds; journal appears when its consumer resumes |
| Consumer handler fails | Local transaction rolls back; retry then DLT |
| User retries same client key and payload | Existing trade returned without a new order |
| User reuses key with different input | Conflict; no new order |
| User edits stale configuration | Version conflict; refresh first |

Inspect DLT records before replay. Fix the underlying problem, preserve the original event ID, and publish the corrected/unchanged contract to the original topic. Do not simply mark an outbox row sent or delete inbox history. There is no automated DLT replay UI in this version. Outbox/inbox/journal retention is unbounded for learning; production needs retention and archival policies.

## Exercises

1. Submit BUY 10 at 100, then SELL 4 at 120. Verify cash changes by −520, remaining cost is 600, and realized P&L is 80.
2. Reduce the risk limit to 500 and submit an order above it. Follow the decision and rejected trade; verify no journal appears.
3. Disable all policies. Show that risk fails closed even for a small trade.
4. Stop Accounting, execute a trade, then restart it. Observe eventual consistency without losing the fill.
5. Publish the same execution event twice. Verify one journal and two lines, not two journals.
6. Submit two sells whose combined quantity exceeds the position. Verify exactly one can fill when each requests the whole holding.
7. Edit the same policy from two clients using its old version. Explain why the second update must fail.
8. Follow a trade ID through HTTP, the risk decision, Kafka payloads, and its journal.

## Suggested next increments

Add these as separate tested capabilities rather than conflating them with the implemented behavior:

- OIDC resource servers and portfolio-scoped authorization; audit the actor on every command.
- Market-data ingestion and independently versioned price snapshots; mark-to-market NAV and unrealized P&L.
- Reservation-based aggregate exposure limits with explicit rejection/release events.
- An execution adapter with partial fills, cancellations, fees, and a proper order state machine.
- Full ledger postings for funding, inventory cost, realized gains, and settlement.
- Decimal GraphQL scalars, cursor pagination, bounded audit queries, OpenAPI generation.
- Event contract compatibility tests, distributed tracing, dashboards for outbox lag, DLT counts, and consumer lag.
- A leased outbox relay, retention jobs, chaos tests, and multi-instance concurrency verification.
