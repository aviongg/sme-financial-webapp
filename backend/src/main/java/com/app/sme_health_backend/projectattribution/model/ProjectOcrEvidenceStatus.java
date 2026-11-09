package com.app.sme_health_backend.projectattribution.model;

public enum ProjectOcrEvidenceStatus {
    EXTRACTED_UNREVIEWED,
    REVIEWED_UNMATCHED,
    MATCHED_TO_POSTED_SOURCE,
    ATTRIBUTED,
    INVALIDATED,
    REVERSED
}
