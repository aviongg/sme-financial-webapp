package com.app.sme_health_backend.obligation.dto;

import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CommercialRecognitionRequest(
        @NotNull(message = "Document ID is required")
        UUID documentId,

        CommercialDocumentKind documentKind,

        @NotNull(message = "Counterparty ID is required")
        UUID counterpartyId,

        String idempotencyKey,

        String notes
) {
}

