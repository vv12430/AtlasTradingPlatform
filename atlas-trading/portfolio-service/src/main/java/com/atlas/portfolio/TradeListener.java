package com.atlas.portfolio;

@org.springframework.stereotype.Component
public class TradeListener {

  private final PortfolioService service;
  private final com.atlas.common.Events events;

  public TradeListener(
    PortfolioService service,
    com.atlas.common.Events events
  ) {
    this.service = service;
    this.events = events;
  }

  /**
   * Kafka listener that consumes risk assessment outcomes from the
   * "risk.assessed.v1" topic.
   *
   * Deserializes the raw JSON payload into a TradeEvent and hands it off
   * to the service to be executed (if approved) or rejected, completing
   * the trade lifecycle that began with its submission and risk check.
   */
  @org.springframework.kafka.annotation.KafkaListener(
    topics = "risk.assessed.v1"
  )
  public void listen(String json) {
    service.assessed(events.read(json));
  }

  @org.springframework.kafka.annotation.KafkaListener(
    topics = "trade.accounted.v1"
  )
  public void accounted(String json) {
    service.accounted(events.read(json).tradeId());
  }
}
