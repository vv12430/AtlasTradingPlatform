package com.atlas.portfolio;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PortfolioValidationTest {

  @Test
  void rejectsNegativeQuantityAndUnsupportedSide() {
    try (
      var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()
    ) {
      var input = new Models.TradeRequest(
        "key",
        "demo",
        "AAPL",
        "SHORT",
        new BigDecimal("-1"),
        BigDecimal.TEN
      );
      assertThat(factory.getValidator().validate(input)).hasSize(2);
    }
  }

  @Test
  void eventUsesBankersRounding() {
    var e = new com.atlas.common.TradeEvent(
      "event",
      1,
      "trade",
      "demo",
      "AAPL",
      "BUY",
      BigDecimal.ONE,
      new BigDecimal("10.005"),
      "PENDING_RISK",
      "",
      BigDecimal.ZERO,
      BigDecimal.ZERO
    );
    assertThat(e.notional()).isEqualByComparingTo("10.00");
  }
}
