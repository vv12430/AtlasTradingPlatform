package com.atlas.accounting;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.atlas.common.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountingIT {

  @Autowired
  AccountingService service;

  @Autowired
  JdbcTemplate db;

  @Autowired
  MockMvc mvc;

  @Test
  void postingIsBalancedAndIdempotent() {
    var e = new TradeEvent(
      UUID.randomUUID().toString(),
      1,
      UUID.randomUUID().toString(),
      "demo",
      "AAPL",
      "BUY",
      new BigDecimal("3"),
      new BigDecimal("100"),
      "EXECUTED",
      ""
    );
    service.post(e);
    service.post(e);
    service.post(e.next("EXECUTED", "duplicate"));
    assertThat(
      db.queryForObject(
        "select count(*) from journals where trade_id=?",
        Integer.class,
        e.tradeId()
      )
    ).isEqualTo(1);
    assertThat(
      service
        .journals()
        .stream()
        .filter(j -> j.tradeId().equals(e.tradeId()))
        .findFirst()
        .get()
        .lines()
    ).hasSize(2);
    assertThat(
      service
        .trialBalance()
        .stream()
        .map(x -> x.debit().subtract(x.credit()))
        .reduce(BigDecimal.ZERO, BigDecimal::add)
    ).isEqualByComparingTo("0");
  }

  @Test
  void cannotDeleteSystemAccounts() {
    assertThatThrownBy(() -> service.delete("cash")).isInstanceOf(
      IllegalStateException.class
    );
  }

  @Test
  void apiAndGraphql() throws Exception {
    var a = service.create(new AccountingService.AccountInput("Test", 0));
    mvc
      .perform(
        put("/api/accounts/" + a.id())
          .contentType("application/json")
          .content("{\"name\":\"Renamed\",\"version\":0}")
      )
      .andExpect(status().isOk());
    mvc
      .perform(
        patch("/api/accounts/" + a.id())
          .contentType("application/json")
          .content("{\"enabled\":false,\"version\":1}")
      )
      .andExpect(status().isOk());
    mvc
      .perform(delete("/api/accounts/" + a.id()))
      .andExpect(status().isNoContent());
    mvc.perform(head("/api/accounts")).andExpect(status().isOk());
    mvc.perform(options("/api/accounts")).andExpect(status().isOk());
    mvc
      .perform(
        post("/graphql")
          .contentType("application/json")
          .content(
            "{\"query\":\"{ trialBalance { accountId debit credit } }\"}"
          )
      )
      .andExpect(jsonPath("$.data.trialBalance").isArray())
      .andExpect(jsonPath("$.errors").doesNotExist());
  }
}
