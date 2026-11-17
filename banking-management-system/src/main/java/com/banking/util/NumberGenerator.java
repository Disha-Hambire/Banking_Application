package com.banking.util;

import java.security.SecureRandom;
import java.util.UUID;

public final class NumberGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private NumberGenerator() {
    }

    /** 12-digit account number that never starts with 0. */
    public static String accountNumber() {
        long n = 100_000_000_000L + RANDOM.nextLong(900_000_000_000L);
        return Long.toString(n);
    }

    /** Unique transaction reference, e.g. TXN3F2A9C01B7E44D12 (TXN + 16 hex chars). */
    public static String transactionReference() {
        return "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
