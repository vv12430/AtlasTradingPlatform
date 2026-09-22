package com.atlas.accounting;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LedgerTest {

  @Test
  void buyDebitsSecuritiesAndCreditsCash() {
    var lines = Ledger.lines("BUY", new BigDecimal("123.45"));
    assertThat(Ledger.balanced(lines)).isTrue();
    assertThat(lines.getFirst().accountId()).isEqualTo("securities");
    assertThat(lines.getFirst().debit()).isEqualByComparingTo("123.45");
  }

  @Test
  void sellDebitsCash() {
    assertThat(
      Ledger.lines("SELL", BigDecimal.TEN).getFirst().accountId()
    ).isEqualTo("cash");
  }

  @Test
  void rejectsZeroAmountAndInvalidSide() {
    assertThatThrownBy(() -> Ledger.lines("BUY", BigDecimal.ZERO)).isInstanceOf(
      IllegalArgumentException.class
    );
    assertThatThrownBy(() ->
      Ledger.lines("SHORT", BigDecimal.TEN)
    ).isInstanceOf(IllegalArgumentException.class);
  }
}
