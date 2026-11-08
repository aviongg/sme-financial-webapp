package com.app.sme_health_backend.commission.repository;

import com.app.sme_health_backend.commission.entity.SalesAssignment;
import com.app.sme_health_backend.commission.model.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalesAssignmentRepository extends JpaRepository<SalesAssignment, UUID> {

    List<SalesAssignment> findByBusinessId(UUID businessId);

    List<SalesAssignment> findByBusinessIdAndSalespersonId(UUID businessId, UUID salespersonId);

    List<SalesAssignment> findByBusinessIdAndSalespersonIdAndStatus(UUID businessId, UUID salespersonId, AssignmentStatus status);

    List<SalesAssignment> findByBusinessIdAndCounterpartyId(UUID businessId, UUID counterpartyId);

    List<SalesAssignment> findByBusinessIdAndCounterpartyIdAndStatus(UUID businessId, UUID counterpartyId, AssignmentStatus status);

    Optional<SalesAssignment> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByBusinessIdAndCounterpartyIdAndStatus(UUID businessId, UUID counterpartyId, AssignmentStatus status);
}
