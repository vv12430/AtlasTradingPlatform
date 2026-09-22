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
