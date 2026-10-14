package com.app.sme_health_backend.accounting.repository;

import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, UUID>, JpaSpecificationExecutor<FinancialAccount> {

    Optional<FinancialAccount> findByIdAndBusinessId(UUID id, UUID businessId);

    @Query("SELECT a FROM FinancialAccount a WHERE a.businessId = :businessId " +
            "AND (:status IS NULL OR a.status = :status) " +
            "AND (:accountType IS NULL OR a.accountType = :accountType) " +
            "AND (:currency IS NULL OR a.currency = :currency) " +
            "ORDER BY a.createdAt ASC, a.id ASC")
    Page<FinancialAccount> findFilteredAccounts(
            @Param("businessId") UUID businessId,
            @Param("status") AccountStatus status,
            @Param("accountType") AccountType accountType,
            @Param("currency") String currency,
            Pageable pageable
    );
}
