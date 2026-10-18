package com.app.sme_health_backend.accounting.draft.imports;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImportBatchRowRepository extends JpaRepository<ImportBatchRow, UUID> {

    Optional<ImportBatchRow> findByBusinessIdAndBatchIdAndSourceRowId(UUID businessId, UUID batchId, String sourceRowId);

    List<ImportBatchRow> findByBusinessIdAndBatchIdOrderBySourceRowNumberAsc(UUID businessId, UUID batchId);

    List<ImportBatchRow> findByBusinessIdAndBatchIdAndSourceRowIdIn(UUID businessId, UUID batchId, Collection<String> sourceRowIds);

    Optional<ImportBatchRow> findByBusinessIdAndDraftId(UUID businessId, UUID draftId);

    int countByBusinessIdAndBatchId(UUID businessId, UUID batchId);
}
