package com.app.sme_health_backend.estimates.dto;

public record EstimateUpdateRequest(String code, String name, String description, Long expectedVersion) { }
