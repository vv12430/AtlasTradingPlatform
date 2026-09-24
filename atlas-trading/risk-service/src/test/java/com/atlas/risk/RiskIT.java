package com.atlas.risk;

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
class RiskIT {

  @Autowired
  RiskService service;

  @Autowired
  JdbcTemplate db;

  @Autowired
  MockMvc mvc;

  TradeEvent event(String price) {
    return new TradeEvent(
      UUID.randomUUID().toString(),
      1,
      UUID.randomUUID().toString(),
      "demo",
      "AAPL",
      "BUY",
      BigDecimal.ONE,
      new BigDecimal(price),
      "PENDING_RISK",
      "",
      BigDecimal.ZERO,
      BigDecimal.ZERO
    );
  }

  @Test
  void limitsAndDuplicateEvents() {
    var e = event("100");
    service.assess(e);
    service.assess(e);
    service.assess(e.next("PENDING_RISK", "duplicate"));
    assertThat(
      db.queryForObject(
        "select count(*) from decisions where trade_id=?",
        Integer.class,
        e.tradeId()
      )
    ).isEqualTo(1);
    assertThat(
      db.queryForObject(
        "select status from decisions where trade_id=?",
        String.class,
        e.tradeId()
      )
    ).isEqualTo("APPROVED");
    var rejected = event("100001");
    service.assess(rejected);
    assertThat(
      db.queryForObject(
        "select status from decisions where trade_id=?",
        String.class,
        rejected.tradeId()
      )
    ).isEqualTo("REJECTED");
  }

  @Test
  void failClosedWhenNoPolicies() {
    var policy = service.policy("default");
    service.toggle("default", new RiskService.Toggle(false, policy.version()));
    try {
      var e = event("1");
      service.assess(e);
      assertThat(
        db.queryForObject(
          "select status from decisions where trade_id=?",
          String.class,
          e.tradeId()
        )
      ).isEqualTo("REJECTED");
    } finally {
      service.toggle(
        "default",
        new RiskService.Toggle(true, service.policy("default").version())
      );
    }
  }

  @Test
  void restVerbsAndGraphql() throws Exception {
    var p = service.create(
      new RiskService.PolicyInput("Temporary", BigDecimal.TEN, 0)
    );
    mvc
      .perform(
        put("/api/policies/" + p.id())
          .contentType("application/json")
          .content("{\"name\":\"Updated\",\"maxNotional\":20,\"version\":0}")
      )
      .andExpect(status().isOk());
    mvc
      .perform(
        patch("/api/policies/" + p.id())
          .contentType("application/json")
          .content("{\"enabled\":false,\"version\":1}")
      )
      .andExpect(status().isOk());
    mvc
      .perform(delete("/api/policies/" + p.id()))
      .andExpect(status().isNoContent());
    mvc.perform(head("/api/policies")).andExpect(status().isOk());
    mvc.perform(options("/api/policies")).andExpect(status().isOk());
    mvc
      .perform(
        post("/graphql")
          .contentType("application/json")
          .content(
            "{\"query\":\"mutation { stress(input:{exposure:1000,shockPercent:-10}) { pnl } }\"}"
          )
      )
      .andExpect(jsonPath("$.data.stress.pnl").value(-100))
      .andExpect(jsonPath("$.errors").doesNotExist());
  }
}
