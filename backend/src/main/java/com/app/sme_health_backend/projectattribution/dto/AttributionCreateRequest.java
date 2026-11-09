package com.app.sme_health_backend.projectattribution.dto;

import com.app.sme_health_backend.projectattribution.model.AttributionCostCategory;
import com.app.sme_health_backend.projectattribution.model.AttributionSourceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AttributionCreateRequest(
        @NotNull(message = "Source type is required")
        AttributionSourceType sourceType,

        @NotNull(message = "Source ID is required")
        UUID sourceId,

        @NotNull(message = "Attributed amount is required")
        @DecimalMin(value = "0.0001", message = "Attributed amount must be strictly positive")
        BigDecimal amount,

        @NotNull(message = "Cost category is required")
        AttributionCostCategory costCategory,

        @Size(max = 500, message = "Notes must not exceed 500 characters")
        String notes,

        LocalDate attributionDate,

        BigDecimal exchangeRate
) {
}
