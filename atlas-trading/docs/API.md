# API reference

Use service ports 8081 (Portfolio), 8082 (Risk), and 8083 (Accounting). JSON requests use `Content-Type: application/json`. There is no authentication in the local learning profile. Read lists are small demo queries: trade/decision/journal lists return the latest 200 rows. IDs are UUIDs except seeded IDs (`demo`, `default`, `cash`, `securities`).

## Portfolio

| Verb | Path | Input / behavior |
|---|---|---|
| GET | `/api/portfolios` | All portfolios |
| GET | `/api/portfolios/{id}` | One portfolio; 404 if absent |
| POST | `/api/portfolios` | `{"name":"Growth","cash":1000000}`; 201 |
| PUT | `/api/portfolios/{id}` | `{"name":"Growth II","version":0}` |
| PATCH | `/api/portfolios/{id}` | `{"active":false,"version":1}` |
| DELETE | `/api/portfolios/{id}` | 204; rejects portfolios with trade history |
| GET | `/api/portfolios/{id}/positions` | Quantity, remaining cost, realized P&L |
| GET | `/api/trades` | Latest trade blotter |
| GET | `/api/trades/{id}` | State and reason |
| POST | `/api/trades` | Trade command below; 202 |
| HEAD | `/api/portfolios` | Same headers as GET, no body |
| OPTIONS | `/api/portfolios` | Framework-generated Allow header |

```json
{
  "clientKey": "your-unique-request-key",
  "portfolioId": "demo",
  "symbol": "AAPL",
  "side": "BUY",
  "quantity": 10,
  "price": 185
}
```

Preserve `clientKey` when retrying after a timeout. Use a new key for a genuinely new order. Valid sides are BUY and SELL. No automatic short positions are allowed.

## Risk

| Verb | Path | Input / behavior |
|---|---|---|
| GET | `/api/policies` | All policies |
| GET | `/api/policies/{id}` | One policy |
| POST | `/api/policies` | `{"name":"Small orders","maxNotional":10000,"version":0}`; 201 |
| PUT | `/api/policies/{id}` | `{"name":"Small orders","maxNotional":20000,"version":0}` |
| PATCH | `/api/policies/{id}` | `{"enabled":false,"version":1}` |
| DELETE | `/api/policies/{id}` | Must first be disabled; 204 |
| GET | `/api/decisions` | Latest immutable decisions |
| POST | `/api/stress` | `{"exposure":100000,"shockPercent":-15}` → P&L −15000, value 85000 |
| HEAD | `/api/policies` | Headers |
| OPTIONS | `/api/policies` | Allowed methods |

Every active policy is evaluated. No active policies means rejection. Policies apply globally to all portfolios; risk is per-order not portfolio VaR.

## Accounting

| Verb | Path | Input / behavior |
|---|---|---|
| GET | `/api/accounts` | Chart of accounts |
| GET | `/api/accounts/{id}` | One account |
| POST | `/api/accounts` | `{"name":"Fees","version":0}`; 201 |
| PUT | `/api/accounts/{id}` | `{"name":"Broker fees","version":0}` |
| PATCH | `/api/accounts/{id}` | `{"enabled":false,"version":1}`; protected system accounts cannot be disabled |
| DELETE | `/api/accounts/{id}` | 204; system/referenced accounts protected |
| GET | `/api/journals` | Latest journals including debit/credit lines |
| GET | `/api/trial-balance` | Account totals across all portfolios |
| HEAD | `/api/accounts` | Headers |
| OPTIONS | `/api/accounts` | Allowed methods |

Journals are written by execution events only. There is no endpoint that silently deletes financial history.

## GraphQL

Each service has a separate schema. POST `/graphql` with `{"query":"...","variables":{...}}`. GraphQL field errors may be returned with HTTP 200; always inspect `errors`. GraphiQL at `/graphiql` can inspect the complete schema.

Portfolio query:

```graphql
query {
  portfolios { id name cash active version }
  positions(id: "demo") { symbol quantity cost realizedPnl }
  trades { id symbol side quantity price status reason }
}
```

Portfolio mutation:

```graphql
mutation {
  submitTrade(input: {
    clientKey: "graphql-order-001", portfolioId: "demo",
    symbol: "MSFT", side: "BUY", quantity: 2, price: 400
  }) { id status }
}
```

Risk query/mutation:

```graphql
query { policies { id name maxNotional enabled version } decisions { tradeId notional status reason } }
mutation { stress(input: {exposure: 100000, shockPercent: -15}) { pnl stressedValue } }
```

Accounting query/mutation:

```graphql
query { trialBalance { accountId name debit credit } journals { id tradeId description lines { accountId debit credit } } }
mutation { createAccount(input: {name: "Broker fees", version: 0}) { id name } }
```

`createPortfolio` and `createPolicy` mutations are also available. REST offers versioned PUT/PATCH/DELETE editing; GraphQL does not duplicate every REST command. Schemas are checked in under each service's `src/main/resources/graphql`.

## Errors and diagnostics

REST uses 400 for invalid input, 404 for missing reads, and 409 for business conflicts, stale edits, duplicate keys, and referenced resources. REST error bodies use Spring ProblemDetail for handled business errors. Bean-validation errors use Spring's validation response. GraphQL validation/domain errors appear in `errors`; unexpected failures remain internal errors.

Use `/actuator/health`, `/actuator/health/readiness`, and `/actuator/metrics` for local diagnostics. Health does not prove the entire Kafka workflow has completed: run the supplied cross-service smoke test for that.
