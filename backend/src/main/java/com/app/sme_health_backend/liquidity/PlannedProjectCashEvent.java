package com.app.sme_health_backend.liquidity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A dated cash event (inflow or outflow) planned for a project.
 *
 * @param eventId     stable unique identifier for the event
 * @param date        effective date of the event within the plan horizon
 * @param direction   cash flow direction (INFLOW or OUTFLOW)
 * @param amount      monetary amount (strictly positive, scale <= 2)
 * @param description optional explanatory description or reference
 */
public record PlannedProjectCashEvent(
        String eventId,
        LocalDate date,
        ProjectCashFlowDirection direction,
        BigDecimal amount,
        String description
) {
    /**
     * Convenience constructor without optional description.
     */
    public PlannedProjectCashEvent(String eventId, LocalDate date, ProjectCashFlowDirection direction, BigDecimal amount) {
        this(eventId, date, direction, amount, null);
    }
}
