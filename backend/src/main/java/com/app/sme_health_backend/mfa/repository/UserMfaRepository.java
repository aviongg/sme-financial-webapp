package com.app.sme_health_backend.mfa.repository;

import com.app.sme_health_backend.mfa.entity.UserMfa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserMfaRepository extends JpaRepository<UserMfa, UUID> {

    Optional<UserMfa> findByUserId(UUID userId);

    @Modifying
    @Query("UPDATE UserMfa m SET m.lastUsedTimeStep = :candidate " +
           "WHERE m.userId = :userId AND m.status = 'ENABLED' " +
           "AND (m.lastUsedTimeStep IS NULL OR m.lastUsedTimeStep < :candidate)")
    int claimTimeStep(@Param("userId") UUID userId, @Param("candidate") long candidate);

    void deleteByUserId(UUID userId);
}
