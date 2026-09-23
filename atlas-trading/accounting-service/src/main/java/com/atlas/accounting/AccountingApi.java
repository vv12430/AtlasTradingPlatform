package com.atlas.accounting;

import static com.atlas.accounting.AccountingService.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Accounting", description = "Accounting operations such as creating, updating, toggling, deleting and listing accounts")
public class AccountingApi {

  private final AccountingService service;

  public AccountingApi(AccountingService service) {
    this.service = service;
  }

  @Operation(summary = "List accounts")
  @GetMapping("/api/accounts")
  @QueryMapping
  public List<Account> accounts() {
    return service.accounts();
  }

  @GetMapping("/api/accounts/{id}")
  @QueryMapping
  public Account account(@PathVariable @Argument String id) {
    return service.account(id);
  }

  @PostMapping("/api/accounts")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @MutationMapping
  public Account createAccount(
    @RequestBody @Argument @Valid AccountInput input
  ) {
    return service.create(input);
  }

  @PutMapping("/api/accounts/{id}")
  public Account update(
    @PathVariable String id,
    @RequestBody @Valid AccountInput input
  ) {
    return service.update(id, input);
  }

  @PatchMapping("/api/accounts/{id}")
  public Account toggle(
    @PathVariable String id,
    @RequestBody @Valid Toggle input
  ) {
    return service.toggle(id, input);
  }

  @DeleteMapping("/api/accounts/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id) {
    service.delete(id);
  }

  @GetMapping("/api/journals")
  @QueryMapping
  public List<Journal> journals() {
    return service.journals();
  }

  @GetMapping("/api/trial-balance")
  @QueryMapping
  public List<Balance> trialBalance() {
    return service.trialBalance();
  }
}
