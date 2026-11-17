package com.banking.entity;

public enum TransactionType {
    DEPOSIT, WITHDRAWAL, TRANSFER_DEBIT, TRANSFER_CREDIT;

    public boolean isDebit() {
        return this == WITHDRAWAL || this == TRANSFER_DEBIT;
    }
}
