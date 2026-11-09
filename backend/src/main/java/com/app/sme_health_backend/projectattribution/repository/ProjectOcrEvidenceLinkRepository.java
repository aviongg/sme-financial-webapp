package com.app.sme_health_backend.projectattribution.repository;

import com.app.sme_health_backend.projectattribution.entity.ProjectOcrEvidenceLink;
import com.app.sme_health_backend.projectattribution.model.AttributionSourceType;
import com.app.sme_health_backend.projectattribution.model.ProjectOcrEvidenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectOcrEvidenceLinkRepository extends JpaRepository<ProjectOcrEvidenceLink, UUID> {

    List<ProjectOcrEvidenceLink> findByBusinessIdAndProjectIdOrderByCreatedAtDesc(UUID businessId, UUID projectId);

    Optional<ProjectOcrEvidenceLink> findByBusinessIdAndId(UUID businessId, UUID id);

    Optional<ProjectOcrEvidenceLink> findByBusinessIdAndProjectIdAndId(UUID businessId, UUID projectId, UUID id);

    List<ProjectOcrEvidenceLink> findByBusinessIdAndDocumentId(UUID businessId, UUID documentId);

    List<ProjectOcrEvidenceLink> findByBusinessIdAndProjectIdAndDocumentId(UUID businessId, UUID projectId, UUID documentId);

    List<ProjectOcrEvidenceLink> findByBusinessIdAndFinancialSourceTypeAndFinancialSourceId(
            UUID businessId, AttributionSourceType sourceType, UUID financialSourceId);

    List<ProjectOcrEvidenceLink> findByBusinessIdAndAttributionId(UUID businessId, UUID attributionId);

    long countByBusinessIdAndDocumentIdAndEvidenceStatusNot(
            UUID businessId, UUID documentId, ProjectOcrEvidenceStatus excludedStatus);
}
