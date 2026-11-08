package com.app.sme_health_backend.commission.dto;

import com.app.sme_health_backend.commission.model.CommissionRuleStatus;
import jakarta.validation.constraints.NotNull;

public record CommissionRuleStatusUpdateRequest(
    @NotNull(message = "Status is required")
    CommissionRuleStatus status
) {}
