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
}
