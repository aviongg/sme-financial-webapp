package com.app.sme_health_backend.documents.dto;

import com.app.sme_health_backend.documents.processing.DocumentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID userId,
        String fileUrl,
        String originalFilename,
        String contentType,
        Long fileSizeBytes,
        LocalDateTime uploadTimestamp,
        DocumentStatus processingStatus,
        String documentTypeHint,
        String extractedData,
        String confirmedData,
        String linkedMonth,
        String failureReason,
        LocalDateTime confirmedAt
) {}
