package com.savewise.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record GoalContributionRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
        @Digits(integer = 10, fraction = 2, message = "Amount allows up to 10 digits and 2 decimals") BigDecimal amount) {
}
