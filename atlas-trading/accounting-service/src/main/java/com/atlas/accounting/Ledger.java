package com.atlas.accounting;

import java.math.BigDecimal;
import java.util.List;

public final class Ledger {

  public record Line(String accountId, BigDecimal debit, BigDecimal credit) {}

  public static List<Line> lines(String side, BigDecimal amount) {
    if (amount.signum() <= 0) throw new IllegalArgumentException(
      "Posting amount must be positive"
    );
    if (
      !side.equals("BUY") && !side.equals("SELL")
    ) throw new IllegalArgumentException("Invalid side");
    boolean buy = side.equals("BUY");
    return List.of(
      new Line(buy ? "securities" : "cash", amount, BigDecimal.ZERO),
      new Line(buy ? "cash" : "securities", BigDecimal.ZERO, amount)
    );
  }

  public static boolean balanced(List<Line> lines) {
    return (
      lines
        .stream()
        .map(x -> x.debit().subtract(x.credit()))
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .signum() == 0
    );
  }
}
