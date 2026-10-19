package com.app.sme_health_backend.obligation.dto;

import java.util.UUID;

public record ObligationSettlementRequest(
        UUID accountId,
        String amount,
        String settlementDate,
        String reference,
        String description
) {
}
