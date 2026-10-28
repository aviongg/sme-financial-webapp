package com.app.sme_health_backend.estimates.dto;

/** Estimate identity plus an optional first DRAFT revision. No business, actor, status or totals are accepted. */
public record EstimateCreateRequest(String code, String name, String description, PricingPreviewRequest draft) { }
