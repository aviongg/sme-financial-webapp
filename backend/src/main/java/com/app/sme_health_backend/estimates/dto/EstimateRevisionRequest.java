package com.app.sme_health_backend.estimates.dto;

/**
 * The canonical f06-preview-v1 assumptions for a DRAFT revision. {@code expectedVersion} is the estimate version
 * when creating a revision and the revision version when editing one. Outputs are always calculated by the server.
 */
public record EstimateRevisionRequest(PricingPreviewRequest content, Long expectedVersion) { }
