package com.atlas.common;

import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
  name = "atlas.messaging.enabled",
  havingValue = "true",
  matchIfMissing = true
)
public class OutboxPublisher {

  private final JdbcTemplate db;
  private final KafkaTemplate<String, String> kafka;

  public OutboxPublisher(JdbcTemplate db, KafkaTemplate<String, String> kafka) {
    this.db = db;
    this.kafka = kafka;
  }

  @Scheduled(fixedDelay = 1000)
  public void publish() {
    var rows = db.queryForList(
      "select id,topic,event_key,payload from outbox where sent=0 order by created_at,id fetch first 100 rows only"
    );
    for (var row : rows) {
      try {
        kafka
          .send(
            row.get("TOPIC").toString(),
            row.get("EVENT_KEY").toString(),
            row.get("PAYLOAD").toString()
          )
          .get(10, TimeUnit.SECONDS);
        db.update("update outbox set sent=1 where id=?", row.get("ID"));
      } catch (Exception e) {
        LoggerFactory.getLogger(getClass()).warn(
          "Outbox publication deferred: {}",
          e.getMessage()
        );
        if (
          e instanceof InterruptedException
        ) Thread.currentThread().interrupt();
        break;
      }
    }
  }
}
