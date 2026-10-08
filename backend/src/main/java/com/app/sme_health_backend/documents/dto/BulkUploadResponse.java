package com.app.sme_health_backend.documents.dto;

import java.util.List;

public record BulkUploadResponse(
        int totalCount,
        List<DocumentResponse> documents
) {}
