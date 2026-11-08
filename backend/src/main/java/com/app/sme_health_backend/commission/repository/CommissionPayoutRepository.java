package com.app.sme_health_backend.commission.repository;

import com.app.sme_health_backend.commission.entity.CommissionPayout;
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
public interface CommissionPayoutRepository extends JpaRepository<CommissionPayout, UUID> {

    Optional<CommissionPayout> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CommissionPayout p WHERE p.id = :id AND p.businessId = :businessId")
    Optional<CommissionPayout> findForUpdateByIdAndBusinessId(@Param("id") UUID id, @Param("businessId") UUID businessId);

    List<CommissionPayout> findByBusinessId(UUID businessId);

    List<CommissionPayout> findByBusinessIdAndEarningId(UUID businessId, UUID earningId);

    List<CommissionPayout> findByBusinessIdAndSalespersonId(UUID businessId, UUID salespersonId);

    Optional<CommissionPayout> findByBusinessIdAndIdempotencyKey(UUID businessId, String idempotencyKey);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM CommissionPayout p WHERE p.businessId = :businessId AND p.earningId = :earningId AND p.status = 'SETTLED'")
    BigDecimal sumEffectiveSettlementsByEarning(@Param("businessId") UUID businessId, @Param("earningId") UUID earningId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM CommissionPayout p WHERE p.businessId = :businessId AND p.payoutType = 'PAYOUT' AND p.status = 'SETTLED'")
    BigDecimal sumTotalCashPayouts(@Param("businessId") UUID businessId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM CommissionPayout p WHERE p.businessId = :businessId AND p.salespersonId = :salespersonId AND p.payoutType = 'PAYOUT' AND p.status = 'SETTLED'")
    BigDecimal sumTotalCashPayoutsBySalesperson(@Param("businessId") UUID businessId, @Param("salespersonId") UUID salespersonId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM CommissionPayout p WHERE p.businessId = :businessId AND p.salespersonId = :salespersonId AND p.status = 'SETTLED'")
    BigDecimal sumEffectiveSettlementsBySalesperson(@Param("businessId") UUID businessId, @Param("salespersonId") UUID salespersonId);
}
