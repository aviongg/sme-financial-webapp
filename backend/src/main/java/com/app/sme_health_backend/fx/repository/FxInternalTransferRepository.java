package com.app.sme_health_backend.fx.repository;

import com.app.sme_health_backend.fx.entity.FxInternalTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FxInternalTransferRepository extends JpaRepository<FxInternalTransfer, UUID> {

    List<FxInternalTransfer> findAllByBusinessIdOrderByTransferDateDescCreatedAtDesc(UUID businessId);

    Optional<FxInternalTransfer> findByIdAndBusinessId(UUID id, UUID businessId);

    Optional<FxInternalTransfer> findByBusinessIdAndIdempotencyKey(UUID businessId, String idempotencyKey);

    Optional<FxInternalTransfer> findByFinancialTransactionId(UUID transactionId);
}
