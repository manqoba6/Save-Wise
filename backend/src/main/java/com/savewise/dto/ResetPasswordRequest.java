package com.savewise.dto;

import jakarta.validation.constraints.*;

public record ResetPasswordRequest(
        @NotBlank(message = "Reset token is required") String token,
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String newPassword) {
}
