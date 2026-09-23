package com.atlas.accounting;

@org.springframework.stereotype.Component
public class AccountingListener {

  private final AccountingService service;
  private final com.atlas.common.Events events;

  public AccountingListener(
    AccountingService service,
    com.atlas.common.Events events
  ) {
    this.service = service;
    this.events = events;
  }

  /**
   * Kafka listener that consumes trade execution events from the
   * "trade.executed.v1" topic.
   *
   * Deserializes the raw JSON payload into a TradeEvent and hands it off
   * to the service to be posted as a double-entry journal in the
   * accounting ledger, completing the trade's lifecycle from submission
   * through risk assessment, execution, and financial posting.
   */
  @org.springframework.kafka.annotation.KafkaListener(
    topics = "trade.executed.v1"
  )
  public void listen(String json) {
    service.post(events.read(json));
  }
}
