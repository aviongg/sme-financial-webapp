package com.app.sme_health_backend.fx.dto;

import java.math.BigDecimal;

public record FeeStructure(
        BigDecimal fixedFee,
        String feeCurrency,
        BigDecimal percentageFeePercent,
        BigDecimal fixedFeeInSettlementCurrency,
        BigDecimal percentageFeeInSettlementCurrency,
        BigDecimal totalFeeInSettlementCurrency
) {}

