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
      r.getString("reason")
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
      "insert into trades(id,client_key,portfolio_id,symbol,side,quantity,price,status) values(?,?,?,?,?,?,?,?)",
      id,
      input.clientKey(),
      input.portfolioId(),
      input.symbol(),
      input.side(),
      input.quantity(),
      input.price(),
      "PENDING_RISK"
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
        "Awaiting risk assessment"
      )
    );
    return trade(id);
  }

  @Transactional
  public void assessed(TradeEvent event) {
    var p = db.queryForObject(
      "select * from portfolios where id=? for update",
      portfolio,
      event.portfolioId()
    );
    if (!events.first(event)) return;
    var t = trade(event.tradeId());
    if (!t.status().equals("PENDING_RISK")) return;
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
    BigDecimal amount = event.notional();
    if (amount.signum() <= 0) {
      reject(t.id(), "Notional rounds to zero cents");
      return;
    }
    if (buy && p.cash().compareTo(amount) < 0) {
      reject(t.id(), "Insufficient cash");
      return;
    }
    if (!buy && old.quantity().compareTo(t.quantity()) < 0) {
      reject(t.id(), "Short selling is disabled");
      return;
    }
    BigDecimal quantity = buy
      ? old.quantity().add(t.quantity())
      : old.quantity().subtract(t.quantity());
    BigDecimal removed = buy
      ? BigDecimal.ZERO
      : old
          .cost()
          .multiply(t.quantity())
          .divide(old.quantity(), 2, RoundingMode.HALF_EVEN);
    BigDecimal cost = buy
      ? old.cost().add(amount)
      : old.cost().subtract(removed);
    BigDecimal pnl = buy
      ? old.realizedPnl()
      : old.realizedPnl().add(amount.subtract(removed));
    if (found.isEmpty()) db.update(
      "insert into positions(portfolio_id,symbol,quantity,cost,realized_pnl) values(?,?,?,?,?)",
      p.id(),
      t.symbol(),
      quantity,
      cost,
      pnl
    );
    else db.update(
      "update positions set quantity=?,cost=?,realized_pnl=? where portfolio_id=? and symbol=?",
      quantity,
      cost,
      pnl,
      p.id(),
      t.symbol()
    );
    db.update(
      "update portfolios set cash=?,version=version+1 where id=?",
      buy ? p.cash().subtract(amount) : p.cash().add(amount),
      p.id()
    );
    db.update(
      "update trades set status='EXECUTED',reason='Simulated fill at submitted price' where id=?",
      t.id()
    );
    events.emit(
      "trade.executed.v1",
      event.next("EXECUTED", "Simulated execution")
    );
  }

  private void reject(String id, String reason) {
    db.update(
      "update trades set status='REJECTED',reason=? where id=?",
      reason,
      id
    );
  }
}
