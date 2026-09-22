package com.atlas.risk;

import java.math.*;

public final class RiskMath {

  private RiskMath() {}

  public static BigDecimal stress(
    BigDecimal exposure,
    BigDecimal shockPercent
  ) {
    return exposure
      .multiply(shockPercent)
      .divide(new BigDecimal("100"), 2, RoundingMode.HALF_EVEN);
  }

  public static boolean allowed(BigDecimal notional, BigDecimal limit) {
    return notional.signum() > 0 && notional.compareTo(limit) <= 0;
  }
}
