package com.app.sme_health_backend.commission.dto;

import com.app.sme_health_backend.commission.model.CalculationType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CommissionCalculationPreviewResponse(
    UUID salespersonId,
    String salespersonName,
    UUID counterpartyId,
    String counterpartyName,
    UUID ruleId,
    String ruleName,
    CalculationType calculationType,
    BigDecimal ratePercentage,
    BigDecimal fixedAmount,
    BigDecimal saleAmount,
    BigDecimal calculatedCommission,
    String currency,
    Instant calculationDate,
    boolean eligible,
    String ineligibilityReason
) {}
