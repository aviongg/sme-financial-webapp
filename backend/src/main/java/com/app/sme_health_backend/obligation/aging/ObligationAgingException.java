package com.app.sme_health_backend.obligation.aging;

/**
 * Thrown when obligation aging calculation input fails validation.
 * <p>
 * Provides an optional field name indicating the input parameter or property
 * that failed validation.
 */
public class ObligationAgingException extends IllegalArgumentException {

    private final String field;

    public ObligationAgingException(String message) {
        super(message);
        this.field = null;
    }

    public ObligationAgingException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
