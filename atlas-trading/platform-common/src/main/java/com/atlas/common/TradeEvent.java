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
  String reason,
  BigDecimal fillQuantity,
  BigDecimal fees
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
      reason,
      fillQuantity,
      fees
    );
  }

  public TradeEvent withFill(BigDecimal fillQuantity, BigDecimal fees) {
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
      reason,
      fillQuantity,
      fees
    );
  }

  /**
   * Calculates the notional (total dollar) value of a trade by
   * multiplying quantity by price, rounded to 2 decimal places using
   * half-even (banker's) rounding.
   *
   * This is the value used throughout the trade lifecycle for risk
   * limit checks, cash/position updates, and ledger postings.
   *
   * For example, a quantity of 100 at a price of $150.00 produces a
   * notional of $15,000.00. A very small quantity or price can round
   * down to $0.00, which is why downstream checks explicitly reject a
   * notional that rounds to zero.
   */  public BigDecimal notional() {
    return quantity
      .multiply(price)
      .setScale(2, java.math.RoundingMode.HALF_EVEN);
  }
}
