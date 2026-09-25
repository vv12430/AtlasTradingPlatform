import { useEffect, useState, useRef, type FormEvent } from "react";
import {
  Activity,
  ArrowDownUp,
  BriefcaseBusiness,
  ChartNoAxesCombined,
  ChevronRight,
  Layers3,
  Plus,
  RefreshCw,
  ShieldCheck,
  Wallet,
} from "lucide-react";
import {
  api,
  graph,
  money,
  type Portfolio,
  type Trade,
  type Position,
  type Policy,
  type Decision,
  type Journal,
  type Balance,
  type Account,
  type TradeTimeline,
} from "./api";
type Snapshot = {
  portfolios: Portfolio[];
  trades: Trade[];
  policies: Policy[];
  decisions: Decision[];
  journals: Journal[];
  balances: Balance[];
  accounts: Account[];
};
const empty: Snapshot = {
  portfolios: [],
  trades: [],
  policies: [],
  decisions: [],
  journals: [],
  balances: [],
  accounts: [],
};
const tabs = [
  "Overview",
  "Trade blotter",
  "Risk studio",
  "Accounting",
] as const;
const status = (s: string) => (
  <span className={"badge " + s.toLowerCase()}>{s.replaceAll("_", " ")}</span>
);
export default function App() {
  const [data, setData] = useState<Snapshot>(empty),
    [positions, setPositions] = useState<Position[]>([]),
    [selected, setSelected] = useState("demo"),
    [tab, setTab] = useState<(typeof tabs)[number]>("Overview");
  const [selectedTradeId, setSelectedTradeId] = useState<string | null>(null),
    [timeline, setTimeline] = useState<TradeTimeline | null>(null),
    [timelineLoading, setTimelineLoading] = useState(false),
    [timelineError, setTimelineError] = useState("");
  const [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(false),
    [updated, setUpdated] = useState("");
  const [symbol, setSymbol] = useState("AAPL"),
    [side, setSide] = useState("BUY"),
    [quantity, setQuantity] = useState("10"),
    [price, setPrice] = useState("185");
  const [shock, setShock] = useState("-15"),
    [stress, setStress] = useState<{
      pnl: number;
      stressedValue: number;
    } | null>(null),
    [name, setName] = useState(""),
    [initialCash, setInitialCash] = useState("1000000");
  const [limit, setLimit] = useState("100000"),
    [accountName, setAccountName] = useState(""),
    [newPolicyName, setNewPolicyName] = useState("");
  const key = useRef<string | null>(null),
    loadSequence = useRef(0);
  async function refresh() {
    const sequence = ++loadSequence.current;
    try {
      const [p, r, a, pos] = await Promise.all([
        graph<{ portfolios: Portfolio[]; trades: Trade[] }>(
          "portfolio",
          "{ portfolios { id name cash active version } trades { id portfolioId symbol side quantity price status reason } }",
        ),
        graph<{ policies: Policy[]; decisions: Decision[] }>(
          "risk",
          "{ policies { id name maxNotional enabled version } decisions { id tradeId portfolioId notional status reason } }",
        ),
        graph<{
          journals: Journal[];
          trialBalance: Balance[];
          accounts: Account[];
        }>(
          "accounting",
          "{ journals { id tradeId portfolioId description lines { accountId debit credit } } trialBalance { accountId name debit credit } accounts { id name enabled version } }",
        ),
        api<Position[]>(
          "portfolio",
          "/api/portfolios/" + selected + "/positions",
        ),
      ]);
      if (sequence !== loadSequence.current) return;
      setData({
        ...p,
        ...r,
        journals: a.journals,
        balances: a.trialBalance,
        accounts: a.accounts,
      });
      setPositions(pos);
      setError("");
      setUpdated(new Date().toLocaleTimeString());
    } catch (e) {
      if (sequence === loadSequence.current)
        setError(e instanceof Error ? e.message : "Unable to connect");
    } finally {
      if (sequence === loadSequence.current) setLoading(false);
    }
  }
  useEffect(() => {
    void refresh();
    const timer = setInterval(() => void refresh(), 3000);
    return () => {
      clearInterval(timer);
      loadSequence.current++;
    };
  }, [selected]);
  useEffect(() => {
    if (!selectedTradeId) {
      setTimeline(null);
      return;
    }
    let active = true;
    setTimelineLoading(true);
    setTimelineError("");
    void api<TradeTimeline>(
      "portfolio",
      "/api/trades/" + selectedTradeId + "/timeline",
    )
      .then((result) => {
        if (active) setTimeline(result);
      })
      .catch((e) => {
        if (active)
          setTimelineError(
            e instanceof Error ? e.message : "Unable to load trade timeline",
          );
      })
      .finally(() => {
        if (active) setTimelineLoading(false);
      });
    return () => {
      active = false;
    };
  }, [selectedTradeId]);
  const portfolio = data.portfolios.find((p) => p.id === selected),
    cost = positions.reduce((s, p) => s + p.cost, 0),
    pnl = positions.reduce((s, p) => s + p.realizedPnl, 0),
    trades = data.trades.filter((t) => t.portfolioId === selected),
    pending = trades.filter((t) => t.status === "PENDING_RISK").length;
  async function action(fn: () => Promise<unknown>, message: string) {
    setBusy(true);
    setNotice("");
    try {
      await fn();
      setNotice(message);
      await refresh();
    } catch (e) {
      setNotice(
        "Action failed: " + (e instanceof Error ? e.message : String(e)),
      );
    } finally {
      setBusy(false);
    }
  }
  function submit(e: FormEvent) {
    e.preventDefault();
    if (!key.current) key.current = crypto.randomUUID();
    void action(async () => {
      await api("portfolio", "/api/trades", "POST", {
        clientKey: key.current,
        portfolioId: selected,
        symbol,
        side,
        quantity: Number(quantity),
        price: Number(price),
      });
      key.current = null;
    }, "Trade submitted. Risk and accounting updates arrive asynchronously.");
  }
  function change(fn: () => void) {
    fn();
    key.current = null;
  }
  return (
    <div className="shell">
      <aside>
        <a className="brand" href="#">
          <Layers3 size={30} />
          <span>
            ATLAS<small>INVESTMENT WORKSPACE</small>
          </span>
        </a>
        <div className="workspace">
          LOCAL ENVIRONMENT <span>SIMULATION</span>
        </div>
        <nav>
          {tabs.map((t, i) => {
            const Icon = [
              ChartNoAxesCombined,
              ArrowDownUp,
              ShieldCheck,
              Wallet,
            ][i];
            return (
              <button
                className={tab === t ? "active" : ""}
                key={t}
                onClick={() => setTab(t)}
              >
                <Icon size={19} />
                {t}
                <ChevronRight size={14} />
              </button>
            );
          })}
        </nav>
        <div className="side-note">
          <Activity size={21} />
          <strong>Connected by events</strong>
          <p>Portfolio → Risk → Execution → Accounting</p>
          <small>
            USD • Long-only equities
            <br />
            Simulated fills • No exchange connection
          </small>
        </div>
        <footer>
          <span className="avatar">LC</span>
          <div>
            Local workspace<small>Learning & development</small>
          </div>
        </footer>
      </aside>
      <main>
        <header>
          <div className="breadcrumb">
            Workspace <ChevronRight size={14} />
            <b>{tab}</b>
          </div>
          <div className="connection">
            <span className={"dot " + (error ? "offline" : "")} />
            {error
              ? "Connection interrupted"
              : updated
                ? "Updated " + updated
                : "Connecting"}
            <button
              aria-label="Refresh data"
              className="icon"
              onClick={() => void refresh()}
            >
              <RefreshCw size={16} />
            </button>
          </div>
        </header>
        <div className="content">
          <div className="title-row">
            <div>
              <p className="eyebrow">YOUR INVESTMENT COMMAND CENTER</p>
              <h1>{tab === "Overview" ? "Portfolio overview" : tab}</h1>
              <p className="subtitle">
                {tab === "Overview"
                  ? "A clear view of your capital, positions, and trade lifecycle."
                  : tab === "Trade blotter"
                    ? "Submit simulated orders and follow every state transition."
                    : tab === "Risk studio"
                      ? "Control order limits and explore deterministic market shocks."
                      : "Trace executed trades into balanced clearing journals."}
              </p>
            </div>
            <select
              aria-label="Selected portfolio"
              value={selected}
              onChange={(e) => {
                setSelected(e.target.value);
                key.current = null;
              }}
            >
              {data.portfolios.length ? (
                data.portfolios.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))
              ) : (
                <option value="demo">Global Opportunities</option>
              )}
            </select>
          </div>
          {error && (
            <div role="alert" className="alert">
              {error}. Start all three services and Oracle/Kafka.{" "}
              {updated
                ? "Last successful data remains visible."
                : "No live data loaded."}
            </div>
          )}
          {notice && (
            <div role="status" className="notice">
              {notice}
            </div>
          )}
          <section className="metrics">
            <Metric
              label="CAPITAL AT BOOK COST"
              value={portfolio ? money(portfolio.cash + cost) : "—"}
              detail="Cash + remaining position cost"
              icon={<BriefcaseBusiness size={20} />}
            />
            <Metric
              label="AVAILABLE CASH"
              value={portfolio ? money(portfolio.cash) : "—"}
              detail="Checked again at execution"
              icon={<Wallet size={20} />}
            />
            <Metric
              label="REALIZED P&L"
              value={portfolio ? money(pnl) : "—"}
              detail="Weighted-average cost basis"
              icon={<ChartNoAxesCombined size={20} />}
            />
            <Metric
              label="PENDING ORDERS"
              value={portfolio ? String(pending) : "—"}
              detail="Awaiting risk assessment"
              icon={<ShieldCheck size={20} />}
            />
          </section>
          {(tab === "Overview" || tab === "Trade blotter") && (
            <div className="grid">
              <section className="panel">
                <div className="panel-heading">
                  <div>
                    <h2>
                      {tab === "Overview"
                        ? "Current positions"
                        : "Trade lifecycle"}
                    </h2>
                    <p>
                      {tab === "Overview"
                        ? "Book values, not live market valuations"
                        : "The latest 200 orders across the workspace"}
                    </p>
                  </div>
                  <span className="pill">
                    {tab === "Overview"
                      ? positions.length + " holdings"
                      : trades.length + " orders"}
                  </span>
                </div>
                {tab === "Overview" ? (
                  <>
                    <table>
                      <thead>
                        <tr>
                          <th>Instrument</th>
                          <th>Quantity</th>
                          <th>Cost basis</th>
                          <th>Realized P&L</th>
                        </tr>
                      </thead>
                      <tbody>
                        {positions.map((p) => (
                          <tr key={p.symbol}>
                            <td>
                              <b className="ticker">{p.symbol.slice(0, 2)}</b>
                              <strong>{p.symbol}</strong>
                            </td>
                            <td>{p.quantity}</td>
                            <td>{money(p.cost)}</td>
                            <td
                              className={
                                p.realizedPnl >= 0 ? "positive" : "negative"
                              }
                            >
                              {money(p.realizedPnl)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    {!positions.length && (
                      <Empty
                        text={
                          loading
                            ? "Loading positions…"
                            : "Your next investment starts here. Submit a BUY order to open a position."
                        }
                      />
                    )}
                    <div className="allocation">
                      <h3>Capital allocation</h3>
                      <div className="allocation-bar">
                        <span
                          style={{
                            width:
                              (portfolio && portfolio.cash + cost > 0
                                ? cost / (portfolio.cash + cost)
                                : 0) *
                                100 +
                              "%",
                          }}
                        />
                      </div>
                      <div className="legend">
                        <span>● Securities at cost {money(cost)}</span>
                        <span>
                          ● Cash {portfolio ? money(portfolio.cash) : "—"}
                        </span>
                      </div>
                    </div>
                  </>
                ) : (
                  <TradeTable
                    trades={trades}
                    onSelect={setSelectedTradeId}
                    selectedTradeId={selectedTradeId}
                  />
                )}
              </section>
              <section className="panel ticket">
                <div className="panel-heading">
                  <div>
                    <h2>Order ticket</h2>
                    <p>Simulated execution • USD</p>
                  </div>
                  <ArrowDownUp size={20} />
                </div>
                <form onSubmit={submit}>
                  <div className="segmented">
                    {["BUY", "SELL"].map((s) => (
                      <button
                        type="button"
                        className={side === s ? "chosen" : ""}
                        key={s}
                        onClick={() => change(() => setSide(s))}
                      >
                        {s}
                      </button>
                    ))}
                  </div>
                  <label>
                    Symbol
                    <input
                      required
                      pattern="[A-Z][A-Z0-9.]{0,11}"
                      value={symbol}
                      onChange={(e) =>
                        change(() => setSymbol(e.target.value.toUpperCase()))
                      }
                    />
                  </label>
                  <div className="form-row">
                    <label>
                      Quantity
                      <input
                        type="number"
                        required
                        min="0.000001"
                        max="1000000"
                        step="0.000001"
                        value={quantity}
                        onChange={(e) =>
                          change(() => setQuantity(e.target.value))
                        }
                      />
                    </label>
                    <label>
                      Price (USD)
                      <input
                        type="number"
                        required
                        min="0.000001"
                        max="1000000"
                        step="0.000001"
                        value={price}
                        onChange={(e) => change(() => setPrice(e.target.value))}
                      />
                    </label>
                  </div>
                  <div className="estimate">
                    <span>Estimated notional</span>
                    <strong>{money(Number(quantity) * Number(price))}</strong>
                  </div>
                  <button
                    className="primary"
                    disabled={busy || !portfolio || !portfolio.active}
                  >
                    Submit {side.toLowerCase()} order <ChevronRight size={16} />
                  </button>
                  <p className="fine">
                    Every order passes asynchronous risk checks. Cash and
                    holdings are validated before a simulated fill.
                  </p>
                </form>
              </section>
            </div>
          )}
          {selectedTradeId && (tab === "Overview" || tab === "Trade blotter") && (
            <TradeTimelinePanel
              trade={trades.find((t) => t.id === selectedTradeId)}
              timeline={timeline}
              loading={timelineLoading}
              error={timelineError}
              onClose={() => setSelectedTradeId(null)}
            />
          )}
          {tab === "Overview" && (
            <>
              <section className="panel">
                <div className="panel-heading">
                  <div>
                    <h2>Recent activity</h2>
                    <p>Live service data, refreshed every 3 seconds</p>
                  </div>
                  <button
                    className="text-button"
                    onClick={() => setTab("Trade blotter")}
                  >
                    View blotter →
                  </button>
                </div>
                <TradeTable
                  trades={trades.slice(0, 5)}
                  onSelect={setSelectedTradeId}
                  selectedTradeId={selectedTradeId}
                />
              </section>
              <section className="panel settings">
                <h2>Portfolio management</h2>
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    void action(
                      () =>
                        api("portfolio", "/api/portfolios", "POST", {
                          name,
                          cash: Number(initialCash),
                        }),
                      "Portfolio created. Select it above.",
                    );
                  }}
                >
                  <label>
                    New portfolio name
                    <input
                      required
                      maxLength={100}
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                    />
                  </label>
                  <label>
                    Initial cash (USD)
                    <input
                      required
                      type="number"
                      min="0"
                      max="1000000000"
                      step="0.01"
                      value={initialCash}
                      onChange={(e) => setInitialCash(e.target.value)}
                    />
                  </label>
                  <button className="secondary" disabled={busy}>
                    <Plus size={16} /> Create portfolio
                  </button>
                </form>
                {portfolio && (
                  <div className="management">
                    <button
                      disabled={busy}
                      onClick={() =>
                        void action(
                          () =>
                            api(
                              "portfolio",
                              "/api/portfolios/" + selected,
                              "PATCH",
                              {
                                active: !portfolio.active,
                                version: portfolio.version,
                              },
                            ),
                          "Portfolio status updated.",
                        )
                      }
                    >
                      {portfolio.active ? "Deactivate" : "Activate"} selected
                      portfolio
                    </button>
                    <button
                      disabled={busy || !name.trim()}
                      onClick={() =>
                        void action(
                          () =>
                            api(
                              "portfolio",
                              "/api/portfolios/" + selected,
                              "PUT",
                              { name, version: portfolio.version },
                            ),
                          "Portfolio renamed.",
                        )
                      }
                    >
                      Rename using name above
                    </button>
                  </div>
                )}
              </section>
            </>
          )}
          {tab === "Risk studio" && (
            <>
              <div className="grid">
                <section className="panel">
                  <div className="panel-heading">
                    <div>
                      <h2>Order risk policies</h2>
                      <p>
                        All enabled policies must pass. No active policy means
                        rejection.
                      </p>
                    </div>
                  </div>
                  <div className="policy-list">
                    {data.policies.map((p) => (
                      <div className="policy" key={p.id}>
                        <ShieldCheck />
                        <div>
                          <strong>{p.name}</strong>
                          <p>Maximum order: {money(p.maxNotional)}</p>
                        </div>
                        <button
                          disabled={busy}
                          onClick={() =>
                            void action(
                              () =>
                                api("risk", "/api/policies/" + p.id, "PATCH", {
                                  enabled: !p.enabled,
                                  version: p.version,
                                }),
                              "Policy updated.",
                            )
                          }
                        >
                          {p.enabled ? "Disable" : "Enable"}
                        </button>
                      </div>
                    ))}
                  </div>
                  <form
                    className="inset"
                    onSubmit={(e) => {
                      e.preventDefault();
                      const p = data.policies.find((p) => p.id === "default");
                      if (p)
                        void action(
                          () =>
                            api("risk", "/api/policies/default", "PUT", {
                              name: p.name,
                              maxNotional: Number(limit),
                              version: p.version,
                            }),
                          "Default limit updated.",
                        );
                    }}
                  >
                    <label>
                      Default maximum notional (USD)
                      <input
                        required
                        type="number"
                        min="0.01"
                        max="1000000000000"
                        step="0.01"
                        value={limit}
                        onChange={(e) => setLimit(e.target.value)}
                      />
                    </label>
                    <button className="secondary" disabled={busy}>
                      Update default limit
                    </button>
                  </form>
                  <form
                    className="inset"
                    onSubmit={(e) => {
                      e.preventDefault();
                      void action(
                        () =>
                          api("risk", "/api/policies", "POST", {
                            name: newPolicyName,
                            maxNotional: Number(limit),
                            version: 0,
                          }),
                        "Additional policy created.",
                      );
                    }}
                  >
                    <label>
                      Additional policy name
                      <input
                        required
                        maxLength={100}
                        value={newPolicyName}
                        onChange={(e) => setNewPolicyName(e.target.value)}
                      />
                    </label>
                    <button disabled={busy}>Create with limit above</button>
                  </form>
                </section>
                <section className="panel ticket">
                  <div className="panel-heading">
                    <div>
                      <h2>Stress scenario</h2>
                      <p>Parallel shock on current book exposure</p>
                    </div>
                  </div>
                  <form
                    onSubmit={(e) => {
                      e.preventDefault();
                      void action(
                        async () =>
                          setStress(
                            await api("risk", "/api/stress", "POST", {
                              exposure: cost,
                              shockPercent: Number(shock),
                            }),
                          ),
                        "Scenario calculated.",
                      );
                    }}
                  >
                    <label>
                      Market shock (%)
                      <input
                        required
                        type="number"
                        min="-100"
                        max="100"
                        step="0.1"
                        value={shock}
                        onChange={(e) => setShock(e.target.value)}
                      />
                    </label>
                    <button className="primary" disabled={busy}>
                      Run scenario
                    </button>
                    {stress && (
                      <div className="stress-result">
                        <small>HYPOTHETICAL P&L</small>
                        <strong
                          className={stress.pnl < 0 ? "negative" : "positive"}
                        >
                          {money(stress.pnl)}
                        </strong>
                        <p>
                          Stressed securities value{" "}
                          {money(stress.stressedValue)}
                        </p>
                      </div>
                    )}
                    <p className="fine">
                      A deterministic sensitivity exercise. This is not VaR, a
                      forecast, or a market data model.
                    </p>
                  </form>
                </section>
              </div>
              <section className="panel">
                <div className="panel-heading">
                  <h2>Risk decision audit</h2>
                </div>
                <table>
                  <thead>
                    <tr>
                      <th>Trade</th>
                      <th>Notional</th>
                      <th>Decision</th>
                      <th>Reason</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.decisions
                      .filter((d) => d.portfolioId === selected)
                      .map((d) => (
                        <tr key={d.id}>
                          <td>
                            <code>{d.tradeId.slice(0, 8)}</code>
                          </td>
                          <td>{money(d.notional)}</td>
                          <td>{status(d.status)}</td>
                          <td>{d.reason}</td>
                        </tr>
                      ))}
                  </tbody>
                </table>
                {!data.decisions.length && (
                  <Empty text="Decisions appear after you submit a trade." />
                )}
              </section>
            </>
          )}
          {tab === "Accounting" && (
            <>
              <section className="panel">
                <div className="panel-heading">
                  <div>
                    <h2>Trial balance</h2>
                    <p>
                      Workspace-wide transaction clearing • USD • excludes
                      opening capital
                    </p>
                  </div>
                  <span className="pill">Double-entry</span>
                </div>
                <table>
                  <thead>
                    <tr>
                      <th>Account</th>
                      <th>Debit</th>
                      <th>Credit</th>
                      <th>Net debit</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.balances.map((b) => (
                      <tr key={b.accountId}>
                        <td>{b.name}</td>
                        <td>{money(b.debit)}</td>
                        <td>{money(b.credit)}</td>
                        <td>{money(b.debit - b.credit)}</td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr>
                      <td>Total</td>
                      <td>
                        {money(data.balances.reduce((s, b) => s + b.debit, 0))}
                      </td>
                      <td>
                        {money(data.balances.reduce((s, b) => s + b.credit, 0))}
                      </td>
                      <td>
                        {money(
                          data.balances.reduce(
                            (s, b) => s + b.debit - b.credit,
                            0,
                          ),
                        )}
                      </td>
                    </tr>
                  </tfoot>
                </table>
              </section>
              <section className="panel">
                <div className="panel-heading">
                  <div>
                    <h2>Journal entries</h2>
                    <p>
                      Immutable execution postings for the selected portfolio
                    </p>
                  </div>
                </div>
                {data.journals
                  .filter((j) => j.portfolioId === selected)
                  .map((j) => (
                    <div className="journal" key={j.id}>
                      <div>
                        <strong>{j.description}</strong>
                        <small>Trade {j.tradeId}</small>
                      </div>
                      <div>
                        {j.lines.map((l, i) => (
                          <div className="journal-line" key={i}>
                            <span>{l.accountId}</span>
                            <span>
                              {l.debit
                                ? "Dr " + money(l.debit)
                                : "Cr " + money(l.credit)}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                {!data.journals.length && (
                  <Empty text="Executed trades generate journals automatically." />
                )}
              </section>
              <section className="panel settings">
                <h2>Chart of accounts</h2>
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    void action(
                      () =>
                        api("accounting", "/api/accounts", "POST", {
                          name: accountName,
                          version: 0,
                        }),
                      "Account created.",
                    );
                  }}
                >
                  <label>
                    Account name
                    <input
                      required
                      maxLength={100}
                      value={accountName}
                      onChange={(e) => setAccountName(e.target.value)}
                    />
                  </label>
                  <button className="secondary" disabled={busy}>
                    Create account
                  </button>
                </form>
                <p className="fine">
                  Custom accounts are available for future posting workflows;
                  the execution flow uses protected system clearing accounts.
                </p>
              </section>
            </>
          )}
          <div className="bottom-note">
            <Layers3 size={14} /> ATLAS / Local investment simulation{" "}
            <span>REST + GraphQL · Kafka events · Oracle</span>
          </div>
        </div>
      </main>
    </div>
  );
}
function Metric({
  label,
  value,
  detail,
  icon,
}: {
  label: string;
  value: string;
  detail: string;
  icon: React.ReactNode;
}) {
  return (
    <div className="metric">
      <div>
        <span>{label}</span>
        {icon}
      </div>
      <strong>{value}</strong>
      <p>{detail}</p>
    </div>
  );
}
function Empty({ text }: { text: string }) {
  return (
    <div className="empty">
      <BriefcaseBusiness size={28} />
      <p>{text}</p>
    </div>
  );
}
function TradeTable({
  trades,
  onSelect,
  selectedTradeId,
}: {
  trades: Trade[];
  onSelect?: (tradeId: string) => void;
  selectedTradeId?: string | null;
}) {
  return (
    <>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Instrument</th>
              <th>Side</th>
              <th>Quantity</th>
              <th>Price</th>
              <th>Status</th>
              <th>Detail</th>
            </tr>
          </thead>
          <tbody>
            {trades.map((t) => (
              <tr
                key={t.id}
                className={selectedTradeId === t.id ? "selected-row" : ""}
              >
                <td>
                  <strong>{t.symbol}</strong>
                  <small>{t.id.slice(0, 8)}</small>
                </td>
                <td className={t.side === "BUY" ? "positive" : "negative"}>
                  {t.side}
                </td>
                <td>{t.quantity}</td>
                <td>{money(t.price)}</td>
                <td>{status(t.status)}</td>
                <td className="reason">
                  {t.reason || "Waiting for risk"}
                  {onSelect && (
                    <button
                      className="timeline-link"
                      onClick={() => onSelect(t.id)}
                      aria-label={"View timeline for " + t.symbol}
                    >
                      View timeline
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!trades.length && (
        <Empty text="No orders yet. Use the order ticket to start the event flow." />
      )}
    </>
  );
}

function TradeTimelinePanel({
  trade,
  timeline,
  loading,
  error,
  onClose,
}: {
  trade?: Trade;
  timeline: TradeTimeline | null;
  loading: boolean;
  error: string;
  onClose: () => void;
}) {
  return (
    <section className="panel timeline-panel" aria-label="Trade timeline">
      <div className="panel-heading">
        <div>
          <h2>
            {trade?.symbol ?? "Trade"} lifecycle
            {trade && <span className="timeline-trade-id"> · {trade.id.slice(0, 8)}</span>}
          </h2>
          <p>
            {trade
              ? `${trade.side} ${trade.quantity} ${trade.symbol} at ${money(trade.price)}`
              : "Selected trade"}
          </p>
        </div>
        <div className="timeline-header-actions">
          {trade && status(trade.status)}
          <button className="text-button" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
      {loading && <div className="empty">Loading trade lifecycle…</div>}
      {error && <div className="alert timeline-alert">{error}</div>}
      {timeline && !loading && (
        <div className="timeline">
          {timeline.steps.map((step) => (
            <div className="timeline-step" key={step.key}>
              <div className={"timeline-marker " + step.state.toLowerCase().replaceAll(" ", "-")}>
                {step.state === "Completed" ? "✓" : step.state === "Failed" ? "!" : "•"}
              </div>
              <div className="timeline-content">
                <div className="timeline-step-heading">
                  <strong>{step.label}</strong>
                  <span className={"timeline-state " + step.state.toLowerCase().replaceAll(" ", "-")}>
                    {step.state}
                  </span>
                </div>
                <small>
                  {step.timestamp
                    ? new Date(step.timestamp).toLocaleString()
                    : "No event recorded yet"}
                </small>
                {step.detail && <p>{step.detail}</p>}
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
