package com.savewise.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record SavingAccountRequest(
        @NotBlank(message = "Account name is required") @Size(max = 100) String accountName,
        @DecimalMin(value = "0.01", message = "Target must be greater than zero.")
        @Digits(integer = 10, fraction = 2, message = "Amount allows up to 10 digits and 2 decimals") BigDecimal targetAmount) {
}
