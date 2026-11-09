package com.savewise.dto;

import jakarta.validation.constraints.*;

public record CategoryRequest(
        @NotBlank(message = "Category name is required") @Size(max = 100) String name,
        @NotBlank(message = "Type is required")
        @Pattern(regexp = "INCOME|EXPENSE", message = "Type must be INCOME or EXPENSE") String type,
        @Size(max = 100) String icon) {
}
