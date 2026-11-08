package com.app.sme_health_backend.commission.dto;

import com.app.sme_health_backend.commission.model.CommissionEarningStatus;
import com.app.sme_health_backend.commission.model.CommissionSourceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CommissionEarningResponse(
    UUID id,
    UUID businessId,
    UUID salespersonId,
    String salespersonName,
    UUID ruleId,
    String ruleName,
    UUID counterpartyId,
    String counterpartyName,
    CommissionSourceType sourceType,
    String sourceId,
    String sourceFingerprint,
    BigDecimal saleAmount,
    BigDecimal commissionRateSnapshot,
    BigDecimal commissionAmount,
    BigDecimal approvedAmount,
    BigDecimal settledAmount,
    BigDecimal remainingPayable,
    String currency,
    CommissionEarningStatus status,
    Instant approvedAt,
    UUID approvedByUserId,
    Instant paidAt,
    String notes,
    Instant createdAt,
    Instant updatedAt
) {}
