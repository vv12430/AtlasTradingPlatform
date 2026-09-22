// Node 22+. Requires all three services, Oracle, and Kafka. Creates its own portfolio.
import assert from "node:assert/strict";
const request = async (port, url, body) => {
  const r = await fetch("http://localhost:" + port + url, {
    method: body ? "POST" : "GET",
    headers: { "Content-Type": "application/json" },
    body: body ? JSON.stringify(body) : undefined,
  });
  assert.ok(r.ok, await r.clone().text());
  return r.json();
};
const p = await request(8081, "/api/portfolios", {
  name: "Automated smoke " + Date.now(),
  cash: 1000000,
});
const t = await request(8081, "/api/trades", {
  clientKey: crypto.randomUUID(),
  portfolioId: p.id,
  symbol: "AAPL",
  side: "BUY",
  quantity: 10,
  price: 185,
});
const until = async (fn) => {
  for (let i = 0; i < 60; i++) {
    const result = await fn();
    if (result) return result;
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw Error("Timed out waiting for event workflow");
};
await until(async () => {
  const current = await request(8081, "/api/trades/" + t.id);
  assert.notEqual(current.status, "REJECTED", current.reason);
  return current.status === "EXECUTED";
});
const journal = await until(async () =>
  (await request(8083, "/api/journals")).find((j) => j.tradeId === t.id),
);
assert.equal(
  journal.lines.reduce((s, l) => s + l.debit, 0),
  1850,
);
assert.equal(
  journal.lines.reduce((s, l) => s + l.credit, 0),
  1850,
);
const positions = await request(8081, "/api/portfolios/" + p.id + "/positions");
assert.equal(positions[0].quantity, 10);
assert.equal(positions[0].cost, 1850);
console.log(
  "PASS: Kafka workflow completed with configured database persistence and balanced accounting",
);
