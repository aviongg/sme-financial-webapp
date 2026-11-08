package com.app.sme_health_backend.commission.dto;

import com.app.sme_health_backend.commission.model.CommissionAdvanceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CommissionAdvanceResponse(
    UUID id,
    UUID businessId,
    UUID salespersonId,
    String salespersonName,
    BigDecimal amount,
    BigDecimal appliedAmount,
    BigDecimal unappliedAmount,
    String currency,
    LocalDate paymentDate,
    UUID financialAccountId,
    UUID financialTransactionId,
    CommissionAdvanceStatus status,
    String notes,
    Instant createdAt
) {}
