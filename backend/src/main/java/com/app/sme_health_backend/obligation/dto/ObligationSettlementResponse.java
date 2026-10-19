package com.app.sme_health_backend.obligation.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ObligationSettlementResponse(
        UUID allocationId,
        UUID obligationId,
        UUID transactionId,
        String amount,
        LocalDate settlementDate,
        String resultingAccountBalance,
        String remainingOutstandingAmount,
        String settlementStatus,
        String createdAt
) {
}
