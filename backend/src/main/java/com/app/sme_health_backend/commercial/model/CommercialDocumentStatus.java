package com.app.sme_health_backend.commercial.model;

/**
 * Phase A lifecycle: DRAFT -> FINALIZED -> VOID.
 * ISSUED and APPROVED are deliberately absent; they are reserved for future C04 obligation integration.
 */
public enum CommercialDocumentStatus {
    DRAFT,
    FINALIZED,
    VOID;

    public boolean isEditable() {
        return this == DRAFT;
    }

    public boolean canFinalize() {
        return this == DRAFT;
    }

    public boolean canVoid() {
        return this == FINALIZED;
    }

    public boolean canDelete() {
        return this == DRAFT;
    }
}
