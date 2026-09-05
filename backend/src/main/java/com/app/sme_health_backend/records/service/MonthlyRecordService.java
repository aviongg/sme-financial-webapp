package com.app.sme_health_backend.records.service;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MonthlyRecordService {

    private final MonthlyRecordRepository monthlyRecordRepository;

    public MonthlyRecordService(MonthlyRecordRepository monthlyRecordRepository) {
        this.monthlyRecordRepository = monthlyRecordRepository;
    }

    @Transactional
    public MonthlyRecord saveMonthlyRecord(MonthlyRecord record) {
        validateMonthlyRecord(record);

        record.setUpdatedAt(LocalDateTime.now());

        return monthlyRecordRepository
                .findByUserIdAndMonth(record.getUserId(), record.getMonth())
                .map(existingRecord -> {
                    updateExistingRecord(existingRecord, record);
                    return monthlyRecordRepository.save(existingRecord);
                })
                .orElseGet(() -> monthlyRecordRepository.save(record));
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

    private void validateMonthlyRecord(MonthlyRecord record) {

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
        validateNonNegative("COGS", record.getCogs());
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

        if (record.getFinancingType() == null ||
                record.getFinancingType().isBlank()) {
            record.setFinancingType("none");
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