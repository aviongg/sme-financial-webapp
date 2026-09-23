package com.app.sme_health_backend.identity.dto;

public record CsrfResponse(
        String token,
        String headerName,
        String parameterName
) {
}
