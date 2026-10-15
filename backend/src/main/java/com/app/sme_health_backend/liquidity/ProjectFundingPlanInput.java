package com.app.sme_health_backend.liquidity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Input contract for calculating project funding gaps over a defined horizon.
 *
 * @param currency     currency code (strictly "PKR" in this version)
 * @param openingFunds explicit opening funds available to this project (non-negative)
 * @param horizonStart inclusive start date of the calculation horizon
 * @param horizonEnd   inclusive end date of the calculation horizon
 * @param events       planned cash inflow and outflow events
 */
public record ProjectFundingPlanInput(
        String currency,
        BigDecimal openingFunds,
        LocalDate horizonStart,
        LocalDate horizonEnd,
        List<PlannedProjectCashEvent> events
) {
    public ProjectFundingPlanInput {
        events = events == null ? null : Collections.unmodifiableList(new ArrayList<>(events));
    }
}
