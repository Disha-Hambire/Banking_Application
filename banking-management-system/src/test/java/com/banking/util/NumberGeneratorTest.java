package com.banking.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NumberGeneratorTest {

    @Test
    void accountNumber_is12DigitsAndNeverStartsWithZero() {
        for (int i = 0; i < 500; i++) {
            String number = NumberGenerator.accountNumber();
            assertTrue(number.matches("[1-9]\\d{11}"), "Unexpected account number: " + number);
        }
    }

    @Test
    void accountNumbers_areUnique() {
        Set<String> numbers = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            numbers.add(NumberGenerator.accountNumber());
        }
        assertEquals(1000, numbers.size());
    }

    @Test
    void transactionReference_hasExpectedFormatAndIsUnique() {
        String a = NumberGenerator.transactionReference();
        String b = NumberGenerator.transactionReference();
        assertTrue(a.matches("TXN[0-9A-F]{16}"));
        assertNotEquals(a, b);
    }
}
