package com.savewise.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalResponse(Long id, String title, BigDecimal targetAmount, BigDecimal currentAmount,
                           LocalDate deadline, String status, BigDecimal percentComplete) {
}
