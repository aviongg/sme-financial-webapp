package com.app.sme_health_backend.projectattribution.repository;

import com.app.sme_health_backend.projectattribution.entity.ProjectFinancialAttribution;
import com.app.sme_health_backend.projectattribution.model.AttributionSourceType;
import com.app.sme_health_backend.projectattribution.model.AttributionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectFinancialAttributionRepository extends JpaRepository<ProjectFinancialAttribution, UUID> {

    List<ProjectFinancialAttribution> findByBusinessIdAndProjectIdOrderByAttributionDateDescCreatedAtDesc(
            UUID businessId, UUID projectId
    );

    List<ProjectFinancialAttribution> findByBusinessIdAndProjectIdAndStatusOrderByAttributionDateDescCreatedAtDesc(
            UUID businessId, UUID projectId, AttributionStatus status
    );

    Optional<ProjectFinancialAttribution> findByBusinessIdAndProjectIdAndId(
            UUID businessId, UUID projectId, UUID id
    );

    Optional<ProjectFinancialAttribution> findByBusinessIdAndId(
            UUID businessId, UUID id
    );

    Optional<ProjectFinancialAttribution> findByBusinessIdAndIdempotencyKey(
            UUID businessId, String idempotencyKey
    );

    List<ProjectFinancialAttribution> findByBusinessIdAndSourceTypeAndSourceId(
            UUID businessId, AttributionSourceType sourceType, UUID sourceId
    );

    List<ProjectFinancialAttribution> findByBusinessIdAndSourceTypeAndSourceIdAndStatus(
            UUID businessId, AttributionSourceType sourceType, UUID sourceId, AttributionStatus status
    );

    @Query("SELECT COALESCE(SUM(a.attributedAmount), 0) FROM ProjectFinancialAttribution a " +
            "WHERE a.businessId = :businessId AND a.sourceType = :sourceType AND a.sourceId = :sourceId AND a.status = :status")
    BigDecimal sumAttributedAmountBySource(
            @Param("businessId") UUID businessId,
            @Param("sourceType") AttributionSourceType sourceType,
            @Param("sourceId") UUID sourceId,
            @Param("status") AttributionStatus status
    );
}
