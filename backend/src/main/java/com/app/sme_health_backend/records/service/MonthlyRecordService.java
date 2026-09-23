package com.app.sme_health_backend.records.service;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.scoring.service.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    public MonthlyRecordService(MonthlyRecordRepository monthlyRecordRepository) {
        this(monthlyRecordRepository, null);
    }

    @Autowired
    public MonthlyRecordService(
            MonthlyRecordRepository monthlyRecordRepository,
            @Autowired(required = false) ScoringService scoringService
    ) {
        this.monthlyRecordRepository = monthlyRecordRepository;
        this.scoringService = scoringService;
    }

    @Transactional
    public MonthlyRecord saveMonthlyRecord(MonthlyRecord record) {
        validateMonthlyRecord(record);

        record.setUpdatedAt(LocalDateTime.now());

        MonthlyRecord saved = monthlyRecordRepository
                .findByUserIdAndMonth(record.getUserId(), record.getMonth())
                .map(existingRecord -> {
                    updateExistingRecord(existingRecord, record);
                    return monthlyRecordRepository.save(existingRecord);
                })
                .orElseGet(() -> monthlyRecordRepository.save(record));

        if (scoringService != null) {
            recalculateAffectedScores(saved.getUserId(), saved.getMonth());
        }

        return saved;
    }

    @Transactional
    public MonthlyRecord applyDocumentContribution(
            UUID userId,
            String targetMonth,
            String targetClassification,
            String cashFlowImpact,
            BigDecimal amount,
            BigDecimal initialCashBalanceEom
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (targetMonth == null || targetMonth.isBlank()) {
            throw new IllegalArgumentException("Target month is required");
        }
        try {
            YearMonth.parse(targetMonth);
        } catch (Exception e) {
            throw new IllegalArgumentException("Target month must be in YYYY-MM format");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Confirmed amount must be greater than zero");
        }

        String classification = targetClassification != null ? targetClassification.trim().toLowerCase() : "none";
        String flowImpact = cashFlowImpact != null ? cashFlowImpact.trim().toLowerCase() : "none";

        MonthlyRecord existing = monthlyRecordRepository.findByUserIdAndMonth(userId, targetMonth).orElse(null);
        MonthlyRecord recordToSave;

        if (existing != null) {
            // Increment ONLY period-flow fields; point-in-time balances remain untouched
            if ("revenue".equals(classification)) {
                existing.setRevenue(existing.getRevenue().add(amount));
            } else if ("operating_expenses".equals(classification)) {
                existing.setOperatingExpenses(existing.getOperatingExpenses().add(amount));
            } else if ("cogs".equals(classification)) {
                BigDecimal currentCogs = existing.getCogs() != null ? existing.getCogs() : BigDecimal.ZERO;
                existing.setCogs(currentCogs.add(amount));
            }

            if ("cash_inflow".equals(flowImpact)) {
                existing.setCashInflow(existing.getCashInflow().add(amount));
            } else if ("cash_outflow".equals(flowImpact)) {
                existing.setCashOutflow(existing.getCashOutflow().add(amount));
            }

            existing.setUpdatedAt(LocalDateTime.now());
            recordToSave = existing;
        } else {
            // Month does not exist: require initial cash balance to avoid fabricating point-in-time balances
            if (initialCashBalanceEom == null) {
                throw new IllegalArgumentException(
                        "No monthly record exists for " + targetMonth
                                + ". An initial cash balance (initialCashBalanceEom) is required to initialize this month's record."
                );
            }
            if (initialCashBalanceEom.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Initial cash balance cannot be negative");
            }

            MonthlyRecord newRecord = new MonthlyRecord();
            newRecord.setUserId(userId);
            newRecord.setMonth(targetMonth);
            newRecord.setRevenue("revenue".equals(classification) ? amount : BigDecimal.ZERO);
            newRecord.setOperatingExpenses("operating_expenses".equals(classification) ? amount : BigDecimal.ZERO);
            newRecord.setCogs("cogs".equals(classification) ? amount : null);
            newRecord.setCashInflow("cash_inflow".equals(flowImpact) ? amount : BigDecimal.ZERO);
            newRecord.setCashOutflow("cash_outflow".equals(flowImpact) ? amount : BigDecimal.ZERO);
            newRecord.setCashBalanceEom(initialCashBalanceEom);
            newRecord.setFinancingType("none");
            newRecord.setUpdatedAt(LocalDateTime.now());

            validateMonthlyRecord(newRecord);
            recordToSave = newRecord;
        }

        MonthlyRecord saved = monthlyRecordRepository.save(recordToSave);

        if (scoringService != null) {
            recalculateAffectedScores(saved.getUserId(), saved.getMonth());
        }

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
    public Optional<MonthlyRecord> getRecordById(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Record ID is required");
        }

        return monthlyRecordRepository.findById(id);
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

    private void validateNonNegative(
            String fieldName,
            BigDecimal value
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName + " is required"
            );
        }

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