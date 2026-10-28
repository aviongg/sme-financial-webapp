package com.app.sme_health_backend.accounting.repository;

import com.app.sme_health_backend.accounting.entity.AccountMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AccountMovementRepository extends JpaRepository<AccountMovement, UUID> {

    List<AccountMovement> findByTransactionIdOrderByCreatedAtAscIdAsc(UUID transactionId);

    List<AccountMovement> findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(UUID accountId);

    @Query("SELECT m.accountId AS accountId, m.movementDirection AS movementDirection, SUM(m.amount) AS totalAmount " +
            "FROM AccountMovement m " +
            "WHERE m.businessId = :businessId AND m.effectiveDate <= :cutoffDate " +
            "GROUP BY m.accountId, m.movementDirection")
    List<AccountMovementSum> findMovementSumsByBusinessIdAndCutoffDate(
            @Param("businessId") UUID businessId,
            @Param("cutoffDate") LocalDate cutoffDate
    );

    @Query("SELECT m.accountId AS accountId, m.movementDirection AS movementDirection, SUM(m.amount) AS totalAmount " +
            "FROM AccountMovement m " +
            "WHERE m.businessId = :businessId AND m.effectiveDate >= :startDate AND m.effectiveDate <= :endDate " +
            "GROUP BY m.accountId, m.movementDirection")
    List<AccountMovementSum> findMovementSumsByBusinessIdAndDateRange(
            @Param("businessId") UUID businessId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT m.accountId AS accountId, m.movementDirection AS movementDirection, SUM(m.amount) AS totalAmount " +
            "FROM AccountMovement m " +
            "WHERE m.accountId = :accountId AND m.effectiveDate <= :cutoffDate " +
            "GROUP BY m.accountId, m.movementDirection")
    List<AccountMovementSum> findMovementSumsByAccountIdAndCutoffDate(
            @Param("accountId") UUID accountId,
            @Param("cutoffDate") LocalDate cutoffDate
    );
}
