package com.app.sme_health_backend.commission.repository;

import com.app.sme_health_backend.commission.entity.SalespersonProfile;
import com.app.sme_health_backend.commission.model.SalespersonStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalespersonProfileRepository extends JpaRepository<SalespersonProfile, UUID> {

    List<SalespersonProfile> findByBusinessId(UUID businessId);

    List<SalespersonProfile> findByBusinessIdAndStatus(UUID businessId, SalespersonStatus status);

    Optional<SalespersonProfile> findByIdAndBusinessId(UUID id, UUID businessId);

    Optional<SalespersonProfile> findByBusinessIdAndUserId(UUID businessId, UUID userId);

    Optional<SalespersonProfile> findByBusinessIdAndEmail(UUID businessId, String email);

    boolean existsByBusinessIdAndEmail(UUID businessId, String email);

    boolean existsByBusinessIdAndUserId(UUID businessId, UUID userId);
}
