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
    public static final String CALCULATION_VERSION = "f06-preview-v1";
    private final EstimateCostCalculator costs;
    public PricingCalculator(EstimateCostCalculator costs) { this.costs = costs; }

    /** Exact f06-preview-v1 values before serialization; persisted revisions store exactly these. */
    public record Result(EstimateCostCalculator.Costs costs, PricingPreviewRequest.PricingMode pricingMode, BigDecimal pricingValue,
                         BigDecimal saleableQuantity, BigDecimal price, BigDecimal contribution, BigDecimal margin,
                         BigDecimal markup, BigDecimal perUnit) {
        public String marginUnavailable() { return margin == null ? "ZERO_PRICE" : null; }
        public String markupUnavailable() { return markup == null ? "ZERO_COST" : null; }
        public String perUnitUnavailable() {
            return saleableQuantity == null ? "SALEABLE_QUANTITY_UNKNOWN" : saleableQuantity.signum() == 0 ? "ZERO_SALEABLE_QUANTITY" : null;
        }
    }

    public PricingPreviewResponse preview(UUID businessId, PricingPreviewRequest input) {
        if (businessId == null) throw new IllegalArgumentException("Server business context is required");
        return response(businessId, calculate(input));
    }

    public Result calculate(PricingPreviewRequest input) {
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
        return new Result(cost, input.pricingMode(), target, quantity, price, contribution, margin, markup, perUnit);
    }

    /** The single f06-preview-v1 serializer, shared by the stateless preview and persisted revisions. */
    public static PricingPreviewResponse response(UUID businessId, Result result) {
        var cost = result.costs();
        return new PricingPreviewResponse(businessId, "PKR", "APPLICATION_FIXED_PKR", true,
                cost.lines(), cost.groupSubtotals(), amount(cost.total()), decimal(result.saleableQuantity()), result.pricingMode(),
                decimal(result.pricingValue()), amount(result.price()), amount(result.contribution()), decimal(result.margin()),
                decimal(result.markup()), amount(result.perUnit()),
                new PricingPreviewResponse.UnavailableReasons(result.marginUnavailable(), result.markupUnavailable(), result.perUnitUnavailable()),
                new PricingPreviewResponse.Calculation(CALCULATION_VERSION, DIVISION_SCALE, DIVISION_ROUNDING.name(), null));
    }
}
