package com.app.sme_health_backend.financialquery.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProjectFundingGapResult(
        UUID projectId,
        String projectCode,
        String projectName,
        String baseCurrency,
        BigDecimal plannedCost,
        BigDecimal attributedCashOutflows,
        BigDecimal cashHeadroom,
        BigDecimal totalCommitments,
        BigDecimal fundingGap,
        boolean requiresFunding,
        String fundingStatus,
        String explanation
) {}

