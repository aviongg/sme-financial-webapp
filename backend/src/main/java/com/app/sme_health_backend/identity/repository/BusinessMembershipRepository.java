package com.app.sme_health_backend.identity.repository;

import com.app.sme_health_backend.identity.entity.BusinessMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessMembershipRepository extends JpaRepository<BusinessMembership, UUID> {
    List<BusinessMembership> findByUserId(UUID userId);
    List<BusinessMembership> findByBusinessId(UUID businessId);
    Optional<BusinessMembership> findByUserIdAndBusinessId(UUID userId, UUID businessId);
    boolean existsByUserIdAndBusinessId(UUID userId, UUID businessId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from BusinessMembership m where m.id = :id and m.businessId = :businessId")
    Optional<BusinessMembership> findScopedForUpdate(@org.springframework.data.repository.query.Param("id") UUID id,
            @org.springframework.data.repository.query.Param("businessId") UUID businessId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from BusinessMembership m where m.id = :id and m.userId = :userId and m.status = com.app.sme_health_backend.identity.model.MembershipStatus.INVITED")
    Optional<BusinessMembership> findInvitationForUpdate(@org.springframework.data.repository.query.Param("id") UUID id,
            @org.springframework.data.repository.query.Param("userId") UUID userId);
}
