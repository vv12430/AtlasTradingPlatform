package com.atlas.portfolio;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class Models {

  public record Portfolio(
    String id,
    String name,
    BigDecimal cash,
    boolean active,
    int version
  ) {}

  public record CreatePortfolio(
    @NotBlank @Size(max = 100) String name,
    @DecimalMin("0")
    @DecimalMax("1000000000")
    @Digits(integer = 10, fraction = 2)
    @NotNull
    BigDecimal cash
  ) {}

  public record Rename(
    @NotBlank @Size(max = 100) String name,
    @Min(0) int version
  ) {}

  public record Active(@NotNull Boolean active, @Min(0) int version) {}

  public record TradeRequest(
    @NotBlank @Size(max = 100) String clientKey,
    @NotBlank String portfolioId,
    @Pattern(regexp = "[A-Z][A-Z0-9.]{0,11}") @NotNull String symbol,
    @Pattern(regexp = "BUY|SELL") @NotNull String side,
    @NotNull
    @DecimalMin("0.000001")
    @DecimalMax("1000000")
    @Digits(integer = 7, fraction = 6)
    BigDecimal quantity,
    @NotNull
    @DecimalMin("0.000001")
    @DecimalMax("1000000")
    @Digits(integer = 7, fraction = 6)
    BigDecimal price
  ) {}

  public record CancelRequest(
    @NotBlank String tradeId,
    @NotBlank String reason
  ) {}

  public record ModifyRequest(
    @NotBlank String tradeId,
    @NotNull
    @DecimalMin("0.000001")
    @DecimalMax("1000000")
    @Digits(integer = 7, fraction = 6)
    BigDecimal quantity,
    @NotNull
    @DecimalMin("0.000001")
    @DecimalMax("1000000")
    @Digits(integer = 7, fraction = 6)
    BigDecimal price
  ) {}

  public record Trade(
    String id,
    String portfolioId,
    String symbol,
    String side,
    BigDecimal quantity,
    BigDecimal price,
    String status,
    String reason,
    BigDecimal filledQuantity,
    BigDecimal remainingQuantity,
    BigDecimal fees
  ) {}

  public record TradePage(
    List<Trade> content,
    int page,
    int size,
    long totalElements,
    int totalPages
  ) {}

  public record Position(
    String symbol,
    BigDecimal quantity,
    BigDecimal cost,
    BigDecimal realizedPnl
  ) {}
}
