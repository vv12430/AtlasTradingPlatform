package com.atlas.risk;

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
public class RiskService {

  public record Policy(
    String id,
    String name,
    BigDecimal maxNotional,
    boolean enabled,
    int version
  ) {}

  public record PolicyInput(
    @NotBlank @Size(max = 100) String name,
    @NotNull
    @DecimalMin("0.01")
    @DecimalMax("1000000000000")
    @Digits(integer = 13, fraction = 2)
    BigDecimal maxNotional,
    @Min(0) int version
  ) {}

  public record Toggle(@NotNull Boolean enabled, @Min(0) int version) {}

  public record Decision(
    String id,
    String tradeId,
    String portfolioId,
    BigDecimal notional,
    String status,
    String reason
  ) {}

  public record StressInput(
    @NotNull @DecimalMin("0") @DecimalMax("1000000000000") BigDecimal exposure,
    @NotNull @DecimalMin("-100") @DecimalMax("100") BigDecimal shockPercent
  ) {}

  public record StressResult(BigDecimal pnl, BigDecimal stressedValue) {}

  private final JdbcTemplate db;
  private final Events events;

  public RiskService(JdbcTemplate db, Events events) {
    this.db = db;
    this.events = events;
  }

  private final RowMapper<Policy> mapper = (r, n) ->
    new Policy(
      r.getString("id"),
      r.getString("name"),
      r.getBigDecimal("max_notional"),
      r.getInt("enabled") == 1,
      r.getInt("version")
    );

  public List<Policy> policies() {
    return db.query("select * from policies order by name", mapper);
  }

  public Policy policy(String id) {
    return db.queryForObject("select * from policies where id=?", mapper, id);
  }

  @Transactional
  public Policy create(@Valid PolicyInput input) {
    String id = UUID.randomUUID().toString();
    db.update(
      "insert into policies(id,name,max_notional) values(?,?,?)",
      id,
      input.name(),
      input.maxNotional()
    );
    return policy(id);
  }

  @Transactional
  public Policy update(String id, @Valid PolicyInput input) {
    if (
      db.update(
        "update policies set name=?,max_notional=?,version=version+1 where id=? and version=?",
        input.name(),
        input.maxNotional(),
        id,
        input.version()
      ) != 1
    ) throw new IllegalStateException("Policy changed; refresh");
    return policy(id);
  }

  @Transactional
  public Policy toggle(String id, @Valid Toggle input) {
    if (
      db.update(
        "update policies set enabled=?,version=version+1 where id=? and version=?",
        input.enabled() ? 1 : 0,
        id,
        input.version()
      ) != 1
    ) throw new IllegalStateException("Policy changed; refresh");
    return policy(id);
  }

  @Transactional
  public void delete(String id) {
    var p = policy(id);
    if (p.enabled()) throw new IllegalStateException(
      "Disable policy before deleting"
    );
    db.update("delete from policies where id=?", id);
  }

  public List<Decision> decisions() {
    return db.query(
      "select * from decisions order by created_at desc fetch first 200 rows only",
      (r, n) ->
        new Decision(
          r.getString("id"),
          r.getString("trade_id"),
          r.getString("portfolio_id"),
          r.getBigDecimal("notional"),
          r.getString("status"),
          r.getString("reason")
        )
    );
  }

  public StressResult stress(@Valid StressInput input) {
    var pnl = RiskMath.stress(input.exposure(), input.shockPercent());
    return new StressResult(pnl, input.exposure().add(pnl));
  }

  @Transactional
  public void assess(TradeEvent event) {
    if (!events.first(event)) return;
    if (
      db.queryForObject(
        "select count(*) from decisions where trade_id=?",
        Integer.class,
        event.tradeId()
      ) > 0
    ) return;
    var active = policies().stream().filter(Policy::enabled).toList();
    boolean allowed =
      !active.isEmpty() &&
      active
        .stream()
        .allMatch(p -> RiskMath.allowed(event.notional(), p.maxNotional()));
    String status = allowed ? "APPROVED" : "REJECTED",
      reason = allowed
        ? "All active order limits passed"
        : active.isEmpty()
          ? "No active policy; fail closed"
          : "Order exceeds limit or rounds to zero";
    db.update(
      "insert into decisions(id,trade_id,portfolio_id,notional,status,reason) values(?,?,?,?,?,?)",
      UUID.randomUUID().toString(),
      event.tradeId(),
      event.portfolioId(),
      event.notional(),
      status,
      reason
    );
    events.emit("risk.assessed.v1", event.next(status, reason));
  }
}
