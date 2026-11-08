package com.app.sme_health_backend.commission.repository;

import com.app.sme_health_backend.commission.entity.CommissionEarning;
import com.app.sme_health_backend.commission.model.CommissionEarningStatus;
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
public interface CommissionEarningRepository extends JpaRepository<CommissionEarning, UUID> {

    List<CommissionEarning> findByBusinessId(UUID businessId);

    List<CommissionEarning> findByBusinessIdAndSalespersonId(UUID businessId, UUID salespersonId);

    List<CommissionEarning> findByBusinessIdAndStatus(UUID businessId, CommissionEarningStatus status);

    List<CommissionEarning> findByBusinessIdAndSalespersonIdAndStatus(UUID businessId, UUID salespersonId, CommissionEarningStatus status);

    Optional<CommissionEarning> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM CommissionEarning e WHERE e.id = :id AND e.businessId = :businessId")
    Optional<CommissionEarning> findForUpdateByIdAndBusinessId(@Param("id") UUID id, @Param("businessId") UUID businessId);

    Optional<CommissionEarning> findByBusinessIdAndSourceFingerprint(UUID businessId, String sourceFingerprint);

    boolean existsByBusinessIdAndSourceFingerprint(UUID businessId, String sourceFingerprint);

    @Query("SELECT COALESCE(SUM(COALESCE(e.approvedAmount, e.commissionAmount)), 0) FROM CommissionEarning e WHERE e.businessId = :businessId AND e.status IN ('APPROVED', 'PARTIALLY_SETTLED', 'SETTLED', 'PAID')")
    BigDecimal sumApprovedAmounts(@Param("businessId") UUID businessId);

    @Query("SELECT COALESCE(SUM(COALESCE(e.approvedAmount, e.commissionAmount)), 0) FROM CommissionEarning e WHERE e.businessId = :businessId AND e.salespersonId = :salespersonId AND e.status IN ('APPROVED', 'PARTIALLY_SETTLED', 'SETTLED', 'PAID')")
    BigDecimal sumApprovedAmountsBySalesperson(@Param("businessId") UUID businessId, @Param("salespersonId") UUID salespersonId);
}
