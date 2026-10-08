package com.app.sme_health_backend.identity.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SelectActiveBusinessRequest(
        @NotNull(message = "businessId is required")
        UUID businessId
) {}
