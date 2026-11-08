package com.app.sme_health_backend.fx.repository;

import com.app.sme_health_backend.fx.entity.FxObligationRecognitionEvidence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FxObligationRecognitionEvidenceRepository extends JpaRepository<FxObligationRecognitionEvidence, UUID> {

    Optional<FxObligationRecognitionEvidence> findByBusinessIdAndObligationId(UUID businessId, UUID obligationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM FxObligationRecognitionEvidence r WHERE r.obligationId = :obligationId AND r.businessId = :businessId")
    Optional<FxObligationRecognitionEvidence> findForUpdateByObligationIdAndBusinessId(
            @Param("obligationId") UUID obligationId,
            @Param("businessId") UUID businessId
    );

    boolean existsByBusinessIdAndObligationId(UUID businessId, UUID obligationId);
}

