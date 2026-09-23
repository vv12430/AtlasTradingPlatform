# Atlas Trading Platform - Architecture Diagram

## Trade Lifecycle Flow

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              TRADE LIFECYCLE FLOW                                    │
└─────────────────────────────────────────────────────────────────────────────────────┘

User Request
    │
    ▼
┌──────────────────────┐
│  PortfolioApi        │
│  POST /api/trades    │
└──────────┬───────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           PORTFOLIO SERVICE                                          │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  Tables:                                                                             │
│  • portfolios (id, name, cash, active, version)                                      │
│  • trades (id, client_key, portfolio_id, symbol, side, quantity, price, status,    │
│            reason, created_at)                                                      │
│  • positions (portfolio_id, symbol, quantity, cost, realized_pnl)                  │
│  • outbox (id, topic, event_key, payload, sent, created_at)                       │
│  • inbox (id, created_at)                                                           │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  PortfolioService.submit(input)                                                     │
│  • INSERT trades (status=PENDING_RISK)                                              │
│  • EMIT event to outbox → "trade.submitted.v1"                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
           │
           │ (OutboxPublisher polls and publishes)
           ▼
                    ┌─────────────────────┐
                    │  Kafka Topic:       │
                    │  trade.submitted.v1 │
                    └──────────┬──────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                            RISK SERVICE                                               │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  Tables:                                                                             │
│  • policies (id, name, max_notional, enabled, version)                              │
│  • decisions (id, trade_id, portfolio_id, notional, status, reason, created_at)    │
│  • outbox (id, topic, event_key, payload, sent, created_at)                        │
│  • inbox (id, created_at)                                                           │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  RiskListener.listen() consumes "trade.submitted.v1"                                 │
│  RiskService.assess(event)                                                           │
│  • SELECT policies (enabled=true)                                                    │
│  • INSERT decisions (status=APPROVED/REJECTED)                                       │
│  • EMIT event to outbox → "risk.assessed.v1"                                        │
└─────────────────────────────────────────────────────────────────────────────────────┘
           │
           │ (OutboxPublisher polls and publishes)
           ▼
                    ┌─────────────────────┐
                    │  Kafka Topic:       │
                    │  risk.assessed.v1   │
                    └──────────┬──────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           PORTFOLIO SERVICE (continued)                               │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  TradeListener.listen() consumes "risk.assessed.v1"                                  │
│  PortfolioService.assessed(event)                                                    │
│  • IF APPROVED:                                                                      │
│    - UPDATE trades (status=EXECUTED)                                                 │
│    - INSERT/UPDATE positions (quantity, cost, realized_pnl)                          │
│    - UPDATE portfolios (cash)                                                         │
│    - EMIT event to outbox → "trade.executed.v1"                                      │
│  • IF REJECTED:                                                                      │
│    - UPDATE trades (status=REJECTED, reason)                                         │
└─────────────────────────────────────────────────────────────────────────────────────┘
           │
           │ (OutboxPublisher polls and publishes)
           ▼
                    ┌─────────────────────┐
                    │  Kafka Topic:       │
                    │  trade.executed.v1  │
                    └──────────┬──────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                          ACCOUNTING SERVICE                                           │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  Tables:                                                                             │
│  • accounts (id, name, enabled, version)                                             │
│  • journals (id, trade_id, portfolio_id, description, created_at)                    │
│  • journal_lines (id, journal_id, account_id, debit, credit)                        │
│  • outbox (id, topic, event_key, payload, sent, created_at)                         │
│  • inbox (id, created_at)                                                            │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  AccountingListener.listen() consumes "trade.executed.v1"                           │
│  AccountingService.post(event)                                                       │
│  • INSERT journals (trade_id, portfolio_id, description)                            │
│  • INSERT journal_lines (account_id, debit, credit)                                 │
│    - BUY: debit securities, credit cash                                             │
│    - SELL: debit cash, credit securities                                             │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## CRUD Operations by Service

### Portfolio Service

| Method | HTTP Endpoint | Tables | Operation |
|--------|---------------|--------|-----------|
| portfolios() | GET /api/portfolios | portfolios | SELECT |
| get(id) | GET /api/portfolios/{id} | portfolios | SELECT |
| create(input) | POST /api/portfolios | portfolios | INSERT |
| rename(id, input) | PUT /api/portfolios/{id} | portfolios | UPDATE |
| active(id, input) | PATCH /api/portfolios/{id} | portfolios | UPDATE |
| delete(id) | DELETE /api/portfolios/{id} | portfolios | DELETE |
| trades() | GET /api/trades | trades | SELECT |
| trade(id) | GET /api/trades/{id} | trades | SELECT |
| positions(id) | GET /api/portfolios/{id}/positions | positions | SELECT |
| submit(input) | POST /api/trades | trades, portfolios | INSERT trades, SELECT portfolios (lock) |
| assessed(event) | (Kafka consumer) | trades, positions, portfolios | UPDATE trades, INSERT/UPDATE positions, UPDATE portfolios |

### Risk Service

| Method | HTTP Endpoint | Tables | Operation |
|--------|---------------|--------|-----------|
| policies() | GET /api/policies | policies | SELECT |
| policy(id) | GET /api/policies/{id} | policies | SELECT |
| create(input) | POST /api/policies | policies | INSERT |
| update(id, input) | PUT /api/policies/{id} | policies | UPDATE |
| toggle(id, input) | PATCH /api/policies/{id} | policies | UPDATE |
| delete(id) | DELETE /api/policies/{id} | policies | DELETE |
| decisions() | GET /api/decisions | decisions | SELECT |
| stress(input) | POST /api/stress | (none - calculation) | - |
| assess(event) | (Kafka consumer) | policies, decisions, inbox | SELECT policies, INSERT decisions, SELECT inbox |

### Accounting Service

| Method | HTTP Endpoint | Tables | Operation |
|--------|---------------|--------|-----------|
| accounts() | GET /api/accounts | accounts | SELECT |
| account(id) | GET /api/accounts/{id} | accounts | SELECT |
| create(input) | POST /api/accounts | accounts | INSERT |
| update(id, input) | PUT /api/accounts/{id} | accounts | UPDATE |
| toggle(id, input) | PATCH /api/accounts/{id} | accounts | UPDATE |
| delete(id) | DELETE /api/accounts/{id} | accounts | DELETE |
| journals() | GET /api/journals | journals, journal_lines | SELECT |
| trialBalance() | GET /api/trial-balance | accounts, journal_lines | SELECT |
| post(event) | (Kafka consumer) | journals, journal_lines, inbox | INSERT journals, INSERT journal_lines, SELECT inbox |

## Kafka Topics

| Topic | Publisher | Subscriber | Purpose |
|-------|-----------|------------|---------|
| trade.submitted.v1 | PortfolioService (via OutboxPublisher) | RiskListener | New trade submitted for risk assessment |
| risk.assessed.v1 | RiskService (via OutboxPublisher) | TradeListener | Risk decision (APPROVED/REJECTED) |
| trade.executed.v1 | PortfolioService (via OutboxPublisher) | AccountingListener | Trade executed, ready for accounting posting |

## Transactional Outbox Pattern

Each service has an `outbox` table that stores events to be published:

- **emit(topic, event)**: Inserts row into outbox with sent=0
- **OutboxPublisher.publish()**: Scheduled method that polls unsent rows (sent=0), publishes to Kafka, marks sent=1
- **inbox table**: Used for idempotency - tracks processed event IDs to prevent duplicate processing

## Database Schema Summary

### Portfolio Service
```
portfolios: id (PK), name, cash, active, version
trades: id (PK), client_key (unique), portfolio_id (FK), symbol, side, quantity, price, status, reason, created_at
positions: portfolio_id (FK), symbol (PK), quantity, cost, realized_pnl
outbox: id (PK), topic, event_key, payload, sent, created_at
inbox: id (PK), created_at
```

### Risk Service
```
policies: id (PK), name, max_notional, enabled, version
decisions: id (PK), trade_id (unique), portfolio_id, notional, status, reason, created_at
outbox: id (PK), topic, event_key, payload, sent, created_at
inbox: id (PK), created_at
```

### Accounting Service
```
accounts: id (PK), name, enabled, version
journals: id (PK), trade_id (unique), portfolio_id, description, created_at
journal_lines: id (PK), journal_id (FK), account_id (FK), debit, credit
outbox: id (PK), topic, event_key, payload, sent, created_at
inbox: id (PK), created_at
```

## Key Design Patterns

1. **Transactional Outbox**: Ensures event publishing reliability by storing events in the database within the same transaction
2. **Idempotency**: 
   - Trades use `client_key` as idempotency key
   - Inbox table tracks processed event IDs
   - Version fields on entities for optimistic locking
3. **Event-Driven Architecture**: Services communicate asynchronously via Kafka
4. **Double-Entry Accounting**: Every journal entry has balanced debits and credits
5. **Fail-Closed Risk**: If no active policies exist, trades are rejected by default
