package com.atlas.common;

import java.math.BigDecimal;
import java.util.UUID;

public record TradeEvent(
  String eventId,
  int version,
  String tradeId,
  String portfolioId,
  String symbol,
  String side,
  BigDecimal quantity,
  BigDecimal price,
  String status,
  String reason
) {
  public TradeEvent next(String status, String reason) {
    return new TradeEvent(
      UUID.randomUUID().toString(),
      1,
      tradeId,
      portfolioId,
      symbol,
      side,
      quantity,
      price,
      status,
      reason
    );
  }

  public BigDecimal notional() {
    return quantity
      .multiply(price)
      .setScale(2, java.math.RoundingMode.HALF_EVEN);
  }
}
