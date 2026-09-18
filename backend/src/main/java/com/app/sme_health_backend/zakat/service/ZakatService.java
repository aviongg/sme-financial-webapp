package com.app.sme_health_backend.zakat.service;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import com.app.sme_health_backend.zakat.dto.MonthlyRecordZakatRequest;
import com.app.sme_health_backend.zakat.dto.ZakatPreviewRequest;
import com.app.sme_health_backend.zakat.dto.ZakatPreviewResponse;
import com.app.sme_health_backend.zakat.dto.ZakatPreviewResponse.MonthlyRecordSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import static com.app.sme_health_backend.zakat.dto.ZakatPreviewRequest.*;

@Service
public class ZakatService {
    private final MonthlyRecordRepository monthlyRecordRepository;
    private final ZakatCalculationService calculationService;

    public ZakatService(MonthlyRecordRepository monthlyRecordRepository, ZakatCalculationService calculationService) {
        this.monthlyRecordRepository = monthlyRecordRepository;
        this.calculationService = calculationService;
    }

    public ZakatPreviewResponse preview(ZakatPreviewRequest request) {
        return calculationService.calculate(request);
    }

    @Transactional(readOnly = true)
    public ZakatPreviewResponse previewMonthlyRecord(UUID userId, String month, MonthlyRecordZakatRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        YearMonth period = parseMonth(month);
        if (request == null) {
            throw new IllegalArgumentException("Supplemental Zakat inputs are required");
        }
        MonthlyRecord record = monthlyRecordRepository.findByUserIdAndMonth(userId, month)
                .orElseThrow(() -> new ResourceNotFoundException("Monthly record not found for the user and month"));

        List<InventoryItem> inventory = null;
        if (request.inventory() != null) {
            InventoryItem declaration = request.inventory();
            inventory = List.of(new InventoryItem(declaration.reference(), declaration.type(),
                    declaration.amount() == null ? record.getInventoryValue() : declaration.amount(), declaration.valuation()));
        } else if (isZero(record.getInventoryValue())) {
            inventory = List.of();
        }

        List<Receivable> receivables = request.receivables();
        if (receivables == null && isZero(record.getReceivablesOutstanding())) {
            receivables = List.of();
        }
        reconcileReceivables(receivables, record.getReceivablesOutstanding());

        ZakatPreviewRequest normalized = new ZakatPreviewRequest(request.assessment(),
                new Assets(record.getCashBalanceEom(), inventory, receivables, request.unsupportedCategories()),
                new Liabilities(record.getPayablesOutstanding(), request.currentPayables(),
                        request.principalDueWithin12LunarMonths(), request.principalExcludedFromPayables()),
                new Financing(record.getFinancingType(), record.getLoanOutstanding(), record.getInterestExpense()));
        MonthlyRecordSource source = new MonthlyRecordSource(userId, month, period.atEndOfMonth(),
                record.getCashBalanceEom(), record.getInventoryValue(), record.getReceivablesOutstanding(),
                record.getPayablesOutstanding(), record.getLoanOutstanding(), record.getInterestExpense(), record.getFinancingType());
        return calculationService.calculate(normalized, source);
    }

    private YearMonth parseMonth(String month) {
        if (month == null || !month.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("Month must be in YYYY-MM format");
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Month must be in YYYY-MM format");
        }
    }

    private void reconcileReceivables(List<Receivable> receivables, BigDecimal outstanding) {
        if (receivables == null || outstanding == null) {
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Receivable receivable : receivables) {
            if (receivable == null || receivable.amount() == null) {
                return; // The calculation service reports incomplete/invalid individual entries.
            }
            total = total.add(receivable.amount());
        }
        if (total.compareTo(outstanding) != 0) {
            throw new IllegalArgumentException("Classified receivables must total the monthly record's receivablesOutstanding");
        }
    }

    private boolean isZero(BigDecimal value) {
        return value != null && value.signum() == 0;
    }
}
