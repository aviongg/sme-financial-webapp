package com.app.sme_health_backend.financialquery.dto;

import com.app.sme_health_backend.financialquery.model.CapabilityStatus;

public record CapabilityItem(
        String toolName,
        CapabilityStatus status,
        String description,
        String unavailableReason
) {}
