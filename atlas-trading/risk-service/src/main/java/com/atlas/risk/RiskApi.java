package com.atlas.risk;

import static com.atlas.risk.RiskService.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Risk", description = "Risk policies, decisions, and stress testing")
public class RiskApi {

  private final RiskService service;

  public RiskApi(RiskService service) {
    this.service = service;
  }

  @Operation(summary = "List risk policies")
  @GetMapping("/api/policies")
  @QueryMapping
  public List<Policy> policies() {
    return service.policies();
  }

  @GetMapping("/api/policies/{id}")
  @QueryMapping
  public Policy policy(@PathVariable @Argument String id) {
    return service.policy(id);
  }

  @PostMapping("/api/policies")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @MutationMapping
  public Policy createPolicy(@RequestBody @Argument @Valid PolicyInput input) {
    return service.create(input);
  }

  @PutMapping("/api/policies/{id}")
  public Policy update(
    @PathVariable String id,
    @RequestBody @Valid PolicyInput input
  ) {
    return service.update(id, input);
  }

  @PatchMapping("/api/policies/{id}")
  public Policy toggle(
    @PathVariable String id,
    @RequestBody @Valid Toggle input
  ) {
    return service.toggle(id, input);
  }

  @DeleteMapping("/api/policies/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id) {
    service.delete(id);
  }

  @GetMapping("/api/decisions")
  @QueryMapping
  public List<Decision> decisions() {
    return service.decisions();
  }

  @PostMapping("/api/stress")
  @MutationMapping
  public StressResult stress(@RequestBody @Argument @Valid StressInput input) {
    return service.stress(input);
  }
}
