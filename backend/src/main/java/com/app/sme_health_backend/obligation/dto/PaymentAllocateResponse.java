package com.app.sme_health_backend.obligation.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PaymentAllocateResponse(
        UUID paymentId,
        UUID financialTransactionId,
        UUID counterpartyId,
        String counterpartyName,
        UUID accountId,
        String accountName,
        LocalDate paymentDate,
        String totalCashAmount,
        String totalAllocatedAmount,
        String unappliedAdvanceAmount,
        UUID advanceId,
        String resultingAccountBalance,
        List<AllocatedObligationItemResponse> allocations,
        String status
) {
}
