package com.banking.dto;

import com.banking.entity.AccountType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record OpenAccountRequest(
        @NotNull Long customerId,
        @NotNull AccountType type,
        @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal initialDeposit) {
}
