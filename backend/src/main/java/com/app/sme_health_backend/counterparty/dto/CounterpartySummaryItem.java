package com.app.sme_health_backend.counterparty.dto;

import java.time.Instant;
import java.util.UUID;

public record CounterpartySummaryItem(
        UUID id,
        String displayName,
        String type,
        String status,
        String email,
        String phone,
        String address,
        String referenceCode,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
