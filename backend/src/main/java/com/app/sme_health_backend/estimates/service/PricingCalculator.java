package com.app.sme_health_backend.estimates.service;

import com.app.sme_health_backend.estimates.dto.PricingPreviewRequest;
import com.app.sme_health_backend.estimates.dto.PricingPreviewResponse;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.UUID;
import static com.app.sme_health_backend.estimates.service.EstimateDecimals.*;

/** Stateless manual planning assumptions only. No financial repositories or posting services. */
@Service
public class PricingCalculator {
    private final EstimateCostCalculator costs;
    public PricingCalculator(EstimateCostCalculator costs) { this.costs = costs; }

    public PricingPreviewResponse preview(UUID businessId, PricingPreviewRequest input) {
        if (businessId == null) throw new IllegalArgumentException("Server business context is required");
        if (input == null) throw new EstimateValidationException("request", "required");
        if (input.pricingMode() == null) throw new EstimateValidationException("pricingMode", "required");
        var cost = costs.calculate(input.lines());
        BigDecimal target = parse(input.pricingValue(), "pricingValue");
        BigDecimal quantity = input.saleableQuantity() == null ? null : parse(input.saleableQuantity(), "saleableQuantity");
        BigDecimal price = switch (input.pricingMode()) {
            case PROPOSED_PRICE -> target;
            case TARGET_MARGIN -> {
                if (target.compareTo(BigDecimal.ONE) >= 0)
                    throw new EstimateValidationException("pricingValue", "margin_out_of_range");
                yield divide(cost.total(), BigDecimal.ONE.subtract(target));
            }
            case TARGET_MARKUP -> cost.total().multiply(BigDecimal.ONE.add(target));
        };
        BigDecimal contribution = price.subtract(cost.total());
        BigDecimal margin = price.signum() > 0 ? divide(contribution, price) : null;
        BigDecimal markup = cost.total().signum() > 0 ? divide(contribution, cost.total()) : null;
        BigDecimal perUnit = quantity != null && quantity.signum() > 0 ? divide(price, quantity) : null;
        return new PricingPreviewResponse(businessId, "PKR", "APPLICATION_FIXED_PKR", true,
                cost.lines(), cost.groupSubtotals(), amount(cost.total()), decimal(quantity), input.pricingMode(), decimal(target),
                amount(price), amount(contribution), decimal(margin), decimal(markup), amount(perUnit),
                new PricingPreviewResponse.UnavailableReasons(margin == null ? "ZERO_PRICE" : null,
                        markup == null ? "ZERO_COST" : null,
                        quantity == null ? "SALEABLE_QUANTITY_UNKNOWN" : quantity.signum() == 0 ? "ZERO_SALEABLE_QUANTITY" : null),
                new PricingPreviewResponse.Calculation("f06-preview-v1", DIVISION_SCALE, DIVISION_ROUNDING.name(), null));
    }
}
