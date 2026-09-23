package com.atlas.portfolio;

import static com.atlas.portfolio.Models.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Portfolio", description = "Portfolio creation, renaming, activation, deletion and trades info")
public class PortfolioApi {

  private final PortfolioService service;

  public PortfolioApi(PortfolioService service) {
    this.service = service;
  }

  @Operation(summary = "List portfolios")
  @GetMapping("/api/portfolios")
  @QueryMapping
  public List<Portfolio> portfolios() {
    return service.portfolios();
  }

  @GetMapping("/api/portfolios/{id}")
  @QueryMapping
  public Portfolio portfolio(@PathVariable @Argument String id) {
    return service.get(id);
  }

  @PostMapping("/api/portfolios")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @MutationMapping
  public Portfolio createPortfolio(
    @RequestBody @Argument @Valid CreatePortfolio input
  ) {
    return service.create(input);
  }

  @PutMapping("/api/portfolios/{id}")
  public Portfolio rename(
    @PathVariable String id,
    @RequestBody @Valid Rename input
  ) {
    return service.rename(id, input);
  }

  @PatchMapping("/api/portfolios/{id}")
  public Portfolio active(
    @PathVariable String id,
    @RequestBody @Valid Active input
  ) {
    return service.active(id, input);
  }

  @DeleteMapping("/api/portfolios/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id) {
    service.delete(id);
  }

  @GetMapping("/api/portfolios/{id}/positions")
  @QueryMapping
  public List<Position> positions(@PathVariable @Argument String id) {
    return service.positions(id);
  }

  @GetMapping("/api/trades")
  @QueryMapping
  public List<Trade> trades() {
    return service.trades();
  }

  @GetMapping("/api/trades/{id}")
  @QueryMapping
  public Trade trade(@PathVariable @Argument String id) {
    return service.trade(id);
  }

  @PostMapping("/api/trades")
  @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
  @MutationMapping
  public Trade submitTrade(@RequestBody @Argument @Valid TradeRequest input) {
    return service.submit(input);
  }
}
