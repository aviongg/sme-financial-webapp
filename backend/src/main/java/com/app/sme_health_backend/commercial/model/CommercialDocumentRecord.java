package com.app.sme_health_backend.commercial.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Persisted commercial document with the counterparty name already decrypted. */
public record CommercialDocumentRecord(
        UUID id,
        UUID businessId,
        CommercialDocumentKind kind,
        CommercialDocumentStatus status,
        String documentNumber,
        String counterpartyName,
        LocalDate documentDate,
        LocalDate dueDate,
        String notes,
        BigDecimal subtotal,
        BigDecimal total,
        String baseCurrency,
        String financialEffect,
        String calculationVersion,
        long version,
        UUID createdByUserId,
        UUID updatedByUserId,
        UUID finalizedByUserId,
        OffsetDateTime finalizedAt,
        UUID voidedByUserId,
        OffsetDateTime voidedAt,
        String voidReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        int lineCount,
        List<LineRecord> lines
) {
    public record LineRecord(
            UUID id,
            int lineNumber,
            String description,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            BigDecimal lineAmount
    ) { }

    public CommercialDocumentRecord withLines(List<LineRecord> loaded) {
        return new CommercialDocumentRecord(id, businessId, kind, status, documentNumber, counterpartyName,
                documentDate, dueDate, notes, subtotal, total, baseCurrency, financialEffect, calculationVersion,
                version, createdByUserId, updatedByUserId, finalizedByUserId, finalizedAt, voidedByUserId, voidedAt,
                voidReason, createdAt, updatedAt, loaded.size(), List.copyOf(loaded));
    }
}
