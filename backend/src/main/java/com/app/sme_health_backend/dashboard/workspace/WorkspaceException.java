package com.app.sme_health_backend.dashboard.workspace;

public class WorkspaceException extends RuntimeException {
    private final String code;
    private final int status;
    public WorkspaceException(String code, int status, String message) { super(message); this.code = code; this.status = status; }
    public String code() { return code; }
    public int status() { return status; }
}
