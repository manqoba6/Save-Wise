package com.savewise.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One income/expense record that a schedule created. */
public record RecurringRunResponse(Long recordId, LocalDate date, BigDecimal amount, String description) {
}
