export type Portfolio = {
  id: string;
  name: string;
  cash: number;
  active: boolean;
  version: number;
};
export type Trade = {
  id: string;
  portfolioId: string;
  symbol: string;
  side: string;
  quantity: number;
  price: number;
  status: string;
  reason: string;
};
export type Position = {
  symbol: string;
  quantity: number;
  cost: number;
  realizedPnl: number;
};
export type Policy = {
  id: string;
  name: string;
  maxNotional: number;
  enabled: boolean;
  version: number;
};
export type Decision = {
  id: string;
  tradeId: string;
  portfolioId: string;
  notional: number;
  status: string;
  reason: string;
};
export type Line = { accountId: string; debit: number; credit: number };
export type Journal = {
  id: string;
  tradeId: string;
  portfolioId: string;
  description: string;
  lines: Line[];
};
export type Balance = {
  accountId: string;
  name: string;
  debit: number;
  credit: number;
};
export type Account = {
  id: string;
  name: string;
  enabled: boolean;
  version: number;
};
export async function api<T>(
  service: string,
  path: string,
  method = "GET",
  body?: unknown,
): Promise<T> {
  const res = await fetch("/" + service + path, {
    method,
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(10000),
  });
  if (!res.ok) {
    const error = await res.json().catch(() => ({}));
    throw new Error(error.detail || service + " returned HTTP " + res.status);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}
export async function graph<T>(service: string, query: string): Promise<T> {
  const result = await api<{ data: T; errors?: { message: string }[] }>(
    service,
    "/graphql",
    "POST",
    { query },
  );
  if (result.errors?.length)
    throw new Error(result.errors.map((e) => e.message).join("; "));
  return result.data;
}
export const money = (value: number) =>
  new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
  }).format(value);
