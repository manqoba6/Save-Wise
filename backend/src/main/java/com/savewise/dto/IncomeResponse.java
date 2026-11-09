package com.savewise.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IncomeResponse(Long id, Long categoryId, String categoryName, BigDecimal amount,
                             String description, LocalDate incomeDate) {
}
