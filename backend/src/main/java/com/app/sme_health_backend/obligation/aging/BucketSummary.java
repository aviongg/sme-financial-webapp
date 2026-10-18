package com.app.sme_health_backend.obligation.aging;

import java.math.BigDecimal;

/**
 * Summary aggregates for a single aging bucket.
 *
 * @param bucket           aging bucket category
 * @param totalOutstanding aggregate outstanding balance within this bucket
 * @param count            number of obligations in this bucket
 */
public record BucketSummary(
        AgingBucket bucket,
        BigDecimal totalOutstanding,
        int count
) {
}
