package com.app.sme_health_backend.records.projection.dto;

import com.app.sme_health_backend.financialquery.dto.FinancialEvidenceReference;
import com.app.sme_health_backend.financialquery.model.DataCompleteness;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Authoritative read-only monthly projection derived from canonical FinSight ledger events.
 */
public record MonthlyLedgerProjectionResponse(
        String contractVersion,
        UUID businessId,
        String month,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate asOfDate,
        DataCompleteness completeness,
        List<String> warnings,
        List<FinancialEvidenceReference> evidence,
        List<CurrencyMonthlyCashSummary> currencySummaries,
        List<AccountMonthlyCashPosition> accountPositions,
        MonthlyOperatingCashFlow cashFlow,
        MonthlyRecognizedFigures recognizedFigures,
        MonthlyObligationPositions obligationPositions,
        MonthlyDraftSummary draftSummary
) {}
