package com.app.sme_health_backend.reconciliation.dto;

import com.app.sme_health_backend.reconciliation.model.StatementImportStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StatementImportResponse(
        UUID id,
        UUID accountId,
        String sourceFilename,
        int totalEntries,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        LocalDate fromDate,
        LocalDate toDate,
        StatementImportStatus status,
        int duplicateWarningsCount,
        Instant createdAt
) {}
