package com.app.sme_health_backend.scoring.service;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.scoring.calculator.CashFlowStabilityCalculator;
import com.app.sme_health_backend.scoring.calculator.ProfitabilityEfficiencyCalculator;
import com.app.sme_health_backend.scoring.dto.ScoreExplanation;
import com.app.sme_health_backend.scoring.dto.ScoreExplanation.*;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Describes the existing calculators without changing their results or substituting missing inputs. */
final class ScoreExplanationBuilder {
    private ScoreExplanationBuilder() { }

    static ScoreExplanation build(ScoreResult score, BusinessProfile profile, List<MonthlyRecord> history,
                                  Map<String, BigDecimal> weights, CashFlowStabilityCalculator cash,
                                  ProfitabilityEfficiencyCalculator profit, ScoreResult previous) {
        MonthlyRecord target = history.getFirst();
        List<MonthlyRecord> window = history.subList(0, Math.min(6, history.size()));
        List<MonthlyRecord> bufferWindow = history.subList(0, Math.min(3, history.size()));
        BigDecimal avgOpex = bufferWindow.stream().map(r -> zero(r.getOperatingExpenses()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(bufferWindow.size()), 8, RoundingMode.HALF_UP);
        BigDecimal buffer = avgOpex.signum() > 0 ? ratio(zero(target.getCashBalanceEom()), avgOpex) : null;
        BigDecimal revenue = target.getRevenue();
        BigDecimal margin = revenue != null && revenue.signum() > 0
                ? ratio(revenue.subtract(zero(target.getCogs())).subtract(zero(target.getOperatingExpenses())), revenue) : null;
        BigDecimal dso = target.getReceivablesOutstanding() != null && revenue != null && revenue.signum() > 0
                ? ratio(target.getReceivablesOutstanding(), revenue).multiply(BigDecimal.valueOf(30)) : null;
        String businessType = profile.getBusinessType() == null ? "retail" : profile.getBusinessType().toLowerCase(Locale.ROOT);
        // These are the unchanged industry benchmarks used by the profitability calculator.
        String good = switch (businessType) { case "trade" -> "0.15"; case "manufacturing" -> "0.25"; case "services" -> "0.35"; default -> "0.20"; };
        String ok = switch (businessType) { case "trade" -> "0.08"; case "manufacturing" -> "0.12"; case "services" -> "0.18"; default -> "0.10"; };
        Map<String, List<Driver>> evidence = new LinkedHashMap<>();
        evidence.put("cashflow", List.of(driver("cashBalanceEom", target.getCashBalanceEom(), "PKR"),
                driver("averageOperatingExpenses", avgOpex, "PKR"), driver("cashBufferMonths", buffer, "months"),
                driver("bufferScore", cash.calculateBufferScore(history), "points"),
                driver("varianceScore", cash.calculateVarianceScore(window), "points"),
                driver("operatingExpensesHistory", series(bufferWindow, MonthlyRecord::getOperatingExpenses), "PKR"),
                driver("netCashFlowHistory", series(window, r -> zero(r.getCashInflow()).subtract(zero(r.getCashOutflow()))), "PKR")));
        evidence.put("profitability", List.of(driver("revenue", revenue, "PKR"), driver("cogs", target.getCogs(), "PKR"),
                driver("operatingExpenses", target.getOperatingExpenses(), "PKR"), driver("netMargin", margin, "ratio"),
                driver("receivablesOutstanding", target.getReceivablesOutstanding(), "PKR"), driver("dso", dso, "days"),
                driver("netMarginScore", profit.calculateNetMarginScore(target, profile), "points"),
                driver("dsoScore", profit.calculateDsoScore(target), "points"), driver("businessType", businessType, "category"),
                driver("goodMarginThreshold", good, "ratio"), driver("okMarginThreshold", ok, "ratio"),
                driver("missingCogsTreatment", target.getCogs() == null ? "ZERO_IN_EXISTING_MODEL" : "RECORDED", "category")));
        evidence.put("repayment", List.of(driver("paymentBehavior", profile.getPaymentBehavior(), "category")));
        evidence.put("trend", List.of(driver("netCashFlowHistory", series(window, r -> zero(r.getCashInflow()).subtract(zero(r.getCashOutflow()))), "PKR")));
        evidence.put("compliance", List.of(driver("ntnRegistered", profile.getNtnRegistered(), "boolean"),
                driver("businessRegistered", profile.getBusinessRegistered(), "boolean")));
        Map<String, Component> components = new LinkedHashMap<>();
        score.getComponentScores().toMap().forEach((key, value) -> {
            String basis = switch (key) {
                case "cashflow" -> history.size() < 3 ? "BUFFER_ONLY" : buffer == null ? "VARIANCE_ONLY" : "VARIANCE_AND_BUFFER";
                case "profitability" -> "MARGIN_AND_RECEIVABLES";
                case "repayment" -> "DECLARED_PAYMENT_BEHAVIOR";
                case "trend" -> history.size() < 3 ? "INSUFFICIENT_HISTORY" : "NET_CASH_FLOW_TREND";
                default -> "DECLARED_REGISTRATION";
            };
            int used = switch (key) { case "cashflow" -> window.size(); case "trend" -> value == null ? 0 : window.size(); case "profitability" -> 1; default -> 0; };
            components.put(key, new Component(value == null ? "UNAVAILABLE" : "AVAILABLE", value, weights.get(key),
                    value == null ? BigDecimal.ZERO : weights.get(key).divide(score.getDataCompleteness(), 8, RoundingMode.HALF_UP),
                    used, Set.of("repayment", "compliance").contains(key) ? "SELF_DECLARED" : "CALCULATED",
                    basis, evidence.get(key), null, null));
        });
        return withPrevious(new ScoreExplanation(history.size(), null, null, null, components, List.of()), score, previous);
    }

    static ScoreExplanation withPrevious(ScoreExplanation evidence, ScoreResult score, ScoreResult previous) {
        if (evidence == null) return null;
        boolean exact = previous != null && score.getUserId().equals(previous.getUserId())
                && YearMonth.parse(score.getMonth()).minusMonths(1).toString().equals(previous.getMonth());
        Map<String, BigDecimal> prior = exact && previous.getComponentScores() != null ? previous.getComponentScores().toMap() : Map.of();
        Map<String, Component> components = new LinkedHashMap<>();
        List<Change> changes = new ArrayList<>();
        evidence.components().forEach((key, component) -> {
            BigDecimal before = prior.get(key);
            BigDecimal delta = before != null && component.score() != null ? component.score().subtract(before) : null;
            components.put(key, new Component(component.status(), component.score(), component.baseWeight(), component.effectiveWeight(),
                    component.historyMonthsUsed(), component.evidenceType(), component.basis(), component.drivers(), before, delta));
            if (exact && (before == null ? component.score() != null : component.score() == null || before.compareTo(component.score()) != 0))
                changes.add(new Change(key, before, component.score(), delta));
        });
        return new ScoreExplanation(evidence.historyMonthsAvailable(), exact ? previous.getMonth() : null,
                exact ? previous.getCompositeScore() : null, exact ? score.getCompositeScore().subtract(previous.getCompositeScore()) : null,
                components, changes);
    }
    private static Driver driver(String key, Object value, String unit) {
        return new Driver(key, value == null ? null : value instanceof BigDecimal decimal ? decimal.stripTrailingZeros().toPlainString() : value.toString(), unit);
    }
    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) { return numerator.divide(denominator, 8, RoundingMode.HALF_UP); }
    private static String series(List<MonthlyRecord> rows, Function<MonthlyRecord, BigDecimal> value) {
        return rows.stream().sorted(Comparator.comparing(MonthlyRecord::getMonth)).map(r -> r.getMonth() + ": "
                + (value.apply(r) == null ? "unavailable" : value.apply(r).stripTrailingZeros().toPlainString())).collect(Collectors.joining("; "));
    }
}
