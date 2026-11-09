package com.app.sme_health_backend.projectattribution.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CommercialDocumentLinkRequest(
        @NotNull(message = "Document ID is required")
        UUID documentId,
        BigDecimal allocatedAmount,
        String notes
) {}
