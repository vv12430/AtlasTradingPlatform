package com.atlas.risk;

@org.springframework.stereotype.Component
public class RiskListener {

  private final RiskService service;
  private final com.atlas.common.Events events;

  public RiskListener(RiskService service, com.atlas.common.Events events) {
    this.service = service;
    this.events = events;
  }

  @org.springframework.kafka.annotation.KafkaListener(
    topics = "trade.submitted.v1"
  )
  public void listen(String json) {
    service.assess(events.read(json));
  }
}
