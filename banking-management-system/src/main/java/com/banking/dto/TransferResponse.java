package com.banking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResponse(String referenceNumber, String fromAccount, String toAccount, BigDecimal amount,
                               BigDecimal fromBalanceAfter, LocalDateTime timestamp) {
}
