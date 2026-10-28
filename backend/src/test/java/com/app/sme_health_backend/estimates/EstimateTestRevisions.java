package com.app.sme_health_backend.estimates;

import com.app.sme_health_backend.estimates.dto.EstimateLineRequest;
import com.app.sme_health_backend.estimates.dto.PricingPreviewRequest;
import com.app.sme_health_backend.estimates.model.EstimateRevisionRecord;
import com.app.sme_health_backend.estimates.model.EstimateRevisionStatus;
import com.app.sme_health_backend.estimates.service.EstimateCostCalculator;
import com.app.sme_health_backend.estimates.service.EstimateCostCalculator.RawLine;
import com.app.sme_health_backend.estimates.service.PricingCalculator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Builds revisions exactly as V36 would return them: every value padded to its column scale. */
final class EstimateTestRevisions {
    private EstimateTestRevisions() { }
    static final PricingCalculator CALCULATOR = new PricingCalculator(new EstimateCostCalculator());

    static EstimateLineRequest line(EstimateLineRequest.CostGroup group, String quantity, String unitCost) {
        return new EstimateLineRequest(group, "Line " + group, quantity, "unit", unitCost);
    }
    static PricingPreviewRequest request(PricingPreviewRequest.PricingMode mode, String value, String saleable, EstimateLineRequest... lines) {
        return new PricingPreviewRequest(List.of(lines), saleable, mode, value);
    }
    static EstimateRevisionRecord stored(PricingCalculator.Result result) {
        List<RawLine> lines = result.costs().rawLines().stream().map(line -> new RawLine(line.costGroup(), line.description(),
                pad(line.quantity(), 24, 12), line.unit(), pad(line.unitCost(), 24, 12), pad(line.plannedCost(), 48, 24))).toList();
        OffsetDateTime now = OffsetDateTime.now();
        UUID user = UUID.randomUUID();
        return new EstimateRevisionRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, EstimateRevisionStatus.APPROVED,
                result.pricingMode(), pad(result.pricingValue(), 24, 12), pad(result.saleableQuantity(), 24, 12),
                pad(result.costs().total(), 50, 24), pad(result.price(), 75, 36), pad(result.contribution(), 75, 36), pad(result.margin(), 63, 24),
                pad(result.markup(), 87, 24), pad(result.perUnit(), 75, 24), PricingCalculator.CALCULATION_VERSION, 1L, user, user, now, now,
                user, now, null, null, lines);
    }
    static EstimateRevisionRecord stored(PricingPreviewRequest request) {
        return stored(CALCULATOR.calculate(request));
    }
    /**
     * PostgreSQL NUMERIC(p, s) pads to scale s and never changes a value that fits; a larger scale would be rounded
     * and more than p - s integer digits would overflow. Either means V36 narrowed the Phase A domain.
     */
    private static BigDecimal pad(BigDecimal value, int precision, int scale) {
        if (value == null) return null;
        if (value.scale() > scale) throw new AssertionError("Value would be rounded by its V36 column: " + value);
        BigDecimal padded = value.setScale(scale);
        if (padded.precision() - scale > precision - scale) throw new AssertionError("Value overflows its V36 column: " + value);
        return padded;
    }
}
