package com.app.sme_health_backend.obligation;

/**
 * Exception thrown when an obligation operation encounters a conflict or overpayment.
 */
public class ObligationConflictException extends RuntimeException {

    public ObligationConflictException(String message) {
        super(message);
    }
}
