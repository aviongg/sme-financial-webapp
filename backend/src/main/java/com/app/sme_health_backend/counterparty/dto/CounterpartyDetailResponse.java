package com.app.sme_health_backend.counterparty.dto;

import java.time.Instant;
import java.util.UUID;

public record CounterpartyDetailResponse(
        UUID id,
        UUID businessId,
        String displayName,
        String type,
        String status,
        String email,
        String phone,
        String address,
        String referenceCode,
        String notes,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
