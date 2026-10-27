package com.app.sme_health_backend.commercial.dto;

/** Raw line input. Decimal values stay strings until the authoritative calculator parses them. */
public record CommercialLineRequest(
        String description,
        String quantity,
        String unit,
        String unitPrice
) { }
