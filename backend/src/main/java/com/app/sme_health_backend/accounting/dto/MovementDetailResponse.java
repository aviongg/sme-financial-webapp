package com.app.sme_health_backend.accounting.dto;

import java.time.LocalDate;
import java.util.UUID;

public record MovementDetailResponse(
        UUID id,
        UUID accountId,
        String movementDirection,
        String amount,
        String currency,
        LocalDate effectiveDate,
        String createdAt
) {}
