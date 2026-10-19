package com.app.sme_health_backend.obligation.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ObligationAllocationResponse(
        UUID id,
        UUID financialTransactionId,
        String amount,
        LocalDate effectiveDate,
        String status,
        String cancelledAt,
        String cancellationReason,
        UUID reversalTransactionId,
        String createdAt,
        String transactionDescription,
        String accountName,
        UUID accountId
) {
}
