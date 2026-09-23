package com.atlas.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class Events {

  private final JdbcTemplate db;
  private final ObjectMapper json;

  public Events(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  /**
   * Records an event to be published to a message topic, using the
   * transactional outbox pattern rather than publishing directly.
   *
   * Serializes the event to JSON and inserts it into the outbox table
   * as unsent (sent = 0). A separate relay process is expected to poll
   * this table, publish unsent rows to the actual topic, and mark them
   * as sent.
   *
   * For example, emitting a "risk.assessed.v1" event for portfolio
   * "PORT-42" inserts a row keyed by that portfolio ID with the
   * serialized event payload and sent = 0, to be delivered later.
   */
  public void emit(String topic, TradeEvent event) {
    try {
      db.update(
        "insert into outbox(id,topic,event_key,payload,sent) values(?,?,?,?,0)",
        event.eventId(),
        topic,
        event.portfolioId(),
        json.writeValueAsString(event)
      );
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  public TradeEvent read(String payload) {
    try {
      TradeEvent event = json.readValue(payload, TradeEvent.class);
      if (event.version() != 1) throw new IllegalArgumentException(
        "Unsupported event version"
      );
      return event;
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  // Must run inside the same transaction as the domain write. A concurrent duplicate rolls back and is retried.
  public boolean first(TradeEvent e) {
    if (
      db.queryForObject(
        "select count(*) from inbox where id=?",
        Integer.class,
        e.eventId()
      ) > 0
    ) return false;
    db.update("insert into inbox(id) values(?)", e.eventId());
    return true;
  }
}
