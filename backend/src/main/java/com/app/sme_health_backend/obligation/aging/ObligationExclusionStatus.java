package com.app.sme_health_backend.obligation.aging;

/**
 * Status explaining why an obligation is excluded from active positive aging buckets.
 */
public enum ObligationExclusionStatus {
    NOT_YET_ISSUED,
    VOIDED,
    SETTLED
}
