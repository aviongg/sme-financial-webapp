package com.app.sme_health_backend.scoring.calculator;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class CashFlowStabilityCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal FIFTY = new BigDecimal("50.00");
    private static final BigDecimal TWO = new BigDecimal("2.00");
    private static final BigDecimal ONE = new BigDecimal("1.00");
    private static final BigDecimal WEIGHT_VARIANCE = new BigDecimal("0.60");
    private static final BigDecimal WEIGHT_BUFFER = new BigDecimal("0.40");

    public BigDecimal calculate(List<MonthlyRecord> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }

        int windowSize = Math.min(6, history.size());
        List<MonthlyRecord> window = history.subList(0, windowSize);

        BigDecimal varianceScore = calculateVarianceScore(window);
        BigDecimal bufferScore = calculateBufferScore(history);

        if (windowSize >= 3) {
            if (varianceScore != null && bufferScore != null) {
                BigDecimal combined = varianceScore.multiply(WEIGHT_VARIANCE)
                        .add(bufferScore.multiply(WEIGHT_BUFFER));
                return clampAndScale(combined);
            } else if (varianceScore != null) {
                return clampAndScale(varianceScore);
            } else if (bufferScore != null) {
                return clampAndScale(bufferScore);
            } else {
                return null;
            }
        } else {
            // For fewer than 3 months, CashFlowStabilityScore = bufferScore
            return bufferScore != null ? clampAndScale(bufferScore) : null;
        }
    }

    public BigDecimal calculateVarianceScore(List<MonthlyRecord> window) {
        if (window == null || window.size() < 3) {
            return null;
        }

        int n = window.size();
        List<BigDecimal> netCashFlows = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;

        for (MonthlyRecord record : window) {
            BigDecimal inflow = record.getCashInflow() != null ? record.getCashInflow() : BigDecimal.ZERO;
            BigDecimal outflow = record.getCashOutflow() != null ? record.getCashOutflow() : BigDecimal.ZERO;
            BigDecimal ncf = inflow.subtract(outflow);
            netCashFlows.add(ncf);
            sum = sum.add(ncf);
        }

        BigDecimal mean = sum.divide(BigDecimal.valueOf(n), 8, RoundingMode.HALF_UP);

        BigDecimal sumSquaredDiff = BigDecimal.ZERO;
        for (BigDecimal ncf : netCashFlows) {
            BigDecimal diff = ncf.subtract(mean);
            sumSquaredDiff = sumSquaredDiff.add(diff.multiply(diff));
        }

        BigDecimal sampleVariance = sumSquaredDiff.divide(BigDecimal.valueOf(n - 1), 8, RoundingMode.HALF_UP);
        BigDecimal stdDev = sampleVariance.sqrt(MathContext.DECIMAL64);

        BigDecimal cv;
        if (mean.compareTo(BigDecimal.ZERO) == 0) {
            cv = BigDecimal.ONE;
        } else {
            cv = stdDev.divide(mean.abs(), 8, RoundingMode.HALF_UP);
        }

        BigDecimal varianceScore = HUNDRED.subtract(cv.multiply(HUNDRED));
        if (varianceScore.compareTo(BigDecimal.ZERO) < 0) {
            varianceScore = BigDecimal.ZERO;
        }
        return clampAndScale(varianceScore);
    }

    public BigDecimal calculateBufferScore(List<MonthlyRecord> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }

        MonthlyRecord target = history.get(0);
        BigDecimal cashBalanceEom = target.getCashBalanceEom();
        if (cashBalanceEom == null) {
            cashBalanceEom = BigDecimal.ZERO;
        }

        int bufferMonths = Math.min(3, history.size());
        BigDecimal sumOpex = BigDecimal.ZERO;
        for (int i = 0; i < bufferMonths; i++) {
            BigDecimal opex = history.get(i).getOperatingExpenses();
            if (opex != null) {
                sumOpex = sumOpex.add(opex);
            }
        }

        BigDecimal avgOpex = sumOpex.divide(BigDecimal.valueOf(bufferMonths), 8, RoundingMode.HALF_UP);

        // Per user correction: If average operating expenses are unavailable or zero, return Cash Buffer Score as null.
        if (avgOpex.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal ratio = cashBalanceEom.divide(avgOpex, 8, RoundingMode.HALF_UP);
        BigDecimal bufferScore;

        if (ratio.compareTo(TWO) >= 0) {
            bufferScore = HUNDRED;
        } else if (ratio.compareTo(ONE) >= 0) {
            bufferScore = FIFTY.add(ratio.subtract(ONE).multiply(FIFTY));
        } else {
            bufferScore = ratio.multiply(FIFTY);
        }

        return clampAndScale(bufferScore);
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
