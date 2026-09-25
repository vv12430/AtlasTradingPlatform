package com.atlas.accounting;

import com.atlas.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class AccountingService {

  public record Account(String id, String name, boolean enabled, int version) {}

  public record AccountInput(
    @NotBlank @Size(max = 100) String name,
    @Min(0) int version
  ) {}

  public record Toggle(@NotNull Boolean enabled, @Min(0) int version) {}

  public record Journal(
    String id,
    String tradeId,
    String portfolioId,
    String description,
    List<Ledger.Line> lines
  ) {}

  public record Balance(
    String accountId,
    String name,
    BigDecimal debit,
    BigDecimal credit
  ) {}

  private final JdbcTemplate db;
  private final Events events;

  public AccountingService(JdbcTemplate db, Events events) {
    this.db = db;
    this.events = events;
  }

  private final RowMapper<Account> mapper = (r, n) ->
    new Account(
      r.getString("id"),
      r.getString("name"),
      r.getInt("enabled") == 1,
      r.getInt("version")
    );

  public List<Account> accounts() {
    return db.query("select * from accounts order by name", mapper);
  }

  public Account account(String id) {
    return db.queryForObject("select * from accounts where id=?", mapper, id);
  }

  @Transactional
  public Account create(@Valid AccountInput input) {
    String id = UUID.randomUUID().toString();
    db.update("insert into accounts(id,name) values(?,?)", id, input.name());
    return account(id);
  }

  @Transactional
  public Account update(String id, @Valid AccountInput input) {
    if (
      db.update(
        "update accounts set name=?,version=version+1 where id=? and version=?",
        input.name(),
        id,
        input.version()
      ) != 1
    ) throw new IllegalStateException("Account changed; refresh");
    return account(id);
  }

  @Transactional
  public Account toggle(String id, @Valid Toggle input) {
    if (
      Set.of("cash", "securities").contains(id)
    ) throw new IllegalStateException(
      "System clearing accounts must remain enabled"
    );
    if (
      db.update(
        "update accounts set enabled=?,version=version+1 where id=? and version=?",
        input.enabled() ? 1 : 0,
        id,
        input.version()
      ) != 1
    ) throw new IllegalStateException("Account changed; refresh");
    return account(id);
  }

  @Transactional
  public void delete(String id) {
    account(id);
    if (
      Set.of("cash", "securities").contains(id)
    ) throw new IllegalStateException("Cannot delete system accounts");
    db.update("delete from accounts where id=?", id);
  }

  public List<Journal> journals() {
    return db.query(
      "select * from journals order by created_at desc fetch first 200 rows only",
      (r, n) ->
        new Journal(
          r.getString("id"),
          r.getString("trade_id"),
          r.getString("portfolio_id"),
          r.getString("description"),
          lines(r.getString("id"))
        )
    );
  }

  private List<Ledger.Line> lines(String id) {
    return db.query(
      "select * from journal_lines where journal_id=? order by account_id",
      (r, n) ->
        new Ledger.Line(
          r.getString("account_id"),
          r.getBigDecimal("debit"),
          r.getBigDecimal("credit")
        ),
      id
    );
  }

  public List<Balance> trialBalance() {
    return db.query(
      "select a.id,a.name,coalesce(sum(l.debit),0) debit,coalesce(sum(l.credit),0) credit from accounts a left join journal_lines l on a.id=l.account_id group by a.id,a.name order by a.id",
      (r, n) ->
        new Balance(
          r.getString("id"),
          r.getString("name"),
          r.getBigDecimal("debit"),
          r.getBigDecimal("credit")
        )
    );
  }

  /**
   * Posts an executed trade to the accounting ledger as a balanced
   * double-entry journal.
   *
   * Only accepts events with status EXECUTED or FILLED, and is idempotent: it
   * skips duplicate event deliveries and skips trades that already have
   * a journal entry recorded, so a trade is never posted twice. Builds
   * the debit/credit lines for the trade (based on side and notional)
   * and refuses to post if those lines don't balance, since every
   * journal entry must have total debits equal to total credits.
   *
   * Supports partial fills via fillQuantity. When fillQuantity is provided,
   * only that portion is posted. Fees are posted to a separate fees account
   * if present.
   *
   * Persists a journal header describing the trade, followed by one
   * journal_lines row per debit/credit entry.
   *
   * For example, posting an EXECUTED BUY of 100 AAPL with a $15,000
   * notional creates a journal entry debiting an Investments account
   * $15,000 and crediting Cash $15,000, keeping the ledger balanced.
   * With $50 in fees, it debits Investments $15,000, debits Fees $50,
   * and credits Cash $15,050.
   */
  @Transactional
  public void post(TradeEvent event) {
    if (!event.status().equals("EXECUTED") && !event.status().equals("FILLED")) {
      throw new IllegalArgumentException(
        "Only executed or filled trades may be posted"
      );
    }
    if (!events.first(event)) return;
    if (
      db.queryForObject(
        "select count(*) from journals where trade_id=?",
        Integer.class,
        event.tradeId()
      ) > 0
    ) return;
    
    // Use fillQuantity if provided, otherwise use full quantity
    BigDecimal qty = event.fillQuantity() != null && event.fillQuantity().compareTo(BigDecimal.ZERO) > 0
      ? event.fillQuantity()
      : event.quantity();
    BigDecimal notional = qty.multiply(event.price()).setScale(2, java.math.RoundingMode.HALF_EVEN);
    BigDecimal fees = event.fees() != null ? event.fees() : BigDecimal.ZERO;
    
    var lines = Ledger.lines(event.side(), notional, fees);
    if (!Ledger.balanced(lines)) throw new IllegalStateException(
      "Unbalanced journal"
    );
    String id = UUID.randomUUID().toString();
    String description = event.side() + " " + qty + " " + event.symbol() + 
      (fees.compareTo(BigDecimal.ZERO) > 0 ? " (fees: " + fees + ")" : "");
    
    db.update(
      "insert into journals(id,trade_id,portfolio_id,description) values(?,?,?,?)",
      id,
      event.tradeId(),
      event.portfolioId(),
      description
    );
    for (var line : lines)
      db.update(
        "insert into journal_lines(id,journal_id,account_id,debit,credit) values(?,?,?,?,?)",
        UUID.randomUUID().toString(),
        id,
        line.accountId(),
        line.debit(),
        line.credit()
      );
    events.emit("trade.accounted.v1", event.next("ACCOUNTED", "Journal posted"));
  }
}
