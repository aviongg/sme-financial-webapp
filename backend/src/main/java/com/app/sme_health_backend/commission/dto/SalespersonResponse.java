package com.app.sme_health_backend.commission.dto;

import com.app.sme_health_backend.commission.model.SalespersonStatus;
import java.time.Instant;
import java.util.UUID;

public record SalespersonResponse(
    UUID id,
    UUID businessId,
    UUID userId,
    String displayName,
    String email,
    String phone,
    SalespersonStatus status,
    String notes,
    long activeAssignmentsCount,
    Instant createdAt,
    Instant updatedAt
) {}
