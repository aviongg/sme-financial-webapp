package com.app.sme_health_backend.estimates.dto;

import java.util.List;

/** No business, currency, financial source, or persistence authority is accepted from the caller. */
public record PricingPreviewRequest(List<EstimateLineRequest> lines, String saleableQuantity,
                                    PricingMode pricingMode, String pricingValue) {
    public enum PricingMode { PROPOSED_PRICE, TARGET_MARGIN, TARGET_MARKUP }
}
