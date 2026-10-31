package com.app.sme_health_backend.samples.service;

public class SampleException extends RuntimeException {
    private final String code;
    private final int status;

    public SampleException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }
}
