package com.app.sme_health_backend.zakat.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.app.sme_health_backend.zakat.dto.ZakatTypes.*;

public record ZakatPreviewResponse(
        String ruleProfile,
        String ruleVersion,
        LocalDate assessmentDate,
        String currency,
        CalculationStatus calculationStatus,
        BigDecimal zakatRate,
        String nisabMethod,
        String nisabMetal,
        BigDecimal metalWeightGrams,
        BigDecimal metalPricePerGram,
        String priceSource,
        OffsetDateTime priceTimestamp,
        BigDecimal nisabValue,
        HaulStatus haulStatus,
        List<Breakdown> assetBreakdown,
        List<Breakdown> liabilityBreakdown,
        BigDecimal grossZakatableAssets,
        BigDecimal deductibleLiabilities,
        BigDecimal netZakatableAssets,
        Boolean nisabMet,
        BigDecimal zakatDue,
        BigDecimal unroundedZakatDue,
        List<String> missingFields,
        List<String> warnings,
        FinancingComplianceStatus financingComplianceStatus,
        String debtPolicy,
        String disclosure,
        ZakatPreviewRequest inputs,
        MonthlyRecordSource source
) {
    public record Breakdown(
            String category,
            String reference,
            BigDecimal inputAmount,
            BigDecimal includedAmount,
            String treatment
    ) {
    }

    public record MonthlyRecordSource(
            UUID businessId,
            String month,
            LocalDate balancesDate,
            BigDecimal cashBalanceEom,
            BigDecimal inventoryValue,
            BigDecimal receivablesOutstanding,
            BigDecimal payablesOutstanding,
            BigDecimal loanOutstanding,
            BigDecimal interestExpense,
            String financingType
    ) {
    }
}
