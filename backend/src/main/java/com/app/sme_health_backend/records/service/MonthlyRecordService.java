package com.app.sme_health_backend.records.service;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.scoring.service.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class MonthlyRecordService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyRecordService.class);

    private static final Set<String> VALID_FINANCING_TYPES =
            Set.of("none", "conventional", "islamic");

    private final MonthlyRecordRepository monthlyRecordRepository;
    private final ScoringService scoringService;
    private final com.app.sme_health_backend.audit.service.SecurityAuditService auditService;

    private final com.app.sme_health_backend.identity.repository.BusinessRepository businessRepository;
    private final com.app.sme_health_backend.records.repository.DocumentFinancialContributionRepository contributions;

    public MonthlyRecordService(MonthlyRecordRepository monthlyRecordRepository, ScoringService scoringService,
            com.app.sme_health_backend.audit.service.SecurityAuditService auditService,
            com.app.sme_health_backend.identity.repository.BusinessRepository businessRepository,
            com.app.sme_health_backend.records.repository.DocumentFinancialContributionRepository contributions) {
        this.monthlyRecordRepository = java.util.Objects.requireNonNull(monthlyRecordRepository);
        this.scoringService = java.util.Objects.requireNonNull(scoringService);
        this.auditService = java.util.Objects.requireNonNull(auditService);
        this.businessRepository = java.util.Objects.requireNonNull(businessRepository);
        this.contributions = java.util.Objects.requireNonNull(contributions);
    }

    /** Every monthly writer first holds the business parent lock, including absent-month creation. */
    @Transactional
    public void lockBusiness(UUID businessId) {
        if (businessId == null) throw new IllegalArgumentException("Business ID is required");
        businessRepository.findByIdForUpdate(businessId)
                .orElseThrow(() -> new com.app.sme_health_backend.shared.exception.ResourceNotFoundException("Business not found"));
    }

    @Transactional
    public MonthlyRecord saveMonthlyRecord(MonthlyRecord record, UUID actorUserId, Long expectedVersion) {
        validateMonthlyRecord(record);
        java.util.Objects.requireNonNull(actorUserId, "Authenticated actor is required");
        lockBusiness(record.getUserId());
        MonthlyRecord existing = monthlyRecordRepository.findByUserIdAndMonthForUpdate(record.getUserId(), record.getMonth()).orElse(null);
        if ((existing == null && expectedVersion != null)
                || (existing != null && (expectedVersion == null || !expectedVersion.equals(existing.getVersion()))))
            throw new FinancialConflictException("stale_monthly_record", "This month changed. Reload its current values before saving.");
        if (existing != null) enforceSourceFloors(record);
        record.setUpdatedAt(LocalDateTime.now());
        MonthlyRecord toSave = record;
        if (existing != null) { updateExistingRecord(existing, record); toSave = existing; }
        // Flush before returning so the response contains the version actually committed by JPA.
        MonthlyRecord saved = monthlyRecordRepository.saveAndFlush(toSave);
        recalculateAffectedScores(saved.getUserId(), saved.getMonth());
        auditService.logSuccess(existing == null
                        ? com.app.sme_health_backend.audit.model.AuditEventType.MONTHLY_RECORD_CREATED
                        : com.app.sme_health_backend.audit.model.AuditEventType.MONTHLY_RECORD_UPDATED,
                actorUserId, null, saved.getUserId(), "monthly_record", saved.getId().toString(),
                java.util.Map.of("month", saved.getMonth()));
        return saved;
    }

    private void enforceSourceFloors(MonthlyRecord incoming) {
        var floors = contributions.floors(incoming.getUserId(), incoming.getMonth());
        java.util.Map<String, String> errors = new java.util.LinkedHashMap<>();
        checkFloor(errors, "revenue", incoming.getRevenue(), floors.get("revenue"));
        checkFloor(errors, "operatingExpenses", incoming.getOperatingExpenses(), floors.get("operatingExpenses"));
        checkFloor(errors, "cogs", incoming.getCogs(), floors.get("cogs"));
        checkFloor(errors, "cashInflow", incoming.getCashInflow(), floors.get("cashInflow"));
        checkFloor(errors, "cashOutflow", incoming.getCashOutflow(), floors.get("cashOutflow"));
        if (!errors.isEmpty()) throw new FinancialConflictException("confirmed_source_conflict",
                "Aggregate totals cannot be below confirmed document contributions.", errors);
    }
    private static void checkFloor(java.util.Map<String, String> errors, String field, BigDecimal value, BigDecimal floor) {
        if (floor.signum() > 0 && (value == null || value.compareTo(floor) < 0)) errors.put(field, "confirmed_source_floor");
    }

    @Transactional
    public MonthlyRecord applyDocumentContribution(UUID businessId, UUID actorUserId, UUID documentId,
            com.app.sme_health_backend.documents.dto.DocumentConfirmationRequest request) {
        java.util.Objects.requireNonNull(actorUserId, "Authenticated actor is required");
        java.util.Objects.requireNonNull(documentId, "Document ID is required");
        java.util.Objects.requireNonNull(request, "Confirmation request is required");
        String targetMonth = request.targetMonth();
        if (targetMonth == null || !targetMonth.matches("[0-9]{4}-(0[1-9]|1[0-2])"))
            throw new IllegalArgumentException("Target month must be in YYYY-MM format");
        BigDecimal amount = request.confirmedAmount();
        validateNonNegative("Confirmed amount", amount);
        if (amount.signum() <= 0) throw new IllegalArgumentException("Confirmed amount must be greater than zero");
        validateOptionalNonNegative("Initial cash balance", request.initialCashBalanceEom());
        String classification = request.targetClassification() == null ? "none" : request.targetClassification().strip().toLowerCase(java.util.Locale.ROOT);
        String flow = request.cashFlowImpact() == null ? "none" : request.cashFlowImpact().strip().toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("revenue", "operating_expenses", "cogs", "none").contains(classification)
                || !Set.of("cash_inflow", "cash_outflow", "none").contains(flow))
            throw new IllegalArgumentException("Invalid contribution classification or cash flow impact");
        lockBusiness(businessId);
        MonthlyRecord record = monthlyRecordRepository.findByUserIdAndMonthForUpdate(businessId, targetMonth).orElse(null);
        String fingerprint = DocumentSourceFingerprint.of(businessId, request, classification, flow);
        boolean duplicate = contributions.hasFingerprint(businessId, fingerprint);
        if (duplicate && !request.allowPotentialDuplicate())
            throw new FinancialConflictException("potential_duplicate_source",
                    "A similar source is already confirmed for this month. Review it and explicitly confirm anyway only for a separate source.");
        boolean newMonth = record == null;
        if (newMonth) {
            if (request.initialCashBalanceEom() == null)
                throw new IllegalArgumentException("No monthly record exists for " + targetMonth + ". An initial cash balance (initialCashBalanceEom) is required.");
            record = new MonthlyRecord(); record.setUserId(businessId); record.setMonth(targetMonth);
            record.setCashBalanceEom(request.initialCashBalanceEom());
        }
        if ("revenue".equals(classification)) record.setRevenue(record.getRevenue().add(amount));
        else if ("operating_expenses".equals(classification)) record.setOperatingExpenses(record.getOperatingExpenses().add(amount));
        else if ("cogs".equals(classification)) record.setCogs((record.getCogs() == null ? BigDecimal.ZERO : record.getCogs()).add(amount));
        if ("cash_inflow".equals(flow)) record.setCashInflow(record.getCashInflow().add(amount));
        else if ("cash_outflow".equals(flow)) record.setCashOutflow(record.getCashOutflow().add(amount));
        record.setUpdatedAt(LocalDateTime.now());
        // Validate resulting period flows without reinterpreting untouched legacy point-in-time balances.
        // New months retain the existing full validation and unknown-versus-zero defaults.
        if (newMonth) validateMonthlyRecord(record);
        else {
            validateDatabaseAmount("Revenue", record.getRevenue());
            validateDatabaseAmount("Operating expenses", record.getOperatingExpenses());
            validateDatabaseAmount("COGS", record.getCogs());
            validateDatabaseAmount("Cash inflow", record.getCashInflow());
            validateDatabaseAmount("Cash outflow", record.getCashOutflow());
        }
        MonthlyRecord saved = monthlyRecordRepository.saveAndFlush(record);
        contributions.insert(new com.app.sme_health_backend.records.entity.DocumentFinancialContribution(UUID.randomUUID(),
                businessId, documentId, saved.getId(), targetMonth, classification, flow, amount, actorUserId, fingerprint,
                request.allowPotentialDuplicate()));
        recalculateAffectedScores(businessId, targetMonth);
        return saved;
    }

    private void recalculateAffectedScores(UUID userId, String targetMonth) {
        try {
            List<MonthlyRecord> records = monthlyRecordRepository.findByUserIdOrderByMonthAsc(userId);
            if (records == null || records.isEmpty()) {
                return;
            }

            int targetIndex = -1;
            for (int i = 0; i < records.size(); i++) {
                if (records.get(i).getMonth().equals(targetMonth)) {
                    targetIndex = i;
                    break;
                }
            }

            if (targetIndex == -1) {
                log.info("Recalculating score for user {} month {} (not found in ascending sequence)", userId, targetMonth);
                scoringService.calculateAndSaveScore(userId, targetMonth);
                return;
            }

            // Feature 2 calculators (CashFlowStability and Trend) use a rolling window of up to 6 records
            // ending at each evaluated month. Therefore, editing record at targetIndex affects scores for
            // targetIndex plus up to the next 5 subsequent chronological records.
            int endIndex = Math.min(records.size(), targetIndex + 6);
            for (int i = targetIndex; i < endIndex; i++) {
                String monthToScore = records.get(i).getMonth();
                log.debug("Recalculating affected score for user {} month {}", userId, monthToScore);
                scoringService.calculateAndSaveScore(userId, monthToScore);
            }
        } catch (Exception e) {
            log.error("Scoring failed for user {} month {}: {}", userId, targetMonth, e.getMessage(), e);
            throw e;
        }
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

    private void validateMonthlyRecord(MonthlyRecord record) {

        if (record == null) {
            throw new IllegalArgumentException("Monthly record is required");
        }

        if (record.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        if (record.getMonth() == null || record.getMonth().isBlank()) {
            throw new IllegalArgumentException("Month is required");
        }

        if (!record.getMonth().matches("[0-9]{4}-(0[1-9]|1[0-2])"))
            throw new IllegalArgumentException("Month must be in YYYY-MM format");
        try {
            YearMonth.parse(record.getMonth());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Month must be in YYYY-MM format"
            );
        }

        validateNonNegative("Cash inflow", record.getCashInflow());
        validateNonNegative("Cash outflow", record.getCashOutflow());
        validateNonNegative("Revenue", record.getRevenue());
        validateOptionalNonNegative("COGS", record.getCogs());
        validateNonNegative(
                "Operating expenses",
                record.getOperatingExpenses()
        );
        validateNonNegative(
                "Cash balance",
                record.getCashBalanceEom()
        );

        validateOptionalNonNegative(
                "Receivables outstanding",
                record.getReceivablesOutstanding()
        );

        validateOptionalNonNegative(
                "Payables outstanding",
                record.getPayablesOutstanding()
        );

        validateOptionalNonNegative(
                "Inventory value",
                record.getInventoryValue()
        );

        validateOptionalNonNegative(
                "Loan outstanding",
                record.getLoanOutstanding()
        );

        validateOptionalNonNegative(
                "Interest expense",
                record.getInterestExpense()
        );

        validateFinancingType(record);
    }

    private void validateFinancingType(MonthlyRecord record) {
        String financingType = record.getFinancingType();

        if (financingType == null) {
            record.setFinancingType("none");
            return;
        }

        if (!VALID_FINANCING_TYPES.contains(financingType)) {
            throw new IllegalArgumentException(
                    "financingType must be one of: none, conventional, islamic"
            );
        }
    }

    private static void validateDatabaseAmount(String field, BigDecimal value) {
        if (value != null && (value.scale() > 2 || (long) value.precision() - value.scale() > 12))
            throw new IllegalArgumentException(field + " must fit 12 integer digits and at most 2 decimal places");
    }

    private void validateNonNegative(
            String fieldName,
            BigDecimal value
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName + " is required"
            );
        }

        validateDatabaseAmount(fieldName, value);
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be negative"
            );
        }
    }

    private void validateOptionalNonNegative(
            String fieldName,
            BigDecimal value
    ) {
        validateDatabaseAmount(fieldName, value);
        if (value != null &&
                value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be negative"
            );
        }
    }

    private void updateExistingRecord(
            MonthlyRecord existing,
            MonthlyRecord incoming
    ) {
        existing.setCashInflow(incoming.getCashInflow());
        existing.setCashOutflow(incoming.getCashOutflow());
        existing.setRevenue(incoming.getRevenue());
        existing.setCogs(incoming.getCogs());
        existing.setOperatingExpenses(
                incoming.getOperatingExpenses()
        );
        existing.setCashBalanceEom(
                incoming.getCashBalanceEom()
        );

        existing.setReceivablesOutstanding(
                incoming.getReceivablesOutstanding()
        );
        existing.setPayablesOutstanding(
                incoming.getPayablesOutstanding()
        );
        existing.setInventoryValue(
                incoming.getInventoryValue()
        );
        existing.setLoanOutstanding(
                incoming.getLoanOutstanding()
        );
        existing.setInterestExpense(
                incoming.getInterestExpense()
        );
        existing.setFinancingType(
                incoming.getFinancingType()
        );
        existing.setUpdatedAt(
                incoming.getUpdatedAt()
        );
    }
}