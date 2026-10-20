package com.app.sme_health_backend.reconciliation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AccountReconciliationSummaryResponse(
        UUID accountId,
        String accountName,
        String accountType,
        String currency,
        BigDecimal ledgerBalance,
        long totalStatementEntries,
        long matchedEntries,
        long unmatchedEntries,
        long excludedEntries,
        long unmatchedMovements,
        BigDecimal importedDebitTotal,
        BigDecimal importedCreditTotal,
        BigDecimal matchedTotal,
        BigDecimal unmatchedTotal,
        LocalDate openingDate,
        Instant lastReconciledAt
) {}
