package com.app.sme_health_backend.cashflow.service;

import com.app.sme_health_backend.cashflow.calculator.TrendProjectionCalculator;
import com.app.sme_health_backend.cashflow.calculator.TrendProjectionCalculator.TrendProjectionResult;
import com.app.sme_health_backend.cashflow.dto.CashFlowChartPointResponse;
import com.app.sme_health_backend.cashflow.dto.CashFlowProjectionResponse;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class CashFlowService {

    private final MonthlyRecordRepository monthlyRecordRepository;
    private final TrendProjectionCalculator trendProjectionCalculator;

    public CashFlowService(MonthlyRecordRepository monthlyRecordRepository) {
        this(monthlyRecordRepository, new TrendProjectionCalculator());
    }

    @Autowired
    public CashFlowService(
            MonthlyRecordRepository monthlyRecordRepository,
            TrendProjectionCalculator trendProjectionCalculator
    ) {
        this.monthlyRecordRepository = monthlyRecordRepository;
        this.trendProjectionCalculator = trendProjectionCalculator;
    }

    @Transactional(readOnly = true)
    public List<CashFlowChartPointResponse> getCashFlowHistory(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        List<MonthlyRecord> records =
                monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId);

        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        // Reverse descending records into chronological ascending order
        List<CashFlowChartPointResponse> chartPoints = new ArrayList<>(records.size());
        for (int i = records.size() - 1; i >= 0; i--) {
            chartPoints.add(CashFlowChartPointResponse.fromEntity(records.get(i)));
        }

        return chartPoints;
    }

    @Transactional(readOnly = true)
    public CashFlowProjectionResponse getTrendProjection(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        List<MonthlyRecord> records =
                monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId);

        if (records == null || records.isEmpty()) {
            return CashFlowProjectionResponse.insufficientData(0);
        }

        int count = records.size();
        if (count < 3) {
            return CashFlowProjectionResponse.insufficientData(count);
        }

        // records are sorted descending by month (records.get(0) is latest)
        String latestMonth = records.get(0).getMonth();
        String projectedMonth = YearMonth.parse(latestMonth).plusMonths(1).toString();

        // Reverse into chronological order (oldest to newest)
        List<String> months = new ArrayList<>(count);
        List<BigDecimal> netCashFlows = new ArrayList<>(count);
        for (int i = count - 1; i >= 0; i--) {
            MonthlyRecord record = records.get(i);
            months.add(record.getMonth());
            BigDecimal inflow = record.getCashInflow() != null ? record.getCashInflow() : BigDecimal.ZERO;
            BigDecimal outflow = record.getCashOutflow() != null ? record.getCashOutflow() : BigDecimal.ZERO;
            netCashFlows.add(inflow.subtract(outflow));
        }

        TrendProjectionResult result = trendProjectionCalculator.calculate(months, netCashFlows);
        if (result == null) {
            return CashFlowProjectionResponse.insufficientData(count);
        }

        return new CashFlowProjectionResponse(
                projectedMonth,
                result.projectedNetCashFlow(),
                result.trendDirection(),
                result.confidence(),
                count,
                null
        );
    }
}
