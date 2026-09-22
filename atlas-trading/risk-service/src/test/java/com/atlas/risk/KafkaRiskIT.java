package com.atlas.risk;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.atlas.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
  properties = {
    "atlas.messaging.enabled=true",
    "spring.kafka.listener.auto-startup=true",
    "spring.datasource.url=jdbc:h2:mem:kafkaRisk;MODE=Oracle;DB_CLOSE_DELAY=-1",
  }
)
@ActiveProfiles("test")
@EmbeddedKafka(
  partitions = 3,
  bootstrapServersProperty = "spring.kafka.bootstrap-servers",
  topics = { "trade.submitted.v1", "risk.assessed.v1" }
)
class KafkaRiskIT {

  @Autowired
  KafkaTemplate<String, String> kafka;

  @Autowired
  ObjectMapper json;

  @Autowired
  JdbcTemplate db;

  @Test
  void kafkaListenerPersistsDecisionAndPublishesOutbox() throws Exception {
    var e = new TradeEvent(
      UUID.randomUUID().toString(),
      1,
      UUID.randomUUID().toString(),
      "demo",
      "AAPL",
      "BUY",
      BigDecimal.ONE,
      BigDecimal.TEN,
      "PENDING_RISK",
      ""
    );
    kafka
      .send("trade.submitted.v1", e.portfolioId(), json.writeValueAsString(e))
      .get();
    await()
      .atMost(Duration.ofSeconds(30))
      .untilAsserted(() -> {
        assertThat(
          db.queryForObject(
            "select count(*) from decisions where trade_id=?",
            Integer.class,
            e.tradeId()
          )
        ).isEqualTo(1);
        assertThat(
          db.queryForObject(
            "select count(*) from outbox where sent=1",
            Integer.class
          )
        ).isGreaterThanOrEqualTo(1);
      });
  }
}
