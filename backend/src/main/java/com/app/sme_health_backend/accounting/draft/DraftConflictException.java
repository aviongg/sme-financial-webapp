package com.app.sme_health_backend.accounting.draft;

/**
 * Thrown when an optimistic concurrency version mismatch, lifecycle conflict,
 * or duplicate recording attempt occurs.
 */
public class DraftConflictException extends RuntimeException {

    public DraftConflictException(String message) {
        super(message);
    }
}
