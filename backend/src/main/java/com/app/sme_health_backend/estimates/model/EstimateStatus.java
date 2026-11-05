package com.app.sme_health_backend.estimates.model;

/** Estimate identity lifecycle. ARCHIVED is terminal and read-only; revision history is never removed. */
public enum EstimateStatus {
    ACTIVE,
    ARCHIVED;

    public boolean allowsChanges() { return this == ACTIVE; }
}
