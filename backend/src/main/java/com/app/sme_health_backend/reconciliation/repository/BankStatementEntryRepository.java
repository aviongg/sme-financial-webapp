package com.app.sme_health_backend.reconciliation.repository;

import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.reconciliation.entity.BankStatementEntry;
import com.app.sme_health_backend.reconciliation.model.StatementEntryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankStatementEntryRepository extends JpaRepository<BankStatementEntry, UUID>, JpaSpecificationExecutor<BankStatementEntry> {

    Optional<BankStatementEntry> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM BankStatementEntry e WHERE e.id = :id AND e.businessId = :businessId")
    Optional<BankStatementEntry> findForUpdateByIdAndBusinessId(@Param("id") UUID id, @Param("businessId") UUID businessId);

    List<BankStatementEntry> findByBusinessIdAndImportIdOrderBySourceRowNumberAsc(UUID businessId, UUID importId);

    Page<BankStatementEntry> findByBusinessIdAndAccountIdOrderByPostedDateDescSourceRowNumberAsc(
            UUID businessId,
            UUID accountId,
            Pageable pageable
    );

    List<BankStatementEntry> findByBusinessIdAndAccountIdAndSourceFingerprint(
            UUID businessId,
            UUID accountId,
            String sourceFingerprint
    );

    long countByBusinessIdAndAccountId(UUID businessId, UUID accountId);

    long countByBusinessIdAndAccountIdAndStatus(UUID businessId, UUID accountId, StatementEntryStatus status);

    @Query("SELECT e.direction AS direction, SUM(e.amount) AS totalAmount " +
           "FROM BankStatementEntry e " +
           "WHERE e.businessId = :businessId AND e.accountId = :accountId " +
           "GROUP BY e.direction")
    List<EntryDirectionSum> sumAmountsByDirection(
            @Param("businessId") UUID businessId,
            @Param("accountId") UUID accountId
    );

    @Query("SELECT e.status AS status, SUM(e.amount) AS totalAmount " +
           "FROM BankStatementEntry e " +
           "WHERE e.businessId = :businessId AND e.accountId = :accountId " +
           "GROUP BY e.status")
    List<EntryStatusSum> sumAmountsByStatus(
            @Param("businessId") UUID businessId,
            @Param("accountId") UUID accountId
    );

    interface EntryDirectionSum {
        MovementDirection getDirection();
        BigDecimal getTotalAmount();
    }

    interface EntryStatusSum {
        StatementEntryStatus getStatus();
        BigDecimal getTotalAmount();
    }
}
