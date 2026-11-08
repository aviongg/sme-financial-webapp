package com.app.sme_health_backend.commission.dto;

import com.app.sme_health_backend.commission.model.CalculationType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EligibleCollectionResponse(
    UUID allocationId,
    UUID obligationId,
    UUID counterpartyId,
    String counterpartyName,
    UUID salespersonId,
    String salespersonName,
    BigDecimal collectionAmount,
    LocalDate collectionDate,
    String currency,
    UUID applicableRuleId,
    String applicableRuleName,
    CalculationType calculationType,
    BigDecimal rateOrAmount,
    BigDecimal estimatedCommission,
    boolean alreadyEarned,
    UUID existingEarningId
) {}
