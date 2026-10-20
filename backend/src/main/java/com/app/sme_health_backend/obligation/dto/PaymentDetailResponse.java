package com.app.sme_health_backend.obligation.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PaymentDetailResponse(
        UUID paymentId,
        UUID businessId,
        UUID accountId,
        String accountName,
        UUID counterpartyId,
        String counterpartyName,
        String eventType,
        String direction,
        String amount,
        LocalDate paymentDate,
        String description,
        Boolean isReversal,
        UUID reversedTransactionId,
        Boolean isReversed,
        UUID reversedByTransactionId,
        String createdAt,
        List<ObligationAllocationResponse> allocations,
        AdvanceDetailResponse advanceResidual
) {
}
