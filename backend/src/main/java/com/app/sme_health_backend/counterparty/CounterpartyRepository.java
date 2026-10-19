package com.app.sme_health_backend.counterparty;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CounterpartyRepository extends JpaRepository<Counterparty, UUID>, JpaSpecificationExecutor<Counterparty> {

    Optional<Counterparty> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Counterparty c WHERE c.id = :id AND c.businessId = :businessId")
    Optional<Counterparty> findForUpdateByIdAndBusinessId(@Param("id") UUID id, @Param("businessId") UUID businessId);

    boolean existsByBusinessIdAndDisplayNameIgnoreCase(UUID businessId, String displayName);

    List<Counterparty> findAllByBusinessIdAndStatusOrderByDisplayNameAsc(UUID businessId, CounterpartyStatus status);
}
