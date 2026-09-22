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

  @org.springframework.kafka.annotation.KafkaListener(
    topics = "risk.assessed.v1"
  )
  public void listen(String json) {
    service.assessed(events.read(json));
  }
}
