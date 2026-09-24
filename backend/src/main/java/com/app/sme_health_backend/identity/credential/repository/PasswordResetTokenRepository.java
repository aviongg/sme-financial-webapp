package com.app.sme_health_backend.identity.credential.repository;

import com.app.sme_health_backend.identity.credential.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now " +
           "WHERE t.tokenHash = :tokenHash AND t.usedAt IS NULL AND t.revokedAt IS NULL AND t.expiresAt > :now")
    int claimToken(@Param("tokenHash") String tokenHash, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.revokedAt = :now " +
           "WHERE t.userId = :userId AND t.usedAt IS NULL AND t.revokedAt IS NULL")
    int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}
