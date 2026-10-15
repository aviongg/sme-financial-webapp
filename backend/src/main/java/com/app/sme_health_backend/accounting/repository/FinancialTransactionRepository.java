package com.app.sme_health_backend.accounting.repository;

import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID>, JpaSpecificationExecutor<FinancialTransaction> {

    Optional<FinancialTransaction> findByIdAndBusinessId(UUID id, UUID businessId);
}
