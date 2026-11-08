package com.app.sme_health_backend.fx.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FxSettlementPostRequest(
        UUID obligationId,
        UUID accountId,
        BigDecimal amountInObligationCurrency,
        BigDecimal exchangeRate,
        BigDecimal fixedFee,
        BigDecimal percentageFeePercent,
        String feeCurrency,
        LocalDate settlementDate,
        String notes,
        String rateSource,
        BigDecimal recognitionRate
) {
    public FxSettlementPostRequest(
            UUID obligationId,
            UUID accountId,
            BigDecimal amountInObligationCurrency,
            BigDecimal exchangeRate,
            BigDecimal fixedFee,
            BigDecimal percentageFeePercent,
            String feeCurrency,
            LocalDate settlementDate,
            String notes,
            String rateSource
    ) {
        this(obligationId, accountId, amountInObligationCurrency, exchangeRate, fixedFee, percentageFeePercent, feeCurrency, settlementDate, notes, rateSource, null);
    }

    public FxSettlementPostRequest {
        if (fixedFee == null) {
            fixedFee = BigDecimal.ZERO;
        }
        if (percentageFeePercent == null) {
            percentageFeePercent = BigDecimal.ZERO;
        }
        if (settlementDate == null) {
            settlementDate = LocalDate.now();
        }
        if (rateSource == null || rateSource.isBlank()) {
            rateSource = "MANUAL_USER_RATE";
        }
    }
}
