package com.app.sme_health_backend.commercial.dto;

import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.model.CommercialDocumentRecord;
import com.app.sme_health_backend.commercial.model.CommercialDocumentStatus;
import com.app.sme_health_backend.commercial.service.CommercialDocumentCalculator;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A recorded commercial document. financialEffect is always NONE and settlement is explicitly unavailable:
 * no paid, due, outstanding or payment-status value exists in Phase A.
 */
public record CommercialDocumentResponse(
        UUID id,
        UUID businessId,
        CommercialDocumentKind kind,
        CommercialDocumentStatus status,
        String documentNumber,
        String counterpartyName,
        LocalDate documentDate,
        LocalDate dueDate,
        String notes,
        String baseCurrency,
        String subtotal,
        String total,
        int lineCount,
        List<Line> lines,
        String financialEffect,
        String settlementAvailability,
        String calculationVersion,
        List<String> finalizeBlockers,
        long version,
        UUID createdByUserId,
        UUID updatedByUserId,
        UUID finalizedByUserId,
        OffsetDateTime finalizedAt,
        UUID voidedByUserId,
        OffsetDateTime voidedAt,
        String voidReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record Line(
            UUID id,
            int lineNumber,
            String description,
            String quantity,
            String unit,
            String unitPrice,
            String lineAmount
    ) { }

    public static CommercialDocumentResponse from(CommercialDocumentRecord record, List<String> finalizeBlockers) {
        return new CommercialDocumentResponse(
                record.id(),
                record.businessId(),
                record.kind(),
                record.status(),
                record.documentNumber(),
                record.counterpartyName(),
                record.documentDate(),
                record.dueDate(),
                record.notes(),
                record.baseCurrency(),
                CommercialDocumentCalculator.amount(record.subtotal()),
                CommercialDocumentCalculator.amount(record.total()),
                record.lineCount(),
                record.lines() == null ? null : record.lines().stream().map(line -> new Line(
                        line.id(),
                        line.lineNumber(),
                        line.description(),
                        CommercialDocumentCalculator.quantity(line.quantity()),
                        line.unit(),
                        CommercialDocumentCalculator.amount(line.unitPrice()),
                        CommercialDocumentCalculator.amount(line.lineAmount())
                )).toList(),
                CommercialDocumentCalculator.FINANCIAL_EFFECT,
                CommercialDocumentCalculator.SETTLEMENT_AVAILABILITY,
                record.calculationVersion(),
                finalizeBlockers,
                record.version(),
                record.createdByUserId(),
                record.updatedByUserId(),
                record.finalizedByUserId(),
                record.finalizedAt(),
                record.voidedByUserId(),
                record.voidedAt(),
                record.voidReason(),
                record.createdAt(),
                record.updatedAt()
        );
    }
}
