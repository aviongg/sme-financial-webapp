package com.app.sme_health_backend.security.service;

public enum AuthenticationStage {
    PASSWORD_CHANGE_REQUIRED,
    MFA_ENROLLMENT_REQUIRED,
    MFA_CHALLENGE_REQUIRED,
    FULLY_AUTHENTICATED
}
