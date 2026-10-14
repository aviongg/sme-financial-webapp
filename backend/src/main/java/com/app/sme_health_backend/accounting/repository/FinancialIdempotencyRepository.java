package com.app.sme_health_backend.accounting.repository;

import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinancialIdempotencyRepository extends JpaRepository<FinancialIdempotencyRecord, UUID> {

    Optional<FinancialIdempotencyRecord> findByBusinessIdAndOperationAndIdempotencyKey(
            UUID businessId,
            String operation,
            String idempotencyKey
    );
}
