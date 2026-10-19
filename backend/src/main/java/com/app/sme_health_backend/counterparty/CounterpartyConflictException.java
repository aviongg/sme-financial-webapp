package com.app.sme_health_backend.counterparty;

/**
 * Exception thrown when a counterparty operation encounters a version or concurrency conflict.
 */
public class CounterpartyConflictException extends RuntimeException {

    public CounterpartyConflictException(String message) {
        super(message);
    }
}
