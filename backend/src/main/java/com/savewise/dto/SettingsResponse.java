package com.savewise.dto;

import java.time.LocalDateTime;

public record SettingsResponse(String theme, String currency, String currencySymbol,
                               boolean notificationsEnabled, LocalDateTime updatedAt) {
}
