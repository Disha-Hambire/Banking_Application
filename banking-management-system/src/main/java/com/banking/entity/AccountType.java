package com.banking.entity;

import java.math.BigDecimal;

public enum AccountType {
    SAVINGS(new BigDecimal("500.00")),
    CURRENT(BigDecimal.ZERO);

    private final BigDecimal minBalance;

    AccountType(BigDecimal minBalance) {
        this.minBalance = minBalance;
    }

    public BigDecimal getMinBalance() {
        return minBalance;
    }
}
