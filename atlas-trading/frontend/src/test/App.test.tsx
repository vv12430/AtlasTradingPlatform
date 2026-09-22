import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { it, expect, vi, beforeEach } from "vitest";
import App from "../App";
import * as client from "../api";
vi.mock("../api", async () => ({
  ...(await vi.importActual("../api")),
  api: vi.fn(),
  graph: vi.fn(),
}));
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(client.api).mockResolvedValue([]);
  vi.mocked(client.graph).mockImplementation(async (service) => {
    if (service === "portfolio")
      return {
        portfolios: [
          {
            id: "demo",
            name: "Global Opportunities",
            cash: 1000000,
            active: true,
            version: 0,
          },
        ],
        trades: [],
      };
    if (service === "risk") return { policies: [], decisions: [] };
    return { journals: [], trialBalance: [], accounts: [] };
  });
});
it("loads balances and submits a validated order", async () => {
  render(<App />);
  await screen.findAllByText("$1,000,000.00");
  await userEvent.click(
    screen.getByRole("button", { name: /Submit buy order/i }),
  );
  await waitFor(() =>
    expect(client.api).toHaveBeenCalledWith(
      "portfolio",
      "/api/trades",
      "POST",
      expect.objectContaining({
        portfolioId: "demo",
        symbol: "AAPL",
        side: "BUY",
        quantity: 10,
        price: 185,
        clientKey: expect.any(String),
      }),
    ),
  );
  expect(await screen.findByRole("status")).toHaveTextContent(
    "Trade submitted",
  );
});
it("navigates to accounting", async () => {
  render(<App />);
  await userEvent.click(screen.getByRole("button", { name: "Accounting" }));
  expect(await screen.findByText("Trial balance")).toBeInTheDocument();
});
it("reports unavailable services", async () => {
  vi.mocked(client.graph).mockRejectedValue(new Error("Service unavailable"));
  render(<App />);
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Service unavailable",
  );
});
