package com.app.sme_health_backend.recurring.exception;

public class RecurringConflictException extends RuntimeException {
    public RecurringConflictException(String message) {
        super(message);
    }
}
