import { test, expect } from "@playwright/test";

// Browser-level contract fixtures. This suite does not replace the real Kafka workflow test.
test("dashboard, order ticket, risk scenario, journals and mobile layout", async ({
  page,
}) => {
  const portfolios = [
    {
      id: "demo",
      name: "Global Opportunities",
      cash: 972700,
      active: true,
      version: 2,
    },
  ];
  const trades = [
    {
      id: "fixture-trade",
      portfolioId: "demo",
      symbol: "AAPL",
      side: "BUY",
      quantity: 100,
      price: 185,
      status: "EXECUTED",
      reason: "Simulated fill at submitted price",
    },
  ];
  const positions = [
    { symbol: "AAPL", quantity: 100, cost: 18500, realizedPnl: 240 },
    { symbol: "MSFT", quantity: 20, cost: 8800, realizedPnl: 0 },
  ];
  const policies = [
    {
      id: "default",
      name: "Maximum order notional (USD)",
      maxNotional: 100000,
      enabled: true,
      version: 0,
    },
  ];
  const decisions = [
    {
      id: "decision",
      tradeId: "fixture-trade",
      portfolioId: "demo",
      notional: 18500,
      status: "APPROVED",
      reason: "All active order limits passed",
    },
  ];
  const lines = [
    { accountId: "securities", debit: 18500, credit: 0 },
    { accountId: "cash", debit: 0, credit: 18500 },
  ];
  const journals = [
    {
      id: "journal",
      tradeId: "fixture-trade",
      portfolioId: "demo",
      description: "BUY 100 AAPL",
      lines,
    },
  ];
  const trialBalance = [
    {
      accountId: "cash",
      name: "Cash settlement clearing",
      debit: 0,
      credit: 18500,
    },
    {
      accountId: "securities",
      name: "Securities transaction clearing",
      debit: 18500,
      credit: 0,
    },
  ];
  let submitted: Record<string, unknown> | undefined;
  await page.route("**/portfolio/**", async (route) => {
    if (route.request().url().endsWith("/graphql"))
      return route.fulfill({ json: { data: { portfolios, trades } } });
    if (route.request().url().endsWith("/positions"))
      return route.fulfill({ json: positions });
    if (route.request().method() === "POST") {
      submitted = route.request().postDataJSON();
      return route.fulfill({
        status: 202,
        json: { ...submitted, id: "new-trade", status: "PENDING_RISK" },
      });
    }
    return route.fulfill({ json: {} });
  });
  await page.route("**/risk/**", async (route) =>
    route.fulfill({
      json: route.request().url().endsWith("/graphql")
        ? { data: { policies, decisions } }
        : { pnl: -4095, stressedValue: 23205 },
    }),
  );
  await page.route("**/accounting/**", async (route) =>
    route.fulfill({ json: { data: { journals, trialBalance, accounts: [] } } }),
  );
  await page.setViewportSize({ width: 1440, height: 1100 });
  await page.goto("/");
  await expect(page.getByText("$972,700.00").first()).toBeVisible();
  await expect(page.getByRole("alert")).toHaveCount(0);
  await page.screenshot({
    path: "../docs/screenshots/dashboard-fixture.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Submit buy order" }).click();
  await expect(page.getByRole("status")).toContainText("Trade submitted");
  expect(submitted).toMatchObject({
    portfolioId: "demo",
    symbol: "AAPL",
    side: "BUY",
    quantity: 10,
    price: 185,
  });
  expect(submitted?.clientKey).toBeTruthy();
  await page.getByRole("button", { name: "Risk studio", exact: true }).click();
  await page.getByRole("button", { name: "Run scenario" }).click();
  await expect(page.getByText("-$4,095.00")).toBeVisible();
  await page.getByRole("button", { name: "Accounting", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Trial balance" }),
  ).toBeVisible();
  await expect(page.getByText("BUY 100 AAPL")).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: "Overview", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Portfolio overview" }),
  ).toBeVisible();
  await page.screenshot({
    path: "../docs/screenshots/mobile-fixture.png",
    fullPage: true,
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});

test("retry after a network failure retains the order idempotency key", async ({
  page,
}) => {
  const keys: string[] = [];
  await page.route("**/portfolio/**", async (route) => {
    if (route.request().url().endsWith("/graphql"))
      return route.fulfill({
        json: {
          data: {
            portfolios: [
              {
                id: "demo",
                name: "Demo",
                cash: 10000,
                active: true,
                version: 0,
              },
            ],
            trades: [],
          },
        },
      });
    if (route.request().url().endsWith("/positions"))
      return route.fulfill({ json: [] });
    keys.push(route.request().postDataJSON().clientKey);
    return route.fulfill({
      status: keys.length === 1 ? 503 : 202,
      json:
        keys.length === 1
          ? { detail: "Temporary failure" }
          : { id: "trade", status: "PENDING_RISK" },
    });
  });
  await page.route("**/risk/graphql", (route) =>
    route.fulfill({ json: { data: { policies: [], decisions: [] } } }),
  );
  await page.route("**/accounting/graphql", (route) =>
    route.fulfill({
      json: { data: { journals: [], trialBalance: [], accounts: [] } },
    }),
  );
  await page.goto("/");
  const button = page.getByRole("button", { name: "Submit buy order" });
  await button.click();
  await expect(page.getByRole("status")).toContainText("Temporary failure");
  await button.click();
  await expect(page.getByRole("status")).toContainText("Trade submitted");
  expect(keys).toHaveLength(2);
  expect(keys[0]).toEqual(keys[1]);
});
