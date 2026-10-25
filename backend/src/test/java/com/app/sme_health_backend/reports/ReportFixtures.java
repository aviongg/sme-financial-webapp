package com.app.sme_health_backend.reports;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.scoring.dto.ComponentScoresDto;
import com.app.sme_health_backend.scoring.entity.ScoreResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Synthetic fixtures only. */
final class ReportFixtures {
    private ReportFixtures() { }

    static MonthlyRecord record(String month, String revenue, String cogs, String opex, String inflow, String outflow,
                                String balance, String receivables, String payables, String inventory, String loan, String interest) {
        MonthlyRecord record = new MonthlyRecord();
        record.setUserId(UUID.fromString("00000000-0000-0000-0000-00000000f12b"));
        record.setMonth(month);
        record.setRevenue(decimal(revenue));
        record.setCogs(decimal(cogs));
        record.setOperatingExpenses(decimal(opex));
        record.setCashInflow(decimal(inflow));
        record.setCashOutflow(decimal(outflow));
        record.setCashBalanceEom(decimal(balance));
        record.setReceivablesOutstanding(decimal(receivables));
        record.setPayablesOutstanding(decimal(payables));
        record.setInventoryValue(decimal(inventory));
        record.setLoanOutstanding(decimal(loan));
        record.setInterestExpense(decimal(interest));
        record.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return record;
    }

    static MonthlyRecord full(String month, String revenue, String cogs, String opex, String inflow, String outflow, String balance) {
        return record(month, revenue, cogs, opex, inflow, outflow, balance, "100.00", "50.00", "75.00", "1000.00", "5.00");
    }

    static ScoreResult score(String month, String composite) {
        ScoreResult score = new ScoreResult();
        score.setUserId(UUID.fromString("00000000-0000-0000-0000-00000000f12b"));
        score.setMonth(month);
        score.setCompositeScore(new BigDecimal(composite));
        score.setBand("moderate");
        score.setComponentScores(new ComponentScoresDto(new BigDecimal("70.00"), new BigDecimal("60.00"), null,
                new BigDecimal("50.00"), new BigDecimal("40.00")));
        score.setWeakestComponent("compliance");
        score.setDataCompleteness(new BigDecimal("0.85"));
        score.setMethodologyVersion("health-score-v1");
        score.setComputedAt(LocalDateTime.of(2026, 3, 31, 12, 0));
        return score;
    }

    static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
