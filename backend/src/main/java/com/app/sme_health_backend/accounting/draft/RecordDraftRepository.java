package com.app.sme_health_backend.accounting.draft;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
public interface RecordDraftRepository extends JpaRepository<RecordDraft, UUID>, JpaSpecificationExecutor<RecordDraft> {

    Optional<RecordDraft> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM RecordDraft d WHERE d.id = :id AND d.businessId = :businessId")
    Optional<RecordDraft> findForUpdateByIdAndBusinessId(@Param("id") UUID id, @Param("businessId") UUID businessId);

    Optional<RecordDraft> findByBusinessIdAndRecordedTransactionId(UUID businessId, UUID recordedTransactionId);

    List<RecordDraft> findByBusinessIdAndStatus(UUID businessId, DraftStatus status);

    long countByBusinessIdAndStatus(UUID businessId, DraftStatus status);
}
