package com.app.sme_health_backend.reconciliation.repository;

import com.app.sme_health_backend.reconciliation.entity.BankReconciliationMatch;
import com.app.sme_health_backend.reconciliation.model.ReconciliationMatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface BankReconciliationMatchRepository extends JpaRepository<BankReconciliationMatch, UUID> {

    Optional<BankReconciliationMatch> findByIdAndBusinessId(UUID id, UUID businessId);

    Optional<BankReconciliationMatch> findByBusinessIdAndStatementEntryIdAndStatus(
            UUID businessId,
            UUID statementEntryId,
            ReconciliationMatchStatus status
    );

    Optional<BankReconciliationMatch> findByBusinessIdAndAccountMovementIdAndStatus(
            UUID businessId,
            UUID accountMovementId,
            ReconciliationMatchStatus status
    );

    List<BankReconciliationMatch> findByBusinessIdAndAccountIdAndStatus(
            UUID businessId,
            UUID accountId,
            ReconciliationMatchStatus status
    );

    boolean existsByBusinessIdAndAccountMovementIdAndStatus(
            UUID businessId,
            UUID accountMovementId,
            ReconciliationMatchStatus status
    );

    boolean existsByBusinessIdAndStatementEntryIdAndStatus(
            UUID businessId,
            UUID statementEntryId,
            ReconciliationMatchStatus status
    );

    @Query("SELECT m.accountMovementId FROM BankReconciliationMatch m " +
           "WHERE m.businessId = :businessId AND m.accountId = :accountId AND m.status = 'ACTIVE'")
    Set<UUID> findActiveMatchedMovementIds(
            @Param("businessId") UUID businessId,
            @Param("accountId") UUID accountId
    );
}
