package com.app.sme_health_backend.estimates;

import com.app.sme_health_backend.estimates.dto.PricingPreviewRequest;
import com.app.sme_health_backend.estimates.model.EstimateRevisionRecord;
import com.app.sme_health_backend.estimates.model.EstimateRevisionStatus;
import com.app.sme_health_backend.estimates.model.EstimateStatus;
import com.app.sme_health_backend.estimates.service.EstimateService;
import com.app.sme_health_backend.estimates.service.PricingCalculator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.app.sme_health_backend.estimates.EstimateTestRevisions.*;
import static com.app.sme_health_backend.estimates.dto.EstimateLineRequest.CostGroup.*;
import static com.app.sme_health_backend.estimates.dto.PricingPreviewRequest.PricingMode.*;
import static org.junit.jupiter.api.Assertions.*;

/** A persisted revision is exactly the Phase A preview: same engine, same serializer, full precision. */
class EstimatePersistenceParityTests {
    private final UUID business = UUID.randomUUID();

    private void assertParity(PricingPreviewRequest request) {
        EstimateRevisionRecord revision = stored(request);
        assertEquals(CALCULATOR.preview(business, request), PricingCalculator.response(business, EstimateService.stored(revision)));
        assertTrue(revision.matches(CALCULATOR.calculate(request)));
    }

    @Test void everyPricingModeAndAvailabilityStateRoundTripsThroughStorageUnchanged() {
        assertParity(request(TARGET_MARGIN, "0.20", "10", line(MATERIAL, "1", "800.00")));
        assertParity(request(TARGET_MARKUP, "0.20", "10", line(MATERIAL, "1", "800.00")));
        assertParity(request(PROPOSED_PRICE, "0", "0", line(LABOUR, "3", "0")));
        assertParity(request(PROPOSED_PRICE, "400", null, line(MATERIAL, "2", "300"), line(FREIGHT, "1", "200")));
        assertParity(request(TARGET_MARGIN, "0.30", "7", line(OVERHEAD, "1.0000001", "1000")));
    }

    @Test void phaseAExtremeValuesFitV33ColumnsWithoutNarrowing() {
        String max = "999999999999.999999999999";
        // Largest quantities/costs, the smallest non-zero margin denominator and the largest markup.
        assertParity(request(TARGET_MARGIN, "0.999999999999", max, line(MATERIAL, max, max), line(LABOUR, max, max)));
        assertParity(request(TARGET_MARKUP, max, "0.000000000001", line(OTHER, max, max)));
        assertParity(request(PROPOSED_PRICE, "0.000000000001", "0.000000000001", line(OTHER, "0.000000000001", "0.000000000001")));
        assertParity(request(PROPOSED_PRICE, max, "0.000000000001", line(OTHER, "0.000000000001", "0.000000000001")));
        var lines = java.util.Collections.nCopies(100, line(MATERIAL, max, max));
        assertParity(new PricingPreviewRequest(List.copyOf(lines), null, TARGET_MARKUP, max));
    }

    @Test void approvedOutputsMatchOnlyTheirOwnInputs() {
        var request = request(TARGET_MARKUP, "0.20", "10", line(MATERIAL, "1", "800.00"));
        EstimateRevisionRecord revision = stored(request);
        assertFalse(revision.matches(CALCULATOR.calculate(request(TARGET_MARKUP, "0.21", "10", line(MATERIAL, "1", "800.00")))));
        assertFalse(revision.matches(CALCULATOR.calculate(request(TARGET_MARKUP, "0.20", null, line(MATERIAL, "1", "800.00")))));
        assertEquals(0, revision.plannedBatchPrice().compareTo(new BigDecimal("960")));
    }

    @Test void lifecycleVocabulary() {
        assertTrue(EstimateStatus.ACTIVE.allowsChanges());
        assertFalse(EstimateStatus.ARCHIVED.allowsChanges());
        assertFalse(EstimateRevisionStatus.DRAFT.isImmutable());
        assertTrue(EstimateRevisionStatus.APPROVED.isImmutable());
        assertTrue(EstimateRevisionStatus.SUPERSEDED.isImmutable());
        assertEquals("f06-preview-v1", PricingCalculator.CALCULATION_VERSION);
    }
}
