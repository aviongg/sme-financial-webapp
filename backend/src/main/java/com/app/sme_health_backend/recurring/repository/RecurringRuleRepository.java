package com.app.sme_health_backend.recurring.repository;

import com.app.sme_health_backend.recurring.entity.RecurringRule;
import com.app.sme_health_backend.recurring.model.RecurringRuleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecurringRuleRepository extends JpaRepository<RecurringRule, UUID> {

    Optional<RecurringRule> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RecurringRule r WHERE r.id = :id AND r.businessId = :businessId")
    Optional<RecurringRule> findForUpdateByIdAndBusinessId(
            @Param("id") UUID id,
            @Param("businessId") UUID businessId
    );

    Page<RecurringRule> findByBusinessId(UUID businessId, Pageable pageable);

    Page<RecurringRule> findByBusinessIdAndStatus(UUID businessId, RecurringRuleStatus status, Pageable pageable);

    List<RecurringRule> findByBusinessIdAndStatusAndNextDueDateLessThanEqual(
            UUID businessId,
            RecurringRuleStatus status,
            LocalDate maxDate
    );

    List<RecurringRule> findByStatusAndNextDueDateLessThanEqual(
            RecurringRuleStatus status,
            LocalDate maxDate
    );

    long countByBusinessIdAndStatus(UUID businessId, RecurringRuleStatus status);
}
