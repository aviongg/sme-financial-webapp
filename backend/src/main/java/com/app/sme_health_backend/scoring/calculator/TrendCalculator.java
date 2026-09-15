package com.app.sme_health_backend.scoring.calculator;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class TrendCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal FIFTY = new BigDecimal("50.00");

    public BigDecimal calculate(List<MonthlyRecord> history) {
        if (history == null || history.size() < 3) {
            return null;
        }

        int windowSize = Math.min(6, history.size());
        List<MonthlyRecord> window = new ArrayList<>(history.subList(0, windowSize));

        // Ensure records are sorted chronologically (oldest to newest) before assigning x = 0, 1, ... N-1
        window.sort(Comparator.comparing(MonthlyRecord::getMonth));

        int n = window.size();
        BigDecimal sumX = BigDecimal.ZERO;
        BigDecimal sumY = BigDecimal.ZERO;
        BigDecimal sumXY = BigDecimal.ZERO;
        BigDecimal sumX2 = BigDecimal.ZERO;
        BigDecimal sumAbsY = BigDecimal.ZERO;

        for (int i = 0; i < n; i++) {
            BigDecimal x = BigDecimal.valueOf(i);
            MonthlyRecord record = window.get(i);
            BigDecimal inflow = record.getCashInflow() != null ? record.getCashInflow() : BigDecimal.ZERO;
            BigDecimal outflow = record.getCashOutflow() != null ? record.getCashOutflow() : BigDecimal.ZERO;
            BigDecimal y = inflow.subtract(outflow);

            sumX = sumX.add(x);
            sumY = sumY.add(y);
            sumXY = sumXY.add(x.multiply(y));
            sumX2 = sumX2.add(x.multiply(x));
            sumAbsY = sumAbsY.add(y.abs());
        }

        BigDecimal bigN = BigDecimal.valueOf(n);
        BigDecimal denominator = bigN.multiply(sumX2).subtract(sumX.multiply(sumX));

        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return FIFTY.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal numerator = bigN.multiply(sumXY).subtract(sumX.multiply(sumY));
        BigDecimal slope = numerator.divide(denominator, 8, RoundingMode.HALF_UP);

        BigDecimal avgAbsNetCashFlow = sumAbsY.divide(bigN, 8, RoundingMode.HALF_UP);

        if (avgAbsNetCashFlow.compareTo(BigDecimal.ZERO) == 0) {
            return FIFTY.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal normalizedSlope = slope.divide(avgAbsNetCashFlow, 8, RoundingMode.HALF_UP);
        BigDecimal trendScore = FIFTY.add(normalizedSlope.multiply(FIFTY));

        return clampAndScale(trendScore);
    }

    private BigDecimal clampAndScale(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            value = BigDecimal.ZERO;
        } else if (value.compareTo(HUNDRED) > 0) {
            value = HUNDRED;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
