package com.app.sme_health_backend.liquidity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Estimated end-of-day project funding position for a single calendar day.
 * <p>
 * Within-day events are netted. This estimates the end-of-day funding position;
 * it does not prove funds were sufficient at every intra-day instant.
 *
 * @param date            calendar date
 * @param plannedInflows  aggregate planned cash inflows on this date
 * @param plannedOutflows aggregate planned cash outflows on this date
 * @param netFlow         net planned cash flow for this date (inflows - outflows)
 * @param closingPosition cumulative closing cash position at end of this date
 */
public record DailyProjectFundingPosition(
        LocalDate date,
        BigDecimal plannedInflows,
        BigDecimal plannedOutflows,
        BigDecimal netFlow,
        BigDecimal closingPosition
) {
    /**
     * Checks if this day has a funding deficit (closing position below zero).
     *
     * @return true if closing position is negative
     */
    public boolean isShortfall() {
        return closingPosition.compareTo(BigDecimal.ZERO) < 0;
    }
}
