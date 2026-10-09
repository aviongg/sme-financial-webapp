package com.app.sme_health_backend.cashflow.calculator;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class TrendProjectionCalculator {

    private static final BigDecimal LOW_CONFIDENCE_THRESHOLD = new BigDecimal("0.30");
    private static final BigDecimal MODERATE_CONFIDENCE_THRESHOLD = new BigDecimal("0.60");
    private static final int INTERNAL_SCALE = 10;
    private static final int OUTPUT_SCALE = 2;

    public record TrendProjectionResult(
            BigDecimal projectedNetCashFlow,
            String trendDirection,
            String confidence,
            BigDecimal rSquared,
            BigDecimal slope,
            BigDecimal intercept
    ) {}

    public TrendProjectionResult calculate(List<BigDecimal> netCashFlows) {
        if (netCashFlows == null || netCashFlows.size() < 3) {
            return null;
        }

        List<String> syntheticMonths = new ArrayList<>(netCashFlows.size());
        YearMonth start = YearMonth.of(2026, 1);
        for (int i = 0; i < netCashFlows.size(); i++) {
            syntheticMonths.add(start.plusMonths(i).toString());
        }

        return calculate(syntheticMonths, netCashFlows);
    }

    public TrendProjectionResult calculate(List<String> months, List<BigDecimal> netCashFlows) {
        if (months == null || netCashFlows == null || months.size() < 3 || netCashFlows.size() < 3
                || months.size() != netCashFlows.size()) {
            return null;
        }

        // Limit window to the latest 6 historical records
        int windowSize = Math.min(6, netCashFlows.size());
        List<String> windowMonths = months.subList(months.size() - windowSize, months.size());
        List<BigDecimal> windowFlows = netCashFlows.subList(netCashFlows.size() - windowSize, netCashFlows.size());

        int n = windowFlows.size();
        BigDecimal bigN = BigDecimal.valueOf(n);

        YearMonth startMonth = YearMonth.parse(windowMonths.get(0));
        YearMonth latestMonth = YearMonth.parse(windowMonths.get(windowMonths.size() - 1));

        List<BigDecimal> xValues = new ArrayList<>(n);
        BigDecimal sumX = BigDecimal.ZERO;
        BigDecimal sumY = BigDecimal.ZERO;

        for (int i = 0; i < n; i++) {
            YearMonth currentMonth = YearMonth.parse(windowMonths.get(i));
            long elapsedMonths = ChronoUnit.MONTHS.between(startMonth, currentMonth);
            BigDecimal x = BigDecimal.valueOf(elapsedMonths);
            xValues.add(x);
            sumX = sumX.add(x);

            BigDecimal y = windowFlows.get(i) != null ? windowFlows.get(i) : BigDecimal.ZERO;
            sumY = sumY.add(y);
        }

        BigDecimal xMean = sumX.divide(bigN, INTERNAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal yMean = sumY.divide(bigN, INTERNAL_SCALE, RoundingMode.HALF_UP);

        BigDecimal sumXDiffYDiff = BigDecimal.ZERO;
        BigDecimal sumXDiff2 = BigDecimal.ZERO;
        BigDecimal ssTot = BigDecimal.ZERO;

        for (int i = 0; i < n; i++) {
            BigDecimal xDiff = xValues.get(i).subtract(xMean);
            BigDecimal y = windowFlows.get(i) != null ? windowFlows.get(i) : BigDecimal.ZERO;
            BigDecimal yDiff = y.subtract(yMean);

            sumXDiffYDiff = sumXDiffYDiff.add(xDiff.multiply(yDiff));
            sumXDiff2 = sumXDiff2.add(xDiff.multiply(xDiff));
            ssTot = ssTot.add(yDiff.multiply(yDiff));
        }

        // Least-squares slope and intercept
        BigDecimal slope;
        if (sumXDiff2.compareTo(BigDecimal.ZERO) == 0) {
            slope = BigDecimal.ZERO;
        } else {
            slope = sumXDiffYDiff.divide(sumXDiff2, INTERNAL_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal intercept = yMean.subtract(slope.multiply(xMean));

        // Evaluate projection at the month immediately following the latest historical record
        YearMonth projectedTargetMonth = latestMonth.plusMonths(1);
        long elapsedProjected = ChronoUnit.MONTHS.between(startMonth, projectedTargetMonth);
        BigDecimal xProj = BigDecimal.valueOf(elapsedProjected);
        BigDecimal projectedNextMonth = intercept.add(slope.multiply(xProj));

        // Calculate R-squared: SS_res = sum((y_i - (intercept + slope * x_i))^2)
        BigDecimal ssRes = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            BigDecimal x = xValues.get(i);
            BigDecimal predictedY = intercept.add(slope.multiply(x));
            BigDecimal y = windowFlows.get(i) != null ? windowFlows.get(i) : BigDecimal.ZERO;
            BigDecimal residual = y.subtract(predictedY);
            ssRes = ssRes.add(residual.multiply(residual));
        }

        BigDecimal rSquared;
        if (ssTot.compareTo(BigDecimal.ZERO) == 0) {
            // Constant series (no variance in y): perfect horizontal fit
            rSquared = BigDecimal.ONE;
        } else {
            BigDecimal ratio = ssRes.divide(ssTot, INTERNAL_SCALE, RoundingMode.HALF_UP);
            rSquared = BigDecimal.ONE.subtract(ratio);
            // Clamp R^2 to [0.00, 1.00]
            if (rSquared.compareTo(BigDecimal.ZERO) < 0) {
                rSquared = BigDecimal.ZERO;
            } else if (rSquared.compareTo(BigDecimal.ONE) > 0) {
                rSquared = BigDecimal.ONE;
            }
        }

        // Trend Direction
        String trendDirection;
        if (slope.compareTo(BigDecimal.ZERO) > 0) {
            trendDirection = "upward";
        } else if (slope.compareTo(BigDecimal.ZERO) < 0) {
            trendDirection = "downward";
        } else {
            trendDirection = "flat";
        }

        // Confidence
        String confidence;
        if (rSquared.compareTo(LOW_CONFIDENCE_THRESHOLD) < 0) {
            confidence = "low";
        } else if (rSquared.compareTo(MODERATE_CONFIDENCE_THRESHOLD) < 0) {
            confidence = "moderate";
        } else {
            confidence = "reasonable";
        }

        BigDecimal finalProjection = projectedNextMonth.setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);

        return new TrendProjectionResult(
                finalProjection,
                trendDirection,
                confidence,
                rSquared.setScale(4, RoundingMode.HALF_UP),
                slope.setScale(4, RoundingMode.HALF_UP),
                intercept.setScale(4, RoundingMode.HALF_UP)
        );
    }
}
