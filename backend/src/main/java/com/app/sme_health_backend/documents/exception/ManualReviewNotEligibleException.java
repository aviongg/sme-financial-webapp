package com.app.sme_health_backend.documents.exception;

/** The document cannot enter the manual-review fallback (DEF-01); surfaced as 409 manual_review_not_eligible. */
public class ManualReviewNotEligibleException extends RuntimeException {
    public ManualReviewNotEligibleException(String message) {
        super(message);
    }
}
