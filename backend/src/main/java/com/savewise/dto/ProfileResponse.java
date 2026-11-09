package com.savewise.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProfileResponse(Long id, String firstName, String lastName, String email, String phone,
                              BigDecimal monthlyIncome, boolean hasProfilePicture, String role, LocalDateTime createdAt) {
}
