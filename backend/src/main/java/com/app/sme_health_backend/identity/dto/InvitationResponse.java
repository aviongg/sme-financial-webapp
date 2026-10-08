package com.app.sme_health_backend.identity.dto;

import com.app.sme_health_backend.identity.model.MembershipRole;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InvitationResponse(UUID id, String businessName, MembershipRole role, OffsetDateTime createdAt) {}
