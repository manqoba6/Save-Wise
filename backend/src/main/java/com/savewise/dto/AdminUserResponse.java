package com.savewise.dto;

import java.time.LocalDateTime;

public record AdminUserResponse(Long id, String firstName, String lastName, String email, String role,
                                boolean enabled, LocalDateTime createdAt) {
}
