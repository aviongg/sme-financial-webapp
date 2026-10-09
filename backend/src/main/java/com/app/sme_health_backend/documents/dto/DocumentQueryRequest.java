package com.app.sme_health_backend.documents.dto;

import com.app.sme_health_backend.documents.processing.DocumentStatus;

public record DocumentQueryRequest(
        DocumentStatus status,
        String month
) {}
