package com.app.sme_health_backend.projectattribution.repository;

import com.app.sme_health_backend.projectattribution.entity.ProjectCommercialDocumentLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectCommercialDocumentLinkRepository extends JpaRepository<ProjectCommercialDocumentLink, UUID> {

    List<ProjectCommercialDocumentLink> findByBusinessIdAndProjectIdAndLinkStatus(
            UUID businessId, UUID projectId, String linkStatus
    );

    List<ProjectCommercialDocumentLink> findByBusinessIdAndProjectIdOrderByCreatedAtDesc(
            UUID businessId, UUID projectId
    );

    Optional<ProjectCommercialDocumentLink> findByBusinessIdAndProjectIdAndDocumentIdAndLinkStatus(
            UUID businessId, UUID projectId, UUID documentId, String linkStatus
    );

    Optional<ProjectCommercialDocumentLink> findByIdAndBusinessId(UUID id, UUID businessId);

    @Query("SELECT COALESCE(SUM(l.allocatedAmount), 0) FROM ProjectCommercialDocumentLink l " +
           "WHERE l.businessId = :businessId AND l.documentId = :documentId AND l.linkStatus = :status")
    BigDecimal sumAllocatedAmountByDocument(
            @Param("businessId") UUID businessId,
            @Param("documentId") UUID documentId,
            @Param("status") String status
    );
}
