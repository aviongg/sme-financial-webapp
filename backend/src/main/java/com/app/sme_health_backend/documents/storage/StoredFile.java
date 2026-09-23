package com.app.sme_health_backend.documents.storage;

public record StoredFile(
        String storagePath,
        String originalFilename,
        String contentType,
        long sizeBytes
) {}
