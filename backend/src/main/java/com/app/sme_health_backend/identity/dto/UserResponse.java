package com.app.sme_health_backend.identity.dto;

import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.AccountStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        AccountStatus accountStatus,
        boolean mustChangePassword,
        OffsetDateTime createdAt
) {
    public static UserResponse fromEntity(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAccountStatus(),
                user.isMustChangePassword(),
                user.getCreatedAt()
        );
    }
}
