package com.app.sme_health_backend.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MonthQueryRequest(
        @NotBlank(message = "month is required")
        @Pattern(regexp = "^\\d{4}-(?:0[1-9]|1[0-2])$", message = "month must be in YYYY-MM format")
        String month
) {}
