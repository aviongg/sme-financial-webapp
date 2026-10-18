package com.app.sme_health_backend.accounting.repository;

import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID>, JpaSpecificationExecutor<FinancialTransaction> {

    Optional<FinancialTransaction> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM FinancialTransaction t WHERE t.id = :id AND t.businessId = :businessId")
    Optional<FinancialTransaction> findForUpdateByIdAndBusinessId(@Param("id") UUID id, @Param("businessId") UUID businessId);

    Optional<FinancialTransaction> findByReversedTransactionIdAndBusinessId(UUID reversedTransactionId, UUID businessId);

    List<FinancialTransaction> findByBusinessIdAndReversedTransactionIdIn(UUID businessId, Collection<UUID> reversedTransactionIds);
}
