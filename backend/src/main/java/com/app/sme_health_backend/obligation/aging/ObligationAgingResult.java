package com.app.sme_health_backend.obligation.aging;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable result of an obligation aging calculation across receivables and payables.
 *
 * @param asOfDate    effective historical calculation date
 * @param currency    iso currency code (strictly PKR in this version)
 * @param receivables aggregated summary for receivable obligations
 * @param payables    aggregated summary for payable obligations
 * @param details     deterministic list of per-obligation evaluations
 */
public record ObligationAgingResult(
        LocalDate asOfDate,
        String currency,
        DirectionAgingSummary receivables,
        DirectionAgingSummary payables,
        List<ObligationAgingDetail> details
) {
    public ObligationAgingResult {
        details = details == null ? List.of() : List.copyOf(details);
    }

    /**
     * Total outstanding receivables balance.
     */
    public BigDecimal receivableTotalOutstanding() {
        return receivables != null ? receivables.totalOutstanding() : BigDecimal.ZERO.setScale(2);
    }

    /**
     * Total overdue receivables balance.
     */
    public BigDecimal receivableOverdueOutstanding() {
        return receivables != null ? receivables.overdueOutstanding() : BigDecimal.ZERO.setScale(2);
    }

    /**
     * Total outstanding payables balance.
     */
    public BigDecimal payableTotalOutstanding() {
        return payables != null ? payables.totalOutstanding() : BigDecimal.ZERO.setScale(2);
    }

    /**
     * Total overdue payables balance.
     */
    public BigDecimal payableOverdueOutstanding() {
        return payables != null ? payables.overdueOutstanding() : BigDecimal.ZERO.setScale(2);
    }

    /**
     * Total outstanding balance for the given direction.
     */
    public BigDecimal totalOutstanding(ObligationDirection direction) {
        return direction == ObligationDirection.RECEIVABLE ? receivableTotalOutstanding() : payableTotalOutstanding();
    }

    /**
     * Total overdue balance for the given direction.
     */
    public BigDecimal overdueOutstanding(ObligationDirection direction) {
        return direction == ObligationDirection.RECEIVABLE ? receivableOverdueOutstanding() : payableOverdueOutstanding();
    }
}
