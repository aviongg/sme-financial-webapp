package com.app.sme_health_backend.reconciliation.dto;

import com.app.sme_health_backend.reconciliation.model.StatementImportStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StatementImportDetailResponse(
        UUID id,
        UUID accountId,
        String accountName,
        String sourceFilename,
        int totalEntries,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        LocalDate fromDate,
        LocalDate toDate,
        StatementImportStatus status,
        List<StatementEntryResponse> entries,
        Instant createdAt
) {}
