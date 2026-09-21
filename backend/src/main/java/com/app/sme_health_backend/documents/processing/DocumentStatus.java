package com.app.sme_health_backend.documents.processing;

/** Matches the existing uploaded_documents schema exactly. */
public enum DocumentStatus {
    pending, processing, extracted, needs_review, confirmed, failed
}
