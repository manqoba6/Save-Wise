package com.savewise.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseResponse(Long id, Long categoryId, String categoryName, BigDecimal amount,
                              String paymentMethod, String description, LocalDate expenseDate) {
}
