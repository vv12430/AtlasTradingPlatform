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
      "Test approval",
      BigDecimal.ZERO,
      BigDecimal.ZERO
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
    ).containsExactlyInAnyOrder("FILLED", "REJECTED");
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

  @Test
  void searchesTradesWithFiltersSortingAndPagination() throws Exception {
    var p = service.create(new CreatePortfolio("Trade search", new BigDecimal("10000")));
    var first = service.submit(new TradeRequest(
      UUID.randomUUID().toString(), p.id(), "ZZZZ", "BUY",
      new BigDecimal("1"), new BigDecimal("100")
    ));
    var second = service.submit(new TradeRequest(
      UUID.randomUUID().toString(), p.id(), "ZZZZ", "SELL",
      new BigDecimal("2"), new BigDecimal("200")
    ));
    var third = service.submit(new TradeRequest(
      UUID.randomUUID().toString(), p.id(), "ZZZZ", "BUY",
      new BigDecimal("3"), new BigDecimal("300")
    ));

    mvc
      .perform(
        get("/api/trades")
          .queryParam("side", "BUY")
          .queryParam("symbol", "ZZZZ")
          .queryParam("page", "0")
          .queryParam("size", "1")
          .queryParam("sort", "price,desc")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content.length()").value(1))
      .andExpect(jsonPath("$.content[0].id").value(third.id()))
      .andExpect(jsonPath("$.totalElements").value(2))
      .andExpect(jsonPath("$.totalPages").value(2));

    mvc
      .perform(
        get("/api/trades")
          .queryParam("status", "DOES_NOT_EXIST")
          .queryParam("page", "0")
          .queryParam("size", "10")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content").isEmpty())
      .andExpect(jsonPath("$.totalElements").value(0))
      .andExpect(jsonPath("$.totalPages").value(0));
  }

  @Test
  void tradeTimelineShowsWaitingProgressAndCompletion() throws Exception {
    var p = service.create(new CreatePortfolio("Timeline", new BigDecimal("10000")));
    var t = service.submit(request(p.id(), "BUY", "1", "100"));

    mvc
      .perform(get("/api/trades/" + t.id() + "/timeline"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.steps[0].state").value("Completed"))
      .andExpect(jsonPath("$.steps[1].state").value("In Progress"))
      .andExpect(jsonPath("$.steps[2].state").value("Waiting"))
      .andExpect(jsonPath("$.steps[3].state").value("Waiting"))
      .andExpect(jsonPath("$.steps[4].state").value("Waiting"))
      .andExpect(jsonPath("$.steps[0].timestamp").isNotEmpty());

    service.assessed(approved(t));
    service.accounted(t.id());

    mvc
      .perform(get("/api/trades/" + t.id() + "/timeline"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.steps[1].state").value("Completed"))
      .andExpect(jsonPath("$.steps[2].state").value("Completed"))
      .andExpect(jsonPath("$.steps[3].state").value("Completed"))
      .andExpect(jsonPath("$.steps[4].state").value("Completed"));
  }

  @Test
  void rejectsInvalidTradeSearchPageAndSize() throws Exception {
    mvc
      .perform(get("/api/trades").queryParam("page", "-1"))
      .andExpect(status().isBadRequest());
    mvc
      .perform(get("/api/trades").queryParam("size", "201"))
      .andExpect(status().isBadRequest());
    mvc
      .perform(get("/api/trades").queryParam("sort", "clientKey,asc"))
      .andExpect(status().isBadRequest());
  }

  @Test
  void partialFillUpdatesTradeState() {
    var p = service.create(new CreatePortfolio("Partial Fill", new BigDecimal("10000")));
    var t = service.submit(request(p.id(), "BUY", "100", "100"));
    
    // Simulate partial fill of 50 shares
    var partialEvent = new TradeEvent(
      UUID.randomUUID().toString(),
      1,
      t.id(),
      p.id(),
      "AAPL",
      "BUY",
      new BigDecimal("100"),
      new BigDecimal("100"),
      "APPROVED",
      "Partial fill",
      new BigDecimal("50"),
      BigDecimal.ZERO
    );
    service.assessed(partialEvent);
    
    var updated = service.trade(t.id());
    assertThat(updated.status()).isEqualTo("PARTIALLY_FILLED");
    assertThat(updated.filledQuantity()).isEqualByComparingTo("50");
    assertThat(updated.remainingQuantity()).isEqualByComparingTo("50");
    assertThat(service.get(p.id()).cash()).isEqualByComparingTo("5000"); // 10000 - (50 * 100) = 5000
  }

  @Test
  void fullFillAfterPartialUpdatesToFilled() {
    var p = service.create(new CreatePortfolio("Full Fill", new BigDecimal("10000")));
    var t = service.submit(request(p.id(), "BUY", "100", "100"));
    
    // First partial fill
    var partialEvent = new TradeEvent(
      UUID.randomUUID().toString(),
      1,
      t.id(),
      p.id(),
      "AAPL",
      "BUY",
      new BigDecimal("100"),
      new BigDecimal("100"),
      "APPROVED",
      "Partial fill",
      new BigDecimal("50"),
      BigDecimal.ZERO
    );
    service.assessed(partialEvent);
    
    // Second fill completes the order
    var completeEvent = new TradeEvent(
      UUID.randomUUID().toString(),
      1,
      t.id(),
      p.id(),
      "AAPL",
      "BUY",
      new BigDecimal("100"),
      new BigDecimal("100"),
      "APPROVED",
      "Complete fill",
      new BigDecimal("50"),
      BigDecimal.ZERO
    );
    service.assessed(completeEvent);
    
    var updated = service.trade(t.id());
    assertThat(updated.status()).isEqualTo("FILLED");
    assertThat(updated.filledQuantity()).isEqualByComparingTo("100");
    assertThat(updated.remainingQuantity()).isEqualByComparingTo("0");
    assertThat(service.get(p.id()).cash()).isEqualByComparingTo("0"); // 10000 - (100 * 100) = 0
  }

  @Test
  void cancelPendingTrade() {
    var p = service.create(new CreatePortfolio("Cancel", new BigDecimal("1000")));
    var t = service.submit(request(p.id(), "BUY", "10", "100"));
    
    service.cancel(new CancelRequest(t.id(), "User requested cancellation"));
    
    var updated = service.trade(t.id());
    assertThat(updated.status()).isEqualTo("CANCELLED");
    assertThat(service.get(p.id()).cash()).isEqualByComparingTo("1000"); // Cash unchanged
  }

  @Test
  void cancelPartiallyFilledTrade() {
    var p = service.create(new CreatePortfolio("Cancel Partial", new BigDecimal("10000")));
    var t = service.submit(request(p.id(), "BUY", "100", "100"));
    
    // Partial fill first
    var partialEvent = new TradeEvent(
      UUID.randomUUID().toString(),
      1,
      t.id(),
      p.id(),
      "AAPL",
      "BUY",
      new BigDecimal("100"),
      new BigDecimal("100"),
      "APPROVED",
      "Partial fill",
      new BigDecimal("30"),
      BigDecimal.ZERO
    );
    service.assessed(partialEvent);
    
    // Cancel remaining
    service.cancel(new CancelRequest(t.id(), "Cancel remaining"));
    
    var updated = service.trade(t.id());
    assertThat(updated.status()).isEqualTo("CANCELLED");
    assertThat(updated.filledQuantity()).isEqualByComparingTo("30");
    assertThat(service.get(p.id()).cash()).isEqualByComparingTo("7000"); // 10000 - (30 * 100) = 7000
  }

  @Test
  void cannotCancelFilledOrRejectedTrade() {
    var p = service.create(new CreatePortfolio("No Cancel", new BigDecimal("1000")));
    var t = service.submit(request(p.id(), "BUY", "10", "100"));
    
    // Fill the trade
    var approved = approved(t);
    service.assessed(approved);
    
    assertThatThrownBy(() -> 
      service.cancel(new CancelRequest(t.id(), "Should fail"))
    ).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void modifyPendingTrade() {
    var p = service.create(new CreatePortfolio("Modify", new BigDecimal("10000")));
    var t = service.submit(request(p.id(), "BUY", "100", "100"));
    
    var modified = service.modify(new ModifyRequest(t.id(), new BigDecimal("80"), new BigDecimal("110")));
    
    assertThat(modified.quantity()).isEqualByComparingTo("80");
    assertThat(modified.price()).isEqualByComparingTo("110");
    assertThat(modified.status()).isEqualTo("PENDING_RISK");
  }

  @Test
  void cannotModifyFilledTrade() {
    var p = service.create(new CreatePortfolio("No Modify", new BigDecimal("1000")));
    var t = service.submit(request(p.id(), "BUY", "10", "100"));
    
    // Fill the trade
    var approved = approved(t);
    service.assessed(approved);
    
    assertThatThrownBy(() -> 
      service.modify(new ModifyRequest(t.id(), new BigDecimal("20"), new BigDecimal("110")))
    ).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void feesDeductedFromCashAndRealizedPnl() {
    var p = service.create(new CreatePortfolio("Fees", new BigDecimal("20000")));
    var t = service.submit(request(p.id(), "BUY", "100", "100"));
    
    var eventWithFees = new TradeEvent(
      UUID.randomUUID().toString(),
      1,
      t.id(),
      p.id(),
      "AAPL",
      "BUY",
      new BigDecimal("100"),
      new BigDecimal("100"),
      "APPROVED",
      "With fees",
      new BigDecimal("100"),
      new BigDecimal("50")
    );
    service.assessed(eventWithFees);
    
    // Cash should be reduced by notional + fees = 10000 + 50 = 10050
    assertThat(service.get(p.id()).cash()).isEqualByComparingTo("9950");
    
    // Now sell with fees
    var sell = service.submit(request(p.id(), "SELL", "100", "120"));
    var sellEvent = new TradeEvent(
      UUID.randomUUID().toString(),
      1,
      sell.id(),
      p.id(),
      "AAPL",
      "SELL",
      new BigDecimal("100"),
      new BigDecimal("120"),
      "APPROVED",
      "Sell with fees",
      new BigDecimal("100"),
      new BigDecimal("30")
    );
    service.assessed(sellEvent);
    
    // Cash: 9950 + (12000 - 30) = 21920
    // Realized P&L: (12000 - 10000 - 30) = 1970 (buy fees already deducted from cash, not from cost basis)
    assertThat(service.get(p.id()).cash()).isEqualByComparingTo("21920");
    var pos = service.positions(p.id()).getFirst();
    assertThat(pos.realizedPnl()).isEqualByComparingTo("1970");
  }
}
