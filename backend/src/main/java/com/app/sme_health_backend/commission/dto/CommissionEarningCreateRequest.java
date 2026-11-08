package com.app.sme_health_backend.commission.dto;

import com.app.sme_health_backend.commission.model.CommissionSourceType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CommissionEarningCreateRequest(
    @NotNull(message = "Source type is required")
    CommissionSourceType sourceType,

    @NotNull(message = "Source ID is required")
    String sourceId,

    UUID salespersonId,
    UUID ruleId,
    String notes
) {}
