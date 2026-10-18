package com.app.sme_health_backend.accounting.draft.imports;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {

    Optional<ImportBatch> findByIdAndBusinessId(UUID id, UUID businessId);

    Page<ImportBatch> findByBusinessIdOrderByCreatedAtDesc(UUID businessId, Pageable pageable);
}
