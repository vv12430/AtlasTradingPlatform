package com.atlas.risk;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RiskMathTest {

  @Test
  void acceptsExactLimitRejectsAboveAndZero() {
    assertThat(
      RiskMath.allowed(new BigDecimal("100"), new BigDecimal("100"))
    ).isTrue();
    assertThat(
      RiskMath.allowed(new BigDecimal("100.01"), new BigDecimal("100"))
    ).isFalse();
    assertThat(RiskMath.allowed(BigDecimal.ZERO, BigDecimal.TEN)).isFalse();
  }

  @Test
  void downsideShockProducesLoss() {
    assertThat(
      RiskMath.stress(new BigDecimal("100000"), new BigDecimal("-15"))
    ).isEqualByComparingTo("-15000");
  }
}
