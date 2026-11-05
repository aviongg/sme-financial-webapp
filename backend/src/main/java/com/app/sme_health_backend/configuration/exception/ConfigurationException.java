package com.app.sme_health_backend.configuration.exception;

public class ConfigurationException extends RuntimeException {
    private final String code;
    private final int status;
    public ConfigurationException(String code, int status, String message) {
        super(message); this.code = code; this.status = status;
    }
    public String code() { return code; }
    public int status() { return status; }
    public static ConfigurationException invalid(String message) {
        return new ConfigurationException("invalid_configuration", 400, message);
    }
}
