package com.app.sme_health_backend.documents.ocr;

import java.util.Objects;
import java.util.UUID;

public record OcrRequest(
        UUID documentId,
        byte[] fileBytes,
        String filename,
        String contentType,
        OcrExtraction.DocumentType documentTypeHint
) {
    public OcrRequest {
        Objects.requireNonNull(documentTypeHint, "document type hint is required");
        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("Document file bytes are required");
        }
        filename = (filename == null || filename.isBlank()) ? "document.bin" : filename.strip();
        contentType = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType.strip();
    }

    public OcrRequest(byte[] fileBytes, String contentType, OcrExtraction.DocumentType documentTypeHint) {
        this(null, fileBytes, "document.bin", contentType, documentTypeHint);
    }
}
