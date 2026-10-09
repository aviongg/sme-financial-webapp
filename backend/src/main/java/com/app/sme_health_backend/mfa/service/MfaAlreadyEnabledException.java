package com.app.sme_health_backend.mfa.service;

public class MfaAlreadyEnabledException extends RuntimeException {
    public MfaAlreadyEnabledException() {
        super("MFA is already enabled. Disable the current factor before enrolling again.");
    }
}
