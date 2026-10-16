package com.app.sme_health_backend.accounting.draft;

/**
 * Lifecycle states of a persistent record draft.
 */
public enum DraftStatus {
    /**
     * In-progress entry. Can be edited, discarded, or recorded.
     */
    DRAFT,

    /**
     * Successfully posted as a financial transaction. Immutable.
     */
    RECORDED,

    /**
     * Abandoned draft. Immutable. Preserved for audit history.
     */
    DISCARDED
}
