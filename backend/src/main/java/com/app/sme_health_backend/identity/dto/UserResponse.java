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
        OffsetDateTime createdAt,
        String platformRole,
        String authStage
) {
    public UserResponse(
            UUID id,
            String email,
            String fullName,
            AccountStatus accountStatus,
            boolean mustChangePassword,
            OffsetDateTime createdAt
    ) {
        this(id, email, fullName, accountStatus, mustChangePassword, createdAt, null, "FULLY_AUTHENTICATED");
    }

    public static UserResponse fromEntity(AppUser user) {
        return fromEntity(user, "FULLY_AUTHENTICATED");
    }

    public static UserResponse fromEntity(AppUser user, String authStage) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAccountStatus(),
                user.isMustChangePassword(),
                user.getCreatedAt(),
                user.getPlatformRole(),
                authStage
        );
    }
}
