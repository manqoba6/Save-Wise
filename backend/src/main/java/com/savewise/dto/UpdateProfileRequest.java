package com.savewise.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record UpdateProfileRequest(
        @NotBlank(message = "First name is required") @Size(max = 100) String firstName,
        @NotBlank(message = "Last name is required") @Size(max = 100) String lastName,
        @Pattern(regexp = "^$|^[+0-9 ()-]{7,20}$", message = "Phone number is not valid") String phone,
        @DecimalMin(value = "0.00", message = "Monthly income cannot be negative.")
        @Digits(integer = 10, fraction = 2, message = "Amount allows up to 10 digits and 2 decimals") BigDecimal monthlyIncome) {
}
