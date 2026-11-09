package com.savewise.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseRequest(
        @NotNull(message = "Category is required") Long categoryId,
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Expense amount must be greater than zero.")
        @Digits(integer = 10, fraction = 2, message = "Amount allows up to 10 digits and 2 decimals") BigDecimal amount,
        @Size(max = 50) String paymentMethod,
        String description,
        @NotNull(message = "Date is required") LocalDate expenseDate) {
}
