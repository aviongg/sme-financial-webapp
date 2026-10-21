package com.app.sme_health_backend.recurring.repository;

import com.app.sme_health_backend.recurring.entity.RecurringRuleOccurrence;
import com.app.sme_health_backend.recurring.model.RecurringOccurrenceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecurringRuleOccurrenceRepository extends JpaRepository<RecurringRuleOccurrence, UUID> {

    Optional<RecurringRuleOccurrence> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM RecurringRuleOccurrence o WHERE o.id = :id AND o.businessId = :businessId")
    Optional<RecurringRuleOccurrence> findForUpdateByIdAndBusinessId(
            @Param("id") UUID id,
            @Param("businessId") UUID businessId
    );

    Optional<RecurringRuleOccurrence> findByBusinessIdAndRecurringRuleIdAndScheduledDate(
            UUID businessId,
            UUID recurringRuleId,
            LocalDate scheduledDate
    );

    Optional<RecurringRuleOccurrence> findByBusinessIdAndDraftId(UUID businessId, UUID draftId);

    Optional<RecurringRuleOccurrence> findByBusinessIdAndPostedTransactionId(UUID businessId, UUID postedTransactionId);

    Page<RecurringRuleOccurrence> findByBusinessId(UUID businessId, Pageable pageable);

    Page<RecurringRuleOccurrence> findByBusinessIdAndStatus(
            UUID businessId,
            RecurringOccurrenceStatus status,
            Pageable pageable
    );

    Page<RecurringRuleOccurrence> findByBusinessIdAndRecurringRuleId(
            UUID businessId,
            UUID recurringRuleId,
            Pageable pageable
    );

    long countByBusinessIdAndStatus(UUID businessId, RecurringOccurrenceStatus status);
}
