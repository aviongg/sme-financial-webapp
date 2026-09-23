package com.app.sme_health_backend.identity.dto;

import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;

import java.util.UUID;

public record BusinessResponse(
        UUID businessId,
        String businessType,
        String languagePreference,
        MembershipRole role,
        MembershipStatus membershipStatus,
        boolean active
) {}
