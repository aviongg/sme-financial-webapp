package com.app.sme_health_backend.projects.service;

public class ProjectException extends RuntimeException {
    private final String code;
    private final int status;

    public ProjectException(String code, int status, String message) {
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
