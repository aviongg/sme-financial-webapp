package com.app.sme_health_backend.obligation.dto;

import java.util.UUID;

public record AdvanceCreateRequest(
        UUID counterpartyId,
        String direction,
        UUID accountId,
        String paymentDate,
        String amount,
        String referenceNumber,
        String description
) {
}
