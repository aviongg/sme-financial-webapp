package com.app.sme_health_backend.obligation.dto;

import java.util.UUID;

public record ObligationCreateRequest(
        UUID counterpartyId,
        String direction,
        String amount,
        String issueDate,
        String dueDate,
        String referenceNumber,
        String description,
        String sourceType,
        String sourceId
) {
}
