package com.app.sme_health_backend.commission.dto;

import com.app.sme_health_backend.commission.model.CommissionAuditEventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Single immutable audit event in the commission ledger.
 */
public record CommissionAuditEventResponse(
        UUID eventId,
        Instant timestamp,
        UUID salespersonId,
        String salespersonName,
        UUID counterpartyId,
        String counterpartyName,
        CommissionAuditEventType eventType,
        BigDecimal amount,
        String currency,
        String status,
        String referenceId,
        UUID financialTransactionId,
        UUID reversalTransactionId,
        String notes
) {
}
