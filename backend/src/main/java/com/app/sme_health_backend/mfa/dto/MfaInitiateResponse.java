package com.app.sme_health_backend.mfa.dto;

public record MfaInitiateResponse(
        String secret,
        String provisioningUri
) {
}
