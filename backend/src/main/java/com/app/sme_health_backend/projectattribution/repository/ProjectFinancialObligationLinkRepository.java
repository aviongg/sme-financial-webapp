package com.app.sme_health_backend.projectattribution.repository;

import com.app.sme_health_backend.projectattribution.entity.ProjectFinancialObligationLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectFinancialObligationLinkRepository extends JpaRepository<ProjectFinancialObligationLink, UUID> {

    List<ProjectFinancialObligationLink> findByBusinessIdAndProjectIdAndLinkStatus(
            UUID businessId, UUID projectId, String linkStatus
    );

    List<ProjectFinancialObligationLink> findByBusinessIdAndProjectIdOrderByCreatedAtDesc(
            UUID businessId, UUID projectId
    );

    Optional<ProjectFinancialObligationLink> findByBusinessIdAndProjectIdAndObligationIdAndLinkStatus(
            UUID businessId, UUID projectId, UUID obligationId, String linkStatus
    );

    Optional<ProjectFinancialObligationLink> findByIdAndBusinessId(UUID id, UUID businessId);

    @Query("SELECT COALESCE(SUM(l.allocatedAmount), 0) FROM ProjectFinancialObligationLink l " +
           "WHERE l.businessId = :businessId AND l.obligationId = :obligationId AND l.linkStatus = :status")
    BigDecimal sumAllocatedAmountByObligation(
            @Param("businessId") UUID businessId,
            @Param("obligationId") UUID obligationId,
            @Param("status") String status
    );
}
