package com.app.sme_health_backend.commission.dto;

import com.app.sme_health_backend.commission.model.AssignmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SalesAssignmentResponse(
    UUID id,
    UUID businessId,
    UUID salespersonId,
    String salespersonName,
    UUID counterpartyId,
    String counterpartyName,
    AssignmentStatus status,
    LocalDate effectiveStartDate,
    LocalDate effectiveEndDate,
    UUID assignedByUserId,
    String notes,
    Instant createdAt
) {}
