package com.app.sme_health_backend.liquidity;

/**
 * Thrown when project funding gap calculation input fails validation.
 * <p>
 * Provides an optional field name indicating the input parameter that failed validation.
 */
public class ProjectFundingGapException extends IllegalArgumentException {

    private final String field;

    public ProjectFundingGapException(String message) {
        super(message);
        this.field = null;
    }

    public ProjectFundingGapException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
