package com.app.sme_health_backend.obligation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CounterpartyAdvanceRepository extends JpaRepository<CounterpartyAdvance, UUID>, JpaSpecificationExecutor<CounterpartyAdvance> {

    Optional<CounterpartyAdvance> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM CounterpartyAdvance a WHERE a.id = :id AND a.businessId = :businessId")
    Optional<CounterpartyAdvance> findForUpdateByIdAndBusinessId(@Param("id") UUID id, @Param("businessId") UUID businessId);

    Optional<CounterpartyAdvance> findByBusinessIdAndFinancialTransactionId(UUID businessId, UUID financialTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM CounterpartyAdvance a WHERE a.businessId = :businessId AND a.financialTransactionId = :financialTransactionId")
    Optional<CounterpartyAdvance> findForUpdateByBusinessIdAndFinancialTransactionId(@Param("businessId") UUID businessId, @Param("financialTransactionId") UUID financialTransactionId);

    List<CounterpartyAdvance> findByBusinessId(UUID businessId);

    List<CounterpartyAdvance> findByBusinessIdAndCounterpartyId(UUID businessId, UUID counterpartyId);

    Page<CounterpartyAdvance> findAll(Specification<CounterpartyAdvance> spec, Pageable pageable);
}
