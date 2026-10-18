package com.app.sme_health_backend.obligation.aging;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Detailed aging calculation evaluation for a single obligation as of a given date.
 *
 * @param obligationId    stable unique obligation identifier
 * @param direction       receivable or payable
 * @param issueDate       original issue date
 * @param dueDate         due date if specified, otherwise null
 * @param originalAmount  initial obligation amount
 * @param asOfOutstanding balance outstanding as of the requested calculation date
 * @param daysOverdue     number of calendar days overdue if overdue, otherwise null
 * @param assignedBucket  aging bucket if active/eligible, otherwise null
 * @param exclusionStatus reason for exclusion if not in active positive aging, otherwise null
 */
public record ObligationAgingDetail(
        String obligationId,
        ObligationDirection direction,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal originalAmount,
        BigDecimal asOfOutstanding,
        Integer daysOverdue,
        AgingBucket assignedBucket,
        ObligationExclusionStatus exclusionStatus
) {
    /**
     * Checks if this obligation is actively included in positive aging buckets.
     *
     * @return true if exclusionStatus is null
     */
    public boolean isActive() {
        return exclusionStatus == null;
    }

    /**
     * Checks if this obligation is currently overdue relative to the as-of date.
     *
     * @return true if assignedBucket represents an overdue bucket
     */
    public boolean isOverdue() {
        return assignedBucket != null && assignedBucket.isOverdue();
    }
}
