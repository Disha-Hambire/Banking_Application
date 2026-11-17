package com.banking.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CustomerRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private CustomerRequest valid() {
        return new CustomerRequest("Jane Doe", "jane@example.com", "9876543210", "Pune", LocalDate.of(1992, 3, 4));
    }

    @Test
    void validRequest_hasNoViolations() {
        assertTrue(validator.validate(valid()).isEmpty());
    }

    @Test
    void blankName_isRejected() {
        CustomerRequest r = new CustomerRequest(" ", "jane@example.com", "9876543210", "Pune", LocalDate.of(1992, 3, 4));
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void invalidEmail_isRejected() {
        CustomerRequest r = new CustomerRequest("Jane", "not-an-email", "9876543210", "Pune", LocalDate.of(1992, 3, 4));
        assertEquals(1, validator.validate(r).size());
    }

    @Test
    void invalidPhone_isRejected() {
        CustomerRequest r = new CustomerRequest("Jane", "jane@example.com", "12ab", "Pune", LocalDate.of(1992, 3, 4));
        assertEquals(1, validator.validate(r).size());
    }

    @Test
    void futureDateOfBirth_isRejected() {
        CustomerRequest r = new CustomerRequest("Jane", "jane@example.com", "9876543210", "Pune",
                LocalDate.now().plusDays(1));
        assertEquals(1, validator.validate(r).size());
    }

    @Test
    void depositAmount_mustBePositiveWithMax2Decimals() {
        assertTrue(validator.validate(new TransactionRequest("123456789012", new java.math.BigDecimal("10.50"), null)).isEmpty());
        assertFalse(validator.validate(new TransactionRequest("123456789012", java.math.BigDecimal.ZERO, null)).isEmpty());
        assertFalse(validator.validate(new TransactionRequest("123456789012", new java.math.BigDecimal("1.234"), null)).isEmpty());
        assertFalse(validator.validate(new TransactionRequest("abc", new java.math.BigDecimal("5"), null)).isEmpty());
    }
}
