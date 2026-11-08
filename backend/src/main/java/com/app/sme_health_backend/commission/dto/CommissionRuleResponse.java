package com.app.sme_health_backend.commission.dto;

import com.app.sme_health_backend.commission.model.CalculationType;
import com.app.sme_health_backend.commission.model.CommissionRuleStatus;
import com.app.sme_health_backend.commission.model.EarningBasis;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CommissionRuleResponse(
    UUID id,
    UUID businessId,
    UUID salespersonId,
    String salespersonName,
    String ruleName,
    CalculationType calculationType,
    EarningBasis earningBasis,
    BigDecimal ratePercentage,
    BigDecimal fixedAmount,
    String currency,
    LocalDate effectiveStartDate,
    LocalDate effectiveEndDate,
    CommissionRuleStatus status,
    Instant createdAt,
    Instant updatedAt
) {}
