package com.atlas.risk;

import java.math.*;

public final class RiskMath {

  private RiskMath() {}

  /**
   * Calculates the profit or loss (P&L) impact of applying a market
   * shock to an exposure.
   *
   * The shock is expressed as a whole-number percentage (e.g. -10 for
   * a 10% drop, not -0.10), and the result is scaled to 2 decimal
   * places using half-even (banker's) rounding.
   *
   * For example, an exposure of $1,000,000 with a shockPercent of -10
   * returns a P&L of -$100,000.00. This method returns only the P&L;
   * the caller is responsible for adding it to the original exposure
   * to get the stressed value.
   */
  public static BigDecimal stress(
    BigDecimal exposure,
    BigDecimal shockPercent
  ) {
    return exposure
      .multiply(shockPercent)
      .divide(new BigDecimal("100"), 2, RoundingMode.HALF_EVEN);
  }


  /**
   * Determines whether a trade's notional value is within an allowed
   * policy limit.
   *
   * A notional is allowed only if it is strictly positive (rejecting
   * zero or negative values) and does not exceed the given limit.
   *
   * For example, a notional of $500,000 against a limit of $1,000,000
   * is allowed; a notional of $1,500,000 against the same limit is not;
   * and a notional of $0 or a negative value is never allowed, regardless
   * of the limit.
   */
  public static boolean allowed(BigDecimal notional, BigDecimal limit) {
    return notional.signum() > 0 && notional.compareTo(limit) <= 0;
  }
}
