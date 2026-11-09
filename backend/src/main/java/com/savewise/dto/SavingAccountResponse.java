package com.savewise.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SavingAccountResponse(Long id, String accountName, BigDecimal currentBalance,
                                    BigDecimal targetAmount, LocalDateTime createdAt) {
}
