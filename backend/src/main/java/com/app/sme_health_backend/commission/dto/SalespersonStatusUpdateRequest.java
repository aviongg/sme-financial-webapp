package com.app.sme_health_backend.commission.dto;

import com.app.sme_health_backend.commission.model.SalespersonStatus;
import jakarta.validation.constraints.NotNull;

public record SalespersonStatusUpdateRequest(
    @NotNull(message = "Status is required")
    SalespersonStatus status
) {}
