package com.app.sme_health_backend.cashflow.service;

import com.app.sme_health_backend.cashflow.dto.CashFlowChartPointResponse;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class CashFlowService {

    private final MonthlyRecordRepository monthlyRecordRepository;

    public CashFlowService(MonthlyRecordRepository monthlyRecordRepository) {
        this.monthlyRecordRepository = monthlyRecordRepository;
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
}
