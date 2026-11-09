package com.savewise.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** startDate is the first date the transaction posts. endDate is optional (open-ended when omitted). */
public record RecurringTransactionRequest(
        @NotBlank(message = "Transaction type is required")
        @Pattern(regexp = "(?i)INCOME|EXPENSE", message = "Transaction type must be INCOME or EXPENSE") String transactionType,
        @NotNull(message = "Category is required") Long categoryId,
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
        @Digits(integer = 10, fraction = 2, message = "Amount allows up to 10 digits and 2 decimals") BigDecimal amount,
        @NotBlank(message = "Frequency is required")
        @Pattern(regexp = "(?i)DAILY|WEEKLY|MONTHLY|YEARLY", message = "Frequency must be one of DAILY, WEEKLY, MONTHLY, YEARLY") String frequency,
        @NotNull(message = "Start date is required") LocalDate startDate,
        LocalDate endDate,
        @Size(max = 500) String description) {
}
