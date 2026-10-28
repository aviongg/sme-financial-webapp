package com.app.sme_health_backend.estimates.service;

/** Domain failure with a stable machine-readable code and HTTP status. */
public class EstimateException extends RuntimeException {
    private final String code;
    private final int status;
    public EstimateException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
    public String code() { return code; }
    public int status() { return status; }
}
