package com.atlas.portfolio;

import static com.atlas.portfolio.Models.*;

import com.atlas.common.*;
import jakarta.validation.Valid;
import java.math.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class PortfolioService {

  private final JdbcTemplate db;
  private final Events events;

  public PortfolioService(JdbcTemplate db, Events events) {
    this.db = db;
    this.events = events;
  }

  private final RowMapper<Portfolio> portfolio = (r, n) ->
    new Portfolio(
      r.getString("id"),
      r.getString("name"),
      r.getBigDecimal("cash"),
      r.getInt("active") == 1,
      r.getInt("version")
    );
  private final RowMapper<Trade> trade = (r, n) ->
    new Trade(
      r.getString("id"),
      r.getString("portfolio_id"),
      r.getString("symbol"),
      r.getString("side"),
      r.getBigDecimal("quantity"),
      r.getBigDecimal("price"),
      r.getString("status"),
      r.getString("reason"),
      r.getBigDecimal("filled_quantity"),
      r.getBigDecimal("remaining_quantity"),
      r.getBigDecimal("fees")
    );

  public List<Portfolio> portfolios() {
    return db.query("select * from portfolios order by name", portfolio);
  }

  public Portfolio get(String id) {
    return db.queryForObject(
      "select * from portfolios where id=?",
      portfolio,
      id
    );
  }

  @Transactional
  public Portfolio create(@Valid CreatePortfolio input) {
    String id = UUID.randomUUID().toString();
    db.update(
      "insert into portfolios(id,name,cash) values(?,?,?)",
      id,
      input.name(),
      input.cash()
    );
    return get(id);
  }

  @Transactional
  public Portfolio rename(String id, @Valid Rename input) {
    if (
      db.update(
        "update portfolios set name=?,version=version+1 where id=? and version=?",
        input.name(),
        id,
        input.version()
      ) != 1
    ) throw new IllegalStateException(
      "Portfolio changed; refresh before editing"
    );
    return get(id);
  }

  @Transactional
  public Portfolio active(String id, @Valid Active input) {
    if (
      db.update(
        "update portfolios set active=?,version=version+1 where id=? and version=?",
        input.active() ? 1 : 0,
        id,
        input.version()
      ) != 1
    ) throw new IllegalStateException(
      "Portfolio changed; refresh before editing"
    );
    return get(id);
  }

  @Transactional
  public void delete(String id) {
    get(id);
    if (
      db.queryForObject(
        "select count(*) from trades where portfolio_id=?",
        Integer.class,
        id
      ) > 0
    ) throw new IllegalStateException(
      "A portfolio with trade history cannot be deleted; deactivate it"
    );
    db.update("delete from portfolios where id=?", id);
  }

  public List<Trade> trades() {
    return db.query(
      "select * from trades order by created_at desc fetch first 200 rows only",
      trade
    );
  }

  public Trade trade(String id) {
    return db.queryForObject("select * from trades where id=?", trade, id);
  }

  public List<Position> positions(String id) {
    get(id);
    return db.query(
      "select * from positions where portfolio_id=? order by symbol",
      (r, n) ->
        new Position(
          r.getString("symbol"),
          r.getBigDecimal("quantity"),
          r.getBigDecimal("cost"),
          r.getBigDecimal("realized_pnl")
        ),
      id
    );
  }

  /**
   * Submits a new trade for risk assessment, handling both concurrency
   * safety and idempotent retries.
   *
   * Locks the target portfolio row for the duration of the transaction
   * so concurrent submissions against the same portfolio are serialized
   * rather than racing. Uses the caller-supplied client key as an
   * idempotency key: if a trade with the same key already exists and
   * matches this request exactly, the existing trade is returned as-is
   * (a safe retry); if it exists but differs in any material field, the
   * request is rejected as a key reuse conflict. New trades are rejected
   * if the portfolio is inactive.
   *
   * On success, persists the trade with status PENDING_RISK and emits a
   * "trade.submitted.v1" event (via the transactional outbox) to trigger
   * downstream risk policy evaluation.
   *
   * For example, submitting a BUY of 100 AAPL @ $150 with client key
   * "client-abc-001" creates a new PENDING_RISK trade and emits an
   * event. Retrying the identical request with the same client key
   * returns the same trade without creating a duplicate; retrying with
   * the same key but quantity 200 instead throws IllegalStateException.
   */

  @Transactional
  public Trade submit(@Valid TradeRequest input) {
    // Lock the aggregate to serialize submissions and execution for this portfolio.
    var p = db.queryForObject(
      "select * from portfolios where id=? for update",
      portfolio,
      input.portfolioId()
    );
    var existing = db.query(
      "select * from trades where client_key=?",
      trade,
      input.clientKey()
    );
    if (!existing.isEmpty()) {
      var t = existing.getFirst();
      if (
        !t.portfolioId().equals(input.portfolioId()) ||
        !t.symbol().equals(input.symbol()) ||
        !t.side().equals(input.side()) ||
        t.quantity().compareTo(input.quantity()) != 0 ||
        t.price().compareTo(input.price()) != 0
      ) throw new IllegalStateException(
        "Idempotency key reused with different trade"
      );
      return t;
    }
    if (!p.active()) throw new IllegalStateException("Portfolio is inactive");
    String id = UUID.randomUUID().toString();
    db.update(
      "insert into trades(id,client_key,portfolio_id,symbol,side,quantity,price,status,filled_quantity,remaining_quantity,fees) values(?,?,?,?,?,?,?,?,?,?,?)",
      id,
      input.clientKey(),
      input.portfolioId(),
      input.symbol(),
      input.side(),
      input.quantity(),
      input.price(),
      "PENDING_RISK",
      BigDecimal.ZERO,
      input.quantity(),
      BigDecimal.ZERO
    );
    events.emit(
      "trade.submitted.v1",
      new TradeEvent(
        UUID.randomUUID().toString(),
        1,
        id,
        input.portfolioId(),
        input.symbol(),
        input.side(),
        input.quantity(),
        input.price(),
        "PENDING_RISK",
        "Awaiting risk assessment",
        BigDecimal.ZERO,
        BigDecimal.ZERO
      )
    );
    return trade(id);
  }

  /**
   * Handles the outcome of risk assessment for a trade and, if approved,
   * simulates its execution against the portfolio.
   *
   * A REJECTED risk outcome simply marks the trade rejected with the given reason.
   * An APPROVED outcome is executed only if the portfolio is still active, the notional is
   * non-zero, there is sufficient cash for a BUY, and the position has enough quantity
   * to cover a SELL (short selling is disabled).
   *
   * On execution, updates the position using average-cost accounting:
   * a BUY increases quantity and cost basis with no realized P&L; a SELL decreases quantity,
   * removes a proportional slice of the cost basis, and realizes P&L as the difference between
   * the sale notional and that removed cost. Persists the updated position and portfolio cash,
   * marks the trade EXECUTED or PARTIALLY_FILLED, and emits a "trade.executed.v1" event.
   *
   * Supports partial fills via fillQuantity in the event. When fillQuantity is provided,
   * only that portion is executed. The trade status becomes PARTIALLY_FILLED if some but
   * not all quantity is filled, or FILLED when the entire order is complete.
   *
   * For example, buying 100 shares for a $15,000 notional against a portfolio with no
   * existing position creates a position with quantity 100, cost 15,000, and realized P&L 0,
   * and reduces cash by $15,000. A partial fill of 50 shares would update the position
   * to quantity 50, cost 7,500, and leave the trade in PARTIALLY_FILLED status.
   */
  @Transactional
  public void assessed(TradeEvent event) {
    var p = db.queryForObject(
      "select * from portfolios where id=? for update",
      portfolio,
      event.portfolioId()
    );
    if (!events.first(event)) return;
    var t = trade(event.tradeId());
    
    // Allow processing of APPROVED events for PARTIALLY_FILLED trades
    if (!t.status().equals("PENDING_RISK") && !t.status().equals("PARTIALLY_FILLED")) return;
    
    if (
      !t.portfolioId().equals(event.portfolioId()) ||
      !t.symbol().equals(event.symbol()) ||
      !t.side().equals(event.side()) ||
      t.quantity().compareTo(event.quantity()) != 0 ||
      t.price().compareTo(event.price()) != 0
    ) throw new IllegalArgumentException("Event does not match trade");
    
    if (!event.status().equals("APPROVED")) {
      if (
        !event.status().equals("REJECTED")
      ) throw new IllegalArgumentException("Invalid risk outcome");
      reject(t.id(), event.reason());
      return;
    }
    if (!p.active()) {
      reject(t.id(), "Portfolio became inactive");
      return;
    }
    
    // Determine fill quantity: use event.fillQuantity if provided, otherwise full quantity
    BigDecimal fillQty = event.fillQuantity() != null && event.fillQuantity().compareTo(BigDecimal.ZERO) > 0
      ? event.fillQuantity()
      : t.quantity();
    
    // For partial fills, ensure we don't exceed remaining quantity
    if (t.status().equals("PARTIALLY_FILLED")) {
      if (fillQty.compareTo(t.remainingQuantity()) > 0) {
        reject(t.id(), "Fill quantity exceeds remaining order quantity");
        return;
      }
    }
    
    var found = positions(p.id())
      .stream()
      .filter(x -> x.symbol().equals(t.symbol()))
      .findFirst();
    var old = found.orElse(
      new Position(
        t.symbol(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO
      )
    );
    boolean buy = t.side().equals("BUY");
    BigDecimal amount = fillQty.multiply(t.price()).setScale(2, RoundingMode.HALF_EVEN);
    BigDecimal fees = event.fees() != null ? event.fees() : BigDecimal.ZERO;
    
    if (amount.signum() <= 0) {
      reject(t.id(), "Notional rounds to zero cents");
      return;
    }
    BigDecimal totalCost = amount.add(fees);
    
    if (buy && p.cash().compareTo(totalCost) < 0) {
      reject(t.id(), "Insufficient cash including fees");
      return;
    }
    if (!buy && old.quantity().compareTo(fillQty) < 0) {
      reject(t.id(), "Short selling is disabled");
      return;
    }
    
    BigDecimal newQuantity = buy
      ? old.quantity().add(fillQty)
      : old.quantity().subtract(fillQty);
    BigDecimal removed = buy
      ? BigDecimal.ZERO
      : old
          .cost()
          .multiply(fillQty)
          .divide(old.quantity(), 2, RoundingMode.HALF_EVEN);
    BigDecimal newCost = buy
      ? old.cost().add(amount)  // Fees are separate from cost basis
      : old.cost().subtract(removed);
    BigDecimal pnl = buy
      ? old.realizedPnl()
      : old.realizedPnl().add(amount.subtract(removed).subtract(fees));
    
    if (found.isEmpty()) db.update(
      "insert into positions(portfolio_id,symbol,quantity,cost,realized_pnl) values(?,?,?,?,?)",
      p.id(),
      t.symbol(),
      newQuantity,
      newCost,
      pnl
    );
    else db.update(
      "update positions set quantity=?,cost=?,realized_pnl=? where portfolio_id=? and symbol=?",
      newQuantity,
      newCost,
      pnl,
      p.id(),
      t.symbol()
    );
    db.update(
      "update portfolios set cash=?,version=version+1 where id=?",
      buy ? p.cash().subtract(totalCost) : p.cash().add(amount.subtract(fees)),
      p.id()
    );
    
    // Update trade state based on fill
    BigDecimal newFilledQty = t.filledQuantity().add(fillQty);
    BigDecimal newRemainingQty = t.quantity().subtract(newFilledQty);
    String newStatus = newRemainingQty.compareTo(BigDecimal.ZERO) == 0 ? "FILLED" : "PARTIALLY_FILLED";
    
    db.update(
      "update trades set status=?,filled_quantity=?,remaining_quantity=?,fees=?,reason=? where id=?",
      newStatus,
      newFilledQty,
      newRemainingQty,
      t.fees().add(fees),
      newStatus.equals("FILLED") ? "Order fully filled" : "Partial fill executed",
      t.id()
    );
    
    events.emit(
      "trade.executed.v1",
      event.withFill(fillQty, fees).next(newStatus, newStatus.equals("FILLED") ? "Order fully filled" : "Partial fill executed")
    );
  }

  private void reject(String id, String reason) {
    db.update(
      "update trades set status='REJECTED',reason=? where id=?",
      reason,
      id
    );
  }

  /**
   * Cancels a trade that is in PENDING_RISK or PARTIALLY_FILLED status.
   *
   * Only trades that have not been fully filled can be cancelled. The trade
   * must be in PENDING_RISK or PARTIALLY_FILLED state. Once cancelled, the
   * trade cannot be executed further.
   *
   * For example, a trade for 100 shares that has been partially filled for
   * 30 shares can be cancelled, leaving the remaining 70 shares unfilled.
   * The 30 filled shares remain in the position.
   */
  @Transactional
  public void cancel(@Valid CancelRequest input) {
    var t = trade(input.tradeId());
    if (!t.status().equals("PENDING_RISK") && !t.status().equals("PARTIALLY_FILLED")) {
      throw new IllegalStateException(
        "Only PENDING_RISK or PARTIALLY_FILLED trades can be cancelled"
      );
    }
    
    var p = db.queryForObject(
      "select * from portfolios where id=? for update",
      portfolio,
      t.portfolioId()
    );
    
    db.update(
      "update trades set status='CANCELLED',cancellation_reason=?,cancelled_at=current_timestamp where id=?",
      input.reason(),
      t.id()
    );
    
    // Emit cancellation event for downstream services
    events.emit(
      "trade.cancelled.v1",
      new TradeEvent(
        UUID.randomUUID().toString(),
        1,
        t.id(),
        t.portfolioId(),
        t.symbol(),
        t.side(),
        t.quantity(),
        t.price(),
        "CANCELLED",
        input.reason(),
        t.filledQuantity(),
        t.fees()
      )
    );
  }

  /**
   * Modifies the quantity and price of a trade that is in PENDING_RISK status.
   *
   * Only trades that have not yet been approved or filled can be modified.
   * The trade must be in PENDING_RISK state. After modification, the trade
   * is resubmitted for risk assessment with the new parameters.
   *
   * For example, a trade for 100 shares at $150 can be modified to 80 shares
   * at $155. The trade remains in PENDING_RISK status and a new risk
   * assessment event is emitted.
   */
  @Transactional
  public Trade modify(@Valid ModifyRequest input) {
    var t = trade(input.tradeId());
    if (!t.status().equals("PENDING_RISK")) {
      throw new IllegalStateException(
        "Only PENDING_RISK trades can be modified"
      );
    }
    
    var p = db.queryForObject(
      "select * from portfolios where id=? for update",
      portfolio,
      t.portfolioId()
    );
    
    // Update trade with new quantity and price
    db.update(
      "update trades set quantity=?,price=?,remaining_quantity=? where id=?",
      input.quantity(),
      input.price(),
      input.quantity(),
      t.id()
    );
    
    // Emit new submission event for risk reassessment
    events.emit(
      "trade.submitted.v1",
      new TradeEvent(
        UUID.randomUUID().toString(),
        1,
        t.id(),
        t.portfolioId(),
        t.symbol(),
        t.side(),
        input.quantity(),
        input.price(),
        "PENDING_RISK",
        "Order modified and resubmitted",
        BigDecimal.ZERO,
        BigDecimal.ZERO
      )
    );
    
    return trade(t.id());
  }
}
