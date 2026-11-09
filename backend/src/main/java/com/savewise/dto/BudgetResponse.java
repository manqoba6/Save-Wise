package com.savewise.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** status is OK (under 80%), WARNING (80% to under 100%) or CRITICAL (100% or more). */
public record BudgetResponse(Long id, Long categoryId, String categoryName, LocalDate budgetMonth,
                             BigDecimal budgetAmount, BigDecimal spent, BigDecimal remaining,
                             BigDecimal percentUsed, String status) {
}
