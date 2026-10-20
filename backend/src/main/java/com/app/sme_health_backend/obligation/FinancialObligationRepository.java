package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinancialObligationRepository extends JpaRepository<FinancialObligation, UUID>, JpaSpecificationExecutor<FinancialObligation> {

    Optional<FinancialObligation> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM FinancialObligation o WHERE o.id = :id AND o.businessId = :businessId")
    Optional<FinancialObligation> findByIdAndBusinessIdForUpdate(@Param("id") UUID id, @Param("businessId") UUID businessId);

    default Optional<FinancialObligation> findForUpdateByIdAndBusinessId(UUID id, UUID businessId) {
        return findByIdAndBusinessIdForUpdate(id, businessId);
    }

    boolean existsByBusinessIdAndSourceTypeAndSourceId(UUID businessId, ObligationSourceType sourceType, String sourceId);

    List<FinancialObligation> findByBusinessId(UUID businessId);

    List<FinancialObligation> findByBusinessIdAndDirection(UUID businessId, ObligationDirection direction);

    long countByBusinessIdAndCounterpartyId(UUID businessId, UUID counterpartyId);
}
