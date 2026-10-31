package com.app.sme_health_backend.samples.model;

/**
 * Plan version lifecycle.
 * Only one APPROVED plan version may be active per activity at a time.
 * Approved and superseded versions are permanently immutable.
 */
public enum SamplePlanStatus {
    DRAFT,
    APPROVED,
    SUPERSEDED;

    public boolean isImmutable() {
        return this == APPROVED || this == SUPERSEDED;
    }

    public boolean canDeleteDraft() {
        return this == DRAFT;
    }
}
