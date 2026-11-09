package com.savewise.dto;

import jakarta.validation.constraints.*;

public record ForgotPasswordRequest(
        @NotBlank(message = "Email is required") @Email(message = "Email is not valid") String email) {
}
