package com.app.sme_health_backend.projectattribution.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record OcrEvidenceCandidateResponse(
        UUID documentId,
        String originalFilename,
        String processingStatus,
        BigDecimal ocrAmount,
        String ocrCurrency,
        LocalDate ocrDate,
        String counterpartyName,
        String category,
        String documentType,
        BigDecimal confidence,
        LocalDateTime uploadTimestamp,
        boolean isAlreadyLinkedToThisProject,
        long alreadyLinkedProjectCount
) {}
