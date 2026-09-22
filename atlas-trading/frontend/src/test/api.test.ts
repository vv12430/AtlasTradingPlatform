import { describe, it, expect, vi, afterEach } from "vitest";
import { api, graph, money } from "../api";
afterEach(() => vi.restoreAllMocks());
describe("HTTP client", () => {
  it("surfaces backend validation errors", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue({
          ok: false,
          status: 409,
          json: async () => ({ detail: "Insufficient cash" }),
        }),
    );
    await expect(api("portfolio", "/api/trades")).rejects.toThrow(
      "Insufficient cash",
    );
  });
  it("surfaces GraphQL errors even with HTTP 200", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue({
          ok: true,
          status: 200,
          json: async () => ({ errors: [{ message: "Policy changed" }] }),
        }),
    );
    await expect(graph("risk", "{}")).rejects.toThrow("Policy changed");
  });
  it("formats USD", () => expect(money(1234.5)).toBe("$1,234.50"));
});
