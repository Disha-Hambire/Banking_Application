package com.banking.dto;

import com.banking.entity.Account;
import com.banking.entity.AccountType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountResponse(String accountNumber, Long customerId, AccountType type, BigDecimal balance,
                              boolean active, LocalDateTime createdAt) {

    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getAccountNumber(), a.getCustomer().getId(), a.getType(),
                a.getBalance(), a.isActive(), a.getCreatedAt());
    }
}
