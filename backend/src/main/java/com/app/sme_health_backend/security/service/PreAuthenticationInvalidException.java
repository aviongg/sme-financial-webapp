package com.app.sme_health_backend.security.service;

import org.springframework.security.authentication.BadCredentialsException;

public class PreAuthenticationInvalidException extends BadCredentialsException {
    public PreAuthenticationInvalidException() {
        super("Authentication challenge is no longer valid. Please log in again.");
    }
}
