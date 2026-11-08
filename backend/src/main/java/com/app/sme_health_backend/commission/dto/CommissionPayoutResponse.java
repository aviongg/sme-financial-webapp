package com.app.sme_health_backend.commission.dto;

import com.app.sme_health_backend.commission.model.CommissionPayoutStatus;
import com.app.sme_health_backend.commission.model.CommissionPayoutType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CommissionPayoutResponse(
    UUID id,
    UUID businessId,
    UUID salespersonId,
    String salespersonName,
    UUID earningId,
    CommissionPayoutType payoutType,
    BigDecimal amount,
    String currency,
    LocalDate paymentDate,
    UUID financialAccountId,
    UUID financialTransactionId,
    UUID commissionAdvanceId,
    CommissionPayoutStatus status,
    UUID reversalTransactionId,
    String reversalReason,
    String notes,
    Instant createdAt
) {}
