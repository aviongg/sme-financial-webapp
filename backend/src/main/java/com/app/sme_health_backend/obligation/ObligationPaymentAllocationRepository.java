package com.app.sme_health_backend.obligation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ObligationPaymentAllocationRepository extends JpaRepository<ObligationPaymentAllocation, UUID> {

    List<ObligationPaymentAllocation> findByBusinessIdAndObligationIdOrderByEffectiveDateAscCreatedAtAsc(UUID businessId, UUID obligationId);

    List<ObligationPaymentAllocation> findByBusinessIdAndObligationIdIn(UUID businessId, Collection<UUID> obligationIds);

    Optional<ObligationPaymentAllocation> findByBusinessIdAndFinancialTransactionId(UUID businessId, UUID financialTransactionId);

    List<ObligationPaymentAllocation> findAllByBusinessIdAndFinancialTransactionId(UUID businessId, UUID financialTransactionId);

    List<ObligationPaymentAllocation> findByBusinessIdAndAdvanceIdOrderByEffectiveDateAscCreatedAtAsc(UUID businessId, UUID advanceId);

    Optional<ObligationPaymentAllocation> findByIdAndBusinessId(UUID id, UUID businessId);

    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM ObligationPaymentAllocation a " +
            "WHERE a.businessId = :businessId AND a.obligationId = :obligationId AND a.status = 'EFFECTIVE'")
    BigDecimal sumEffectiveAllocations(@Param("businessId") UUID businessId, @Param("obligationId") UUID obligationId);

    @Query("SELECT a.obligationId, SUM(a.amount) FROM ObligationPaymentAllocation a " +
            "WHERE a.businessId = :businessId AND a.obligationId IN :obligationIds AND a.status = 'EFFECTIVE' " +
            "GROUP BY a.obligationId")
    List<Object[]> sumEffectiveAllocationsByObligationIds(@Param("businessId") UUID businessId, @Param("obligationIds") Collection<UUID> obligationIds);

    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM ObligationPaymentAllocation a " +
            "WHERE a.businessId = :businessId AND a.advanceId = :advanceId AND a.status = 'EFFECTIVE'")
    BigDecimal sumEffectiveAllocationsByAdvanceId(@Param("businessId") UUID businessId, @Param("advanceId") UUID advanceId);

    List<ObligationPaymentAllocation> findByBusinessId(UUID businessId);
}
