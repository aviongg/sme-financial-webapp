package com.app.sme_health_backend.records.service;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MonthlyRecordService {

    private final MonthlyRecordRepository monthlyRecordRepository;
    private final com.app.sme_health_backend.identity.repository.BusinessRepository businessRepository;

    public MonthlyRecordService(MonthlyRecordRepository monthlyRecordRepository,
            com.app.sme_health_backend.identity.repository.BusinessRepository businessRepository) {
        this.monthlyRecordRepository = java.util.Objects.requireNonNull(monthlyRecordRepository);
        this.businessRepository = java.util.Objects.requireNonNull(businessRepository);
    }

    /** Every monthly writer first holds the business parent lock, including absent-month creation. */
    @Transactional
    public void lockBusiness(UUID businessId) {
        if (businessId == null) throw new IllegalArgumentException("Business ID is required");
        businessRepository.findByIdForUpdate(businessId)
                .orElseThrow(() -> new com.app.sme_health_backend.shared.exception.ResourceNotFoundException("Business not found"));
    }

    /**
     * Manual monthly financial entry is retired (V52, monthly-projection-v1). Monthly figures are derived from
     * authoritative daily records by {@code MonthlyProjectionService}; this method only exists so legacy callers get a
     * typed, explicit refusal instead of silently creating a second source of truth.
     */
    public MonthlyRecord saveMonthlyRecord(MonthlyRecord record, UUID actorUserId, Long expectedVersion) {
        throw manualEntryRetired();
    }

    public static com.app.sme_health_backend.productsupport.DomainException manualEntryRetired() {
        return new com.app.sme_health_backend.productsupport.DomainException("monthly_figures_derived", 409,
                "Monthly financial figures are derived automatically from transactions, invoices, bills, payments and "
                        + "inventory. Record the underlying activity instead; only supplemental financing inputs are manual.");
    }

    @Transactional(readOnly = true)
    public List<MonthlyRecord> getUserRecords(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        return monthlyRecordRepository.findByUserIdOrderByMonthDesc(userId);
    }

    @Transactional(readOnly = true)
    public Optional<MonthlyRecord> getMonthlyRecord(
            UUID userId,
            String month
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        if (month == null || month.isBlank()) {
            throw new IllegalArgumentException("Month is required");
        }

        try {
            YearMonth.parse(month);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Month must be in YYYY-MM format"
            );
        }

        return monthlyRecordRepository.findByUserIdAndMonth(
                userId,
                month
        );
    }

    @Transactional(readOnly = true)
    public Optional<MonthlyRecord> getRecordById(UUID id, UUID businessId) {
        if (id == null) {
            throw new IllegalArgumentException("Record ID is required");
        }
        if (businessId == null) {
            throw new IllegalArgumentException("Business ID is required");
        }

        return monthlyRecordRepository.findByIdAndUserId(id, businessId);
    }
}
