package com.app.sme_health_backend.identity.dto;

import com.app.sme_health_backend.identity.model.MembershipRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InviteMemberRequest(@NotBlank @Email @Size(max = 320) String email, @NotNull MembershipRole role) {}
