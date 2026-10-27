package com.app.sme_health_backend.projects.model;

/**
 * Budget version lifecycle.
 * Only one APPROVED budget version may be active per project at a time.
 * Approved and superseded versions are permanently immutable.
 */
public enum ProjectBudgetStatus {
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
