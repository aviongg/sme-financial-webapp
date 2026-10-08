package com.app.sme_health_backend.identity.dto;

import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MembershipResponse(UUID id, String email, MembershipRole role, MembershipStatus status, OffsetDateTime createdAt) {}
