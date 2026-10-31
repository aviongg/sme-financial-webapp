package com.app.sme_health_backend.inventory.dto;

import java.util.List;

/** Decimal fields are strings so they are parsed exactly; numeric JSON is rejected by the reader. */
public record ValuationPreviewRequest(
        String openingQuantity,
        String openingValue,
        List<Step> steps
) {
    public record Step(String type, String quantity, String value) { }
}
