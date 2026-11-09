package com.savewise.dto;

import jakarta.validation.constraints.*;

public record SettingsRequest(
        @NotBlank(message = "Theme is required")
        @Pattern(regexp = "(?i)LIGHT|DARK|SYSTEM", message = "Theme must be LIGHT, DARK or SYSTEM") String theme,
        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "(?i)[A-Z]{3}", message = "Currency must be a 3-letter code such as ZAR") String currency,
        @NotNull(message = "notificationsEnabled is required") Boolean notificationsEnabled) {
}
