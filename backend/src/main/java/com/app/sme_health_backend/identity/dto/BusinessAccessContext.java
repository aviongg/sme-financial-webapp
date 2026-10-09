package com.app.sme_health_backend.identity.dto;

import com.app.sme_health_backend.identity.model.MembershipRole;

import java.util.Objects;
import java.util.UUID;

public record BusinessAccessContext(
        UUID userId,
        UUID businessId,
        MembershipRole role
) {
    public BusinessAccessContext {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(businessId, "businessId must not be null");
        Objects.requireNonNull(role, "role must not be null");
    }
}
