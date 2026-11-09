package com.savewise.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SavingTransactionResponse(Long id, Long accountId, String accountName, String transactionType,
                                        BigDecimal amount, LocalDate transactionDate, String description) {
}
