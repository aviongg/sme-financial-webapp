package com.app.sme_health_backend.liquidity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable calculation result answering how much additional funding a project
 * needs and when, across a defined plan horizon.
 * <p>
 * The daily curve represents the plan before hypothetical extra funding.
 *
 * @param currency                  currency code (strictly PKR in this version)
 * @param openingFunds              explicit opening funds available at start of horizon
 * @param horizonStart              inclusive start date of calculation horizon
 * @param horizonEnd                inclusive end date of calculation horizon
 * @param totalInflow               aggregate planned inflows across the horizon
 * @param totalOutflow              aggregate planned outflows across the horizon
 * @param finalClosingPosition      estimated closing position on horizonEnd
 * @param additionalFundingRequired max(0, -minimum closing position)
 * @param firstShortfallDate        first date where closing position falls below zero (null if no shortfall)
 * @param firstPeakShortfallDate    earliest date reaching the minimum negative position (null if no shortfall)
 * @param recoveryDate              earliest date after first shortfall from which closing positions
 *                                  remain non-negative through the supplied horizon (null if no recovery or no shortfall)
 * @param shortfallDayCount         count of calendar days with closing position below zero
 * @param dailyCurve                unmodifiable continuous daily curve across every calendar day in the horizon
 */
public record ProjectFundingGapResult(
        String currency,
        BigDecimal openingFunds,
        LocalDate horizonStart,
        LocalDate horizonEnd,
        BigDecimal totalInflow,
        BigDecimal totalOutflow,
        BigDecimal finalClosingPosition,
        BigDecimal additionalFundingRequired,
        LocalDate firstShortfallDate,
        LocalDate firstPeakShortfallDate,
        LocalDate recoveryDate,
        int shortfallDayCount,
        List<DailyProjectFundingPosition> dailyCurve
) {
    public ProjectFundingGapResult {
        dailyCurve = dailyCurve == null ? List.of() : List.copyOf(dailyCurve);
    }

    /**
     * Checks if this project plan experiences any funding deficit during the horizon.
     *
     * @return true if additional funding is required
     */
    public boolean hasShortfall() {
        return additionalFundingRequired != null && additionalFundingRequired.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Checks if the project plan recovers from all deficits to a non-negative closing
     * position through the remainder of the supplied horizon.
     *
     * @return true if recoveryDate is non-null
     */
    public boolean isRecoveredWithinHorizon() {
        return recoveryDate != null;
    }
}
