package com.app.sme_health_backend.search.dto;

public record SearchRequest(
        String query,
        String type
) {}
