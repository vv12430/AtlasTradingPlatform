package com.atlas.accounting;

import java.math.BigDecimal;
import java.util.List;

public final class Ledger {

  public record Line(String accountId, BigDecimal debit, BigDecimal credit) {}

  /**
   * Builds the two double-entry journal lines for a trade, based on
   * its side (BUY or SELL) and notional amount.
   *
   * A BUY debits the securities account and credits cash for the full
   * amount (acquiring an asset, paying cash for it). A SELL debits cash
   * and credits the securities account for the full amount (receiving
   * cash, giving up the asset). Because both lines always use the same
   * amount on opposite sides, the result is guaranteed to balance.
   *
   * Rejects a non-positive amount and any side other than "BUY" or
   * "SELL".
   *
   * For example, lines("BUY", 15000) debits securities $15,000 and
   * credits cash $15,000; lines("SELL", 6800) debits cash $6,800 and
   * credits securities $6,800.
   */
  public static List<Line> lines(String side, BigDecimal amount) {
    return lines(side, amount, BigDecimal.ZERO);
  }

  /**
   * Builds the double-entry journal lines for a trade, including fees.
   *
   * A BUY debits the securities account and credits cash for the full
   * amount. A SELL debits cash and credits the securities account.
   * When fees are present, an additional line debits the fees account
   * and adjusts the cash credit/debit to include the fee amount,
   * keeping the journal balanced.
   *
   * Rejects a non-positive amount and any side other than "BUY" or
   * "SELL".
   *
   * For example, lines("BUY", 15000, 50) debits securities $15,000,
   * debits fees $50, and credits cash $15,050. lines("SELL", 6800, 50)
   * debits cash $6,750, credits securities $6,800, and credits fees $50.
   */
  public static List<Line> lines(String side, BigDecimal amount, BigDecimal fees) {
    if (amount.signum() <= 0) throw new IllegalArgumentException(
      "Posting amount must be positive"
    );
    if (
      !side.equals("BUY") && !side.equals("SELL")
    ) throw new IllegalArgumentException("Invalid side");
    boolean buy = side.equals("BUY");
    
    if (fees == null || fees.compareTo(BigDecimal.ZERO) == 0) {
      return List.of(
        new Line(buy ? "securities" : "cash", amount, BigDecimal.ZERO),
        new Line(buy ? "cash" : "securities", BigDecimal.ZERO, amount)
      );
    }
    
    // With fees: BUY debits securities + fees, credits cash (amount + fees)
    // SELL debits cash (amount - fees), credits securities + credits fees
    if (buy) {
      return List.of(
        new Line("securities", amount, BigDecimal.ZERO),
        new Line("fees", fees, BigDecimal.ZERO),
        new Line("cash", BigDecimal.ZERO, amount.add(fees))
      );
    } else {
      return List.of(
        new Line("cash", amount.subtract(fees), BigDecimal.ZERO),
        new Line("securities", BigDecimal.ZERO, amount),
        new Line("fees", BigDecimal.ZERO, fees)
      );
    }
  }

  /**
   * Checks whether a list of journal lines satisfies double-entry
   * bookkeeping's core rule: total debits must equal total credits.
   *
   * For each line, computes debit minus credit, then sums these values
   * across all lines. The lines are balanced only if that sum is
   * exactly zero — any nonzero sum means debits and credits don't
   * offset each other, indicating an inconsistent journal entry.
   *
   * For example, a line debiting $15,000 to Investments and a line
   * crediting $15,000 to Cash sum to zero and are balanced; if the
   * credit were $14,999 instead, the sum would be $1 and the lines
   * would be considered unbalanced.
   */
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
