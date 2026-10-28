package com.app.sme_health_backend.financialquery.dto;

import java.time.Instant;

/**
 * Audit and provenance reference linking a financial query output to underlying authoritative records.
 */
public record FinancialEvidenceReference(
        String entityType,
        String entityId,
        String description,
        Instant recordedAt
) {}
