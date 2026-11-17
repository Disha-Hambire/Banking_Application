package com.banking.controller;

import com.banking.dto.AccountResponse;
import com.banking.dto.BalanceResponse;
import com.banking.dto.OpenAccountRequest;
import com.banking.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/api/accounts")
    @Operation(summary = "Open a SAVINGS or CURRENT account - ADMIN (201, 400, 404)")
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.open(request));
    }

    @GetMapping("/api/accounts/{accountNumber}")
    @Operation(summary = "Get account details (200, 404)")
    public AccountResponse get(@PathVariable("accountNumber") String accountNumber) {
        return accountService.get(accountNumber);
    }

    @GetMapping("/api/accounts/{accountNumber}/balance")
    @Operation(summary = "Check balance (200, 404)")
    public BalanceResponse balance(@PathVariable("accountNumber") String accountNumber) {
        return accountService.balance(accountNumber);
    }

    @GetMapping("/api/customers/{customerId}/accounts")
    @Operation(summary = "List accounts of a customer - ADMIN (200, 404)")
    public List<AccountResponse> byCustomer(@PathVariable("customerId") Long customerId) {
        return accountService.listByCustomer(customerId);
    }

    @DeleteMapping("/api/accounts/{accountNumber}")
    @Operation(summary = "Close an account; balance must be zero - ADMIN (204, 400, 404)")
    public ResponseEntity<Void> close(@PathVariable("accountNumber") String accountNumber) {
        accountService.close(accountNumber);
        return ResponseEntity.noContent().build();
    }
}
