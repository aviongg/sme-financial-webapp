package com.app.sme_health_backend.estimates.model;

/** At most one DRAFT and one APPROVED revision per estimate; APPROVED and SUPERSEDED content is immutable. */
public enum EstimateRevisionStatus {
    DRAFT,
    APPROVED,
    SUPERSEDED;

    public boolean isImmutable() { return this != DRAFT; }
}
