package com.atlas.portfolio;

import static com.atlas.portfolio.Models.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.atlas.common.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortfolioIT {

  @Autowired
  PortfolioService service;

  @Autowired
  JdbcTemplate db;

  @Autowired
  MockMvc mvc;

  private TradeRequest request(
    String p,
    String side,
    String quantity,
    String price
  ) {
    return new TradeRequest(
      UUID.randomUUID().toString(),
      p,
      "AAPL",
      side,
      new BigDecimal(quantity),
      new BigDecimal(price)
    );
  }

  private TradeEvent approved(Trade t) {
    return new TradeEvent(
      UUID.randomUUID().toString(),
      1,
      t.id(),
      t.portfolioId(),
      t.symbol(),
      t.side(),
      t.quantity(),
      t.price(),
      "APPROVED",
      "Test approval"
    );
  }

  @Test
  void buyThenSellMaintainsCashCostAndRealizedPnl() {
    var p = service.create(new CreatePortfolio("Test", new BigDecimal("1000")));
    var buy = service.submit(request(p.id(), "BUY", "4", "100"));
    service.assessed(approved(buy));
    var sell = service.submit(request(p.id(), "SELL", "2", "120"));
    service.assessed(approved(sell));
    assertThat(service.get(p.id()).cash()).isEqualByComparingTo("840");
    var pos = service.positions(p.id()).getFirst();
    assertThat(pos.quantity()).isEqualByComparingTo("2");
    assertThat(pos.cost()).isEqualByComparingTo("200");
    assertThat(pos.realizedPnl()).isEqualByComparingTo("40");
  }

  @Test
  void submissionAndEventsAreIdempotent() {
    var p = service.create(
      new CreatePortfolio("Idempotency", new BigDecimal("1000"))
    );
    var req = request(p.id(), "BUY", "1", "100");
    var trade = service.submit(req);
    assertThat(service.submit(req).id()).isEqualTo(trade.id());
    var event = approved(trade);
    service.assessed(event);
    service.assessed(event);
    service.assessed(event.next("APPROVED", "duplicate business event"));
    assertThat(service.get(p.id()).cash()).isEqualByComparingTo("900");
    assertThat(
      db.queryForObject(
        "select count(*) from outbox where topic='trade.executed.v1' and event_key=?",
        Integer.class,
        p.id()
      )
    ).isEqualTo(1);
    assertThatThrownBy(() ->
      service.submit(
        new TradeRequest(
          req.clientKey(),
          p.id(),
          "MSFT",
          "BUY",
          BigDecimal.ONE,
          BigDecimal.TEN
        )
      )
    ).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsOversellAndInsufficientCash() {
    var p = service.create(new CreatePortfolio("Checks", BigDecimal.TEN));
    for (var req : java.util.List.of(
      request(p.id(), "SELL", "1", "100"),
      request(p.id(), "BUY", "1", "100")
    )) {
      var t = service.submit(req);
      service.assessed(approved(t));
      assertThat(service.trade(t.id()).status()).isEqualTo("REJECTED");
    }
    assertThat(service.get(p.id()).cash()).isEqualByComparingTo("10");
  }

  @Test
  void concurrentSellsCannotCreateShortPosition() throws Exception {
    var p = service.create(
      new CreatePortfolio("Concurrency", new BigDecimal("1000"))
    );
    var buy = service.submit(request(p.id(), "BUY", "1", "100"));
    service.assessed(approved(buy));
    var a = service.submit(request(p.id(), "SELL", "1", "100"));
    var b = service.submit(request(p.id(), "SELL", "1", "100"));
    try (
      var pool =
        java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
    ) {
      var x = pool.submit(() -> service.assessed(approved(a)));
      var y = pool.submit(() -> service.assessed(approved(b)));
      x.get();
      y.get();
    }
    assertThat(
      service.positions(p.id()).getFirst().quantity()
    ).isEqualByComparingTo("0");
    assertThat(
      java.util.List.of(
        service.trade(a.id()).status(),
        service.trade(b.id()).status()
      )
    ).containsExactlyInAnyOrder("EXECUTED", "REJECTED");
  }

  @Test
  void staleEditsFail() {
    var p = service.create(new CreatePortfolio("Original", BigDecimal.ZERO));
    service.rename(p.id(), new Rename("New", 0));
    assertThatThrownBy(() ->
      service.rename(p.id(), new Rename("Lost", 0))
    ).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void httpAndGraphqlContracts() throws Exception {
    mvc.perform(get("/api/portfolios")).andExpect(status().isOk());
    mvc.perform(head("/api/portfolios")).andExpect(status().isOk());
    mvc.perform(options("/api/portfolios")).andExpect(status().isOk());
    mvc
      .perform(
        post("/api/trades").contentType("application/json").content("{}")
      )
      .andExpect(status().isBadRequest());
    mvc
      .perform(
        post("/graphql")
          .contentType("application/json")
          .content("{\"query\":\"{ portfolios { id name cash } }\"}")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.portfolios").isArray())
      .andExpect(jsonPath("$.errors").doesNotExist());
  }
}
