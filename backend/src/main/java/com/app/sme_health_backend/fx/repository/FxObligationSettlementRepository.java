package com.app.sme_health_backend.fx.repository;

import com.app.sme_health_backend.fx.entity.FxObligationSettlement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FxObligationSettlementRepository extends JpaRepository<FxObligationSettlement, UUID> {

    Optional<FxObligationSettlement> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM FxObligationSettlement s WHERE s.id = :id AND s.businessId = :businessId")
    Optional<FxObligationSettlement> findForUpdateByIdAndBusinessId(@Param("id") UUID id, @Param("businessId") UUID businessId);

    Optional<FxObligationSettlement> findByBusinessIdAndIdempotencyKey(UUID businessId, String idempotencyKey);

    Optional<FxObligationSettlement> findByBusinessIdAndFinancialTransactionId(UUID businessId, UUID financialTransactionId);

    Optional<FxObligationSettlement> findByBusinessIdAndAllocationId(UUID businessId, UUID allocationId);

    List<FxObligationSettlement> findAllByBusinessIdAndObligationIdOrderBySettlementDateDescCreatedAtDesc(UUID businessId, UUID obligationId);

    List<FxObligationSettlement> findAllByBusinessIdOrderBySettlementDateDescCreatedAtDesc(UUID businessId);
}

