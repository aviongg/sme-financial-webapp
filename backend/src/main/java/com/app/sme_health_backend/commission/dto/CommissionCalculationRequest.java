package com.app.sme_health_backend.commission.dto;

import com.app.sme_health_backend.commission.model.CommissionSourceType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CommissionCalculationRequest(
    @NotNull(message = "Source type is required")
    CommissionSourceType sourceType,

    String sourceId,
    UUID counterpartyId,
    UUID salespersonId,
    BigDecimal saleAmount,
    LocalDate eventDate,
    String currency,
    String notes
) {}
