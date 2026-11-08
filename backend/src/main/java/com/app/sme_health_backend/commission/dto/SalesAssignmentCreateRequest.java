package com.app.sme_health_backend.commission.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record SalesAssignmentCreateRequest(
    @NotNull(message = "Counterparty ID is required")
    UUID counterpartyId,

    @NotNull(message = "Effective start date is required")
    LocalDate effectiveStartDate,

    LocalDate effectiveEndDate,
    String notes
) {}
