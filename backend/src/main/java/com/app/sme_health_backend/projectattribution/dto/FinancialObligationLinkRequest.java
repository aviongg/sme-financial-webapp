package com.app.sme_health_backend.projectattribution.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record FinancialObligationLinkRequest(
        @NotNull(message = "Obligation ID is required")
        UUID obligationId,
        BigDecimal allocatedAmount,
        String notes
) {}
