package com.savewise.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** status is ACTIVE, PAUSED, or ENDED (the end date has passed). */
public record RecurringTransactionResponse(Long id, String transactionType, Long categoryId, String categoryName,
                                           BigDecimal amount, String frequency, LocalDate startDate, LocalDate endDate,
                                           LocalDate nextExecution, String description, boolean active, String status) {
}
