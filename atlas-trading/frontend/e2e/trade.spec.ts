import { test, expect } from "@playwright/test";

test("trade flows from ticket through risk into accounting", async ({
  page,
}) => {
  await page.goto("/");
  await expect(
    page.getByRole("button", { name: "Submit buy order" }),
  ).toBeEnabled();
  await page.getByLabel("Quantity", { exact: true }).fill("1");
  await page.getByLabel("Price (USD)").fill("101.23");
  await page.getByRole("button", { name: "Submit buy order" }).click();
  await expect(page.getByRole("status")).toContainText("Trade submitted");
  await page
    .getByRole("button", { name: "Trade blotter", exact: true })
    .click();
  const row = page.locator("tbody tr").filter({ hasText: "$101.23" }).first();
  await expect(row).toContainText("EXECUTED", { timeout: 45000 });
  await page.getByRole("button", { name: "Accounting", exact: true }).click();
  await expect(page.getByText(/BUY 1(?:\.0+)? AAPL/).first()).toBeVisible({
    timeout: 30000,
  });
});
