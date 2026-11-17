package com.banking.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record TransactionRequest(
        @NotBlank @Pattern(regexp = "^\\d{12}$", message = "must be a 12-digit account number") String accountNumber,
        @NotNull @DecimalMin(value = "0.01", message = "must be greater than zero")
        @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @Size(max = 200) String description) {
}
