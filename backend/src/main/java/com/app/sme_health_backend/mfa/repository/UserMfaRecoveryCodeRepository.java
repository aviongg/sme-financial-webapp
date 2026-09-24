package com.app.sme_health_backend.mfa.repository;

import com.app.sme_health_backend.mfa.entity.UserMfaRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserMfaRecoveryCodeRepository extends JpaRepository<UserMfaRecoveryCode, UUID> {

    List<UserMfaRecoveryCode> findByUserId(UUID userId);

    List<UserMfaRecoveryCode> findByUserIdAndUsedAtIsNull(UUID userId);

    @Modifying
    @Query("UPDATE UserMfaRecoveryCode r SET r.usedAt = :now WHERE r.id = :id AND r.usedAt IS NULL")
    int claimCode(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    void deleteByUserId(UUID userId);
}
