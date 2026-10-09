package com.app.sme_health_backend.scoring.calculator;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ProfitabilityEfficiencyCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal FIFTY = new BigDecimal("50.00");
    private static final BigDecimal SEVENTY = new BigDecimal("70.00");
    private static final BigDecimal THIRTY = new BigDecimal("30.00");
    private static final BigDecimal FIFTEEN = new BigDecimal("15.00");
    private static final BigDecimal TWO = new BigDecimal("2.00");
    private static final BigDecimal HALF = new BigDecimal("0.50");

    public BigDecimal calculate(MonthlyRecord record, BusinessProfile profile) {
        if (record == null) {
            return null;
        }

        BigDecimal netMarginScore = calculateNetMarginScore(record, profile);
        BigDecimal dsoScore = calculateDsoScore(record);

        if (netMarginScore != null && dsoScore != null) {
            return netMarginScore.add(dsoScore).divide(TWO, 2, RoundingMode.HALF_UP);
        } else if (netMarginScore != null) {
            return netMarginScore.setScale(2, RoundingMode.HALF_UP);
        } else if (dsoScore != null) {
            return dsoScore.setScale(2, RoundingMode.HALF_UP);
        } else {
            return null;
        }
    }

    public BigDecimal calculateNetMarginScore(MonthlyRecord record, BusinessProfile profile) {
        BigDecimal revenue = record.getRevenue();
        if (revenue == null || revenue.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal cogs = record.getCogs() != null ? record.getCogs() : BigDecimal.ZERO;
        BigDecimal opex = record.getOperatingExpenses() != null ? record.getOperatingExpenses() : BigDecimal.ZERO;

        BigDecimal netProfit = revenue.subtract(cogs).subtract(opex);
        BigDecimal netMargin = netProfit.divide(revenue, 8, RoundingMode.HALF_UP);

        BigDecimal good;
        BigDecimal ok;

        String businessType = profile != null && profile.getBusinessType() != null
                ? profile.getBusinessType().toLowerCase()
                : "retail";

        switch (businessType) {
            case "trade" -> {
                good = new BigDecimal("0.15");
                ok = new BigDecimal("0.08");
            }
            case "manufacturing" -> {
                good = new BigDecimal("0.25");
                ok = new BigDecimal("0.12");
            }
            case "services" -> {
                good = new BigDecimal("0.35");
                ok = new BigDecimal("0.18");
            }
            case "retail" -> {
                good = new BigDecimal("0.20");
                ok = new BigDecimal("0.10");
            }
            default -> {
                good = new BigDecimal("0.20");
                ok = new BigDecimal("0.10");
            }
        }

        BigDecimal score;
        if (netMargin.compareTo(good) >= 0) {
            score = HUNDRED;
        } else if (netMargin.compareTo(ok) >= 0) {
            BigDecimal range = good.subtract(ok);
            BigDecimal diff = netMargin.subtract(ok);
            score = FIFTY.add(diff.divide(range, 8, RoundingMode.HALF_UP).multiply(FIFTY));
        } else if (netMargin.compareTo(BigDecimal.ZERO) >= 0) {
            score = netMargin.divide(ok, 8, RoundingMode.HALF_UP).multiply(FIFTY);
        } else {
            score = BigDecimal.ZERO;
        }

        return clamp(score);
    }

    public BigDecimal calculateDsoScore(MonthlyRecord record) {
        BigDecimal receivables = record.getReceivablesOutstanding();
        BigDecimal revenue = record.getRevenue();

        if (receivables == null || revenue == null || revenue.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal dso = receivables.divide(revenue, 8, RoundingMode.HALF_UP).multiply(THIRTY);

        BigDecimal score;
        if (dso.compareTo(FIFTEEN) <= 0) {
            score = HUNDRED;
        } else if (dso.compareTo(THIRTY) <= 0) {
            BigDecimal diff = dso.subtract(FIFTEEN);
            score = HUNDRED.subtract(diff.divide(FIFTEEN, 8, RoundingMode.HALF_UP).multiply(THIRTY));
        } else if (dso.compareTo(new BigDecimal("60.00")) <= 0) {
            BigDecimal diff = dso.subtract(THIRTY);
            score = SEVENTY.subtract(diff.divide(THIRTY, 8, RoundingMode.HALF_UP).multiply(new BigDecimal("40.00")));
        } else {
            BigDecimal diff = dso.subtract(new BigDecimal("60.00"));
            score = THIRTY.subtract(diff.multiply(HALF));
            if (score.compareTo(BigDecimal.ZERO) < 0) {
                score = BigDecimal.ZERO;
            }
        }

        return clamp(score);
    }

    private BigDecimal clamp(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (value.compareTo(HUNDRED) > 0) {
            return HUNDRED.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
