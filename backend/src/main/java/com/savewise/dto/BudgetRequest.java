package com.savewise.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** budgetMonth can be any date in the month; it is stored as the 1st of that month. */
public record BudgetRequest(
        @NotNull(message = "Category is required") Long categoryId,
        @NotNull(message = "Month is required") LocalDate budgetMonth,
        @NotNull(message = "Budget amount is required")
        @DecimalMin(value = "0.01", message = "Budget must be greater than zero.")
        @Digits(integer = 10, fraction = 2, message = "Amount allows up to 10 digits and 2 decimals") BigDecimal budgetAmount) {
}
