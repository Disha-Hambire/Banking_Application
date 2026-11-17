package com.banking.dto;

import com.banking.entity.BankTransaction;
import com.banking.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(String referenceNumber, String accountNumber, TransactionType type,
                                  String direction, BigDecimal amount, BigDecimal balanceAfter,
                                  String counterpartAccount, String description, LocalDateTime createdAt) {

    public static TransactionResponse from(BankTransaction t) {
        return new TransactionResponse(t.getReferenceNumber(), t.getAccount().getAccountNumber(), t.getType(),
                t.getType().isDebit() ? "DEBIT" : "CREDIT", t.getAmount(), t.getBalanceAfter(),
                t.getCounterpartAccount(), t.getDescription(), t.getCreatedAt());
    }
}
