package com.app.sme_health_backend.identity.dto;

import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;

public record UpdateMembershipRequest(MembershipRole role, MembershipStatus status) {}
