package com.app.sme_health_backend.commission.repository;

import com.app.sme_health_backend.commission.entity.CommissionAdvance;
import com.app.sme_health_backend.commission.model.CommissionAdvanceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommissionAdvanceRepository extends JpaRepository<CommissionAdvance, UUID> {

    Optional<CommissionAdvance> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM CommissionAdvance a WHERE a.id = :id AND a.businessId = :businessId")
    Optional<CommissionAdvance> findForUpdateByIdAndBusinessId(@Param("id") UUID id, @Param("businessId") UUID businessId);

    List<CommissionAdvance> findByBusinessId(UUID businessId);

    List<CommissionAdvance> findByBusinessIdAndSalespersonId(UUID businessId, UUID salespersonId);

    List<CommissionAdvance> findByBusinessIdAndSalespersonIdAndStatus(UUID businessId, UUID salespersonId, CommissionAdvanceStatus status);

    Optional<CommissionAdvance> findByBusinessIdAndIdempotencyKey(UUID businessId, String idempotencyKey);

    @Query("SELECT COALESCE(SUM(a.amount - a.appliedAmount), 0) FROM CommissionAdvance a WHERE a.businessId = :businessId AND a.salespersonId = :salespersonId AND a.status = 'ACTIVE'")
    BigDecimal sumUnappliedAdvancesBySalesperson(@Param("businessId") UUID businessId, @Param("salespersonId") UUID salespersonId);

    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM CommissionAdvance a WHERE a.businessId = :businessId AND a.status != 'VOID'")
    BigDecimal sumTotalAdvances(@Param("businessId") UUID businessId);

    @Query("SELECT COALESCE(SUM(a.appliedAmount), 0) FROM CommissionAdvance a WHERE a.businessId = :businessId AND a.status != 'VOID'")
    BigDecimal sumTotalAdvancesApplied(@Param("businessId") UUID businessId);

    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM CommissionAdvance a WHERE a.businessId = :businessId AND a.salespersonId = :salespersonId AND a.status != 'VOID'")
    BigDecimal sumTotalAdvancesBySalesperson(@Param("businessId") UUID businessId, @Param("salespersonId") UUID salespersonId);

    @Query("SELECT COALESCE(SUM(a.appliedAmount), 0) FROM CommissionAdvance a WHERE a.businessId = :businessId AND a.salespersonId = :salespersonId AND a.status != 'VOID'")
    BigDecimal sumTotalAdvancesAppliedBySalesperson(@Param("businessId") UUID businessId, @Param("salespersonId") UUID salespersonId);
}
