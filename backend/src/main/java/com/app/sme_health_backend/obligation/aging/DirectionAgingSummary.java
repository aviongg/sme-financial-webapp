package com.app.sme_health_backend.obligation.aging;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Aggregated aging breakdown for a single obligation direction (receivables or payables).
 *
 * @param direction          receivable or payable
 * @param totalOutstanding   total positive balance outstanding across all active obligations
 * @param overdueOutstanding total balance outstanding for obligations past their due date
 * @param totalActiveCount   count of obligations actively included in aging
 * @param overdueCount       count of obligations with overdue status
 * @param buckets            immutable breakdown across all standard aging buckets
 * @param settledCount       count of obligations fully settled (0 balance) as of the date
 * @param voidedCount        count of obligations voided on or before the date
 * @param notYetIssuedCount  count of obligations issued after the date
 */
public record DirectionAgingSummary(
        ObligationDirection direction,
        BigDecimal totalOutstanding,
        BigDecimal overdueOutstanding,
        int totalActiveCount,
        int overdueCount,
        Map<AgingBucket, BucketSummary> buckets,
        int settledCount,
        int voidedCount,
        int notYetIssuedCount
) {
    public DirectionAgingSummary {
        buckets = buckets == null ? Map.of() : Map.copyOf(buckets);
    }

    /**
     * Gets summary for a specific bucket, returning a zero-value summary if not present.
     *
     * @param bucket aging bucket category
     * @return bucket summary
     */
    public BucketSummary getBucket(AgingBucket bucket) {
        return buckets.getOrDefault(bucket, new BucketSummary(bucket, BigDecimal.ZERO.setScale(2), 0));
    }
}
