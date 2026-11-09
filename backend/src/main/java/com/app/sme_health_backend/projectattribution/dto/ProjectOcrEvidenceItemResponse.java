package com.app.sme_health_backend.projectattribution.dto;

import com.app.sme_health_backend.projectattribution.model.AttributionSourceType;
import com.app.sme_health_backend.projectattribution.model.ProjectOcrEvidenceStatus;
import com.app.sme_health_backend.projectattribution.model.ProjectOcrMatchMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProjectOcrEvidenceItemResponse(
        UUID id,
        UUID projectId,
        String projectName,
        UUID documentId,
        String originalFilename,
        ProjectOcrEvidenceStatus evidenceStatus,
        AttributionSourceType financialSourceType,
        UUID financialSourceId,
        UUID attributionId,
        ProjectOcrMatchMethod matchMethod,
        BigDecimal ocrAmount,
        String ocrCurrency,
        LocalDate ocrDate,
        String counterpartyName,
        String category,
        String documentType,
        BigDecimal confidence,
        BigDecimal discrepancyAmount,
        String discrepancyNotes,
        boolean hasDiscrepancy,
        boolean isDuplicate,
        long duplicateCount,
        String linkProvenance,
        String notes,
        UUID confirmedByUserId,
        Instant confirmedAt,
        Instant invalidatedAt,
        UUID invalidatedBy,
        String invalidationReason,
        Instant createdAt,
        BigDecimal matchedTransactionAmount,
        LocalDate matchedTransactionDate,
        String matchedTransactionDescription
) {}
