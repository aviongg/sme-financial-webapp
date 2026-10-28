package com.app.sme_health_backend.records.projection.dto;

/**
 * Summary of unposted draft entries and recurring rule projections excluded from actual monthly ledger metrics.
 */
public record MonthlyDraftSummary(
        int unpostedDraftCount,
        String unpostedDraftAmount,
        int recurringProjectedCount,
        String recurringProjectedAmount,
        boolean isEstimate
) {}
