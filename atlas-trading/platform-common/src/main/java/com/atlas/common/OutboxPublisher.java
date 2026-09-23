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

  /**
   * Relays pending events from the outbox table to Kafka.
   *
   * Fetches up to 100 unsent outbox rows in creation order and attempts
   * to publish each one to its target topic, marking it sent only after
   * the Kafka send is confirmed. If a publish fails, processing stops
   * for the remainder of this batch rather than skipping ahead, so
   * events are never published out of order; the failed (and any
   * later) events remain unsent and will be retried on the next run.
   *
   * Interruption during a send restores the thread's interrupted status
   * so callers (e.g. a scheduler or thread pool) can detect the request
   * to stop.
   *
   * For example, given unsent events A, B, and C in that order, if B
   * fails to publish, A is marked sent, but B and C are left unsent and
   * both will be retried, in order, the next time this method runs.
   */

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
