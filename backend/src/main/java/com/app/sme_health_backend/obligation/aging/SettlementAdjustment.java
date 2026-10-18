package com.app.sme_health_backend.obligation.aging;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable dated settlement adjustment modifying an obligation's outstanding balance.
 *
 * @param adjustmentId  stable unique identifier for this adjustment
 * @param effectiveDate calendar date on which the adjustment took effect
 * @param type          reduction or restoration of outstanding amount
 * @param amount        positive adjustment amount in obligation currency
 */
public record SettlementAdjustment(
        String adjustmentId,
        LocalDate effectiveDate,
        SettlementAdjustmentType type,
        BigDecimal amount
) {
}
