package com.app.sme_health_backend.reconciliation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReconciliationMatchRequest(
        @NotNull(message = "accountMovementId is required")
        UUID accountMovementId
) {}
