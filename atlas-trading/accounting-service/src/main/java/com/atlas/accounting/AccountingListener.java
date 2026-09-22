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

  @org.springframework.kafka.annotation.KafkaListener(
    topics = "trade.executed.v1"
  )
  public void listen(String json) {
    service.post(events.read(json));
  }
}
