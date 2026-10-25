package com.app.sme_health_backend.reports.service;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.reports.dto.BusinessFinancialReport;
import com.app.sme_health_backend.reports.dto.BusinessFinancialReport.*;
import com.app.sme_health_backend.reports.dto.ReportRequest;
import com.app.sme_health_backend.scoring.dto.ComponentScoresDto;
import com.app.sme_health_backend.scoring.entity.ScoreResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Pure, side-effect free assembly of the canonical report from already persisted monthly snapshots and the
 * already persisted score. It creates no new financial methodology:
 * <ul>
 *   <li>flows are plain sums of recorded monthly values (null stays unknown, zero stays zero);</li>
 *   <li>balances are the latest recorded snapshot and are never summed;</li>
 *   <li>health is the stored latest score and is never averaged or recalculated.</li>
 * </ul>
 */
public final class BusinessFinancialReportAssembler {
    public static final int MAX_MONTHS = 24;
    public static final String FLOW_BASIS = "SUM_OF_RECORDED_MONTHS";
    public static final String BALANCE_BASIS = "LATEST_RECORD_IN_PERIOD_NOT_SUMMED";
    public static final String HEALTH_BASIS = "LATEST_SCORE_IN_PERIOD_NOT_AVERAGED";
    private static final Pattern MONTH = Pattern.compile("^\\d{4}-(?:0[1-9]|1[0-2])$");
    private static final List<String> COMPONENT_ORDER =
            List.of("cashflow", "profitability", "repayment", "trend", "compliance");

    private BusinessFinancialReportAssembler() { }

    /** Validated, normalized request. */
    public record ValidatedRequest(YearMonth from, YearMonth to, String language) {
        public int monthCount() {
            return (int) ChronoUnit.MONTHS.between(from, to) + 1;
        }
    }

    public static ValidatedRequest validate(ReportRequest request) {
        if (request == null) throw new ReportValidationException("request", "required");
        YearMonth from = month(request.fromMonth(), "fromMonth");
        YearMonth to = month(request.toMonth(), "toMonth");
        if (from.isAfter(to)) throw new ReportValidationException("fromMonth", "range_reversed");
        if (ChronoUnit.MONTHS.between(from, to) + 1 > MAX_MONTHS) {
            throw new ReportValidationException("toMonth", "range_too_long");
        }
        String language = request.language() == null ? "en" : request.language();
        if (!language.equals("en") && !language.equals("ur")) {
            throw new ReportValidationException("language", "unsupported_language");
        }
        return new ValidatedRequest(from, to, language);
    }

    private static YearMonth month(String value, String field) {
        if (value == null || value.isBlank()) throw new ReportValidationException(field, "required");
        if (!MONTH.matcher(value).matches()) throw new ReportValidationException(field, "month_invalid");
        return YearMonth.parse(value);
    }

    public static BusinessFinancialReport assemble(
            String businessName, String businessType, ValidatedRequest request,
            List<MonthlyRecord> persisted, ScoreResult score, Instant generatedAt) {
        String fromKey = request.from().toString();
        String toKey = request.to().toString();
        List<MonthlyRecord> records = persisted.stream()
                .filter(record -> record.getMonth().compareTo(fromKey) >= 0 && record.getMonth().compareTo(toKey) <= 0)
                .sorted(Comparator.comparing(MonthlyRecord::getMonth))
                .toList();

        List<MonthlyRow> rows = records.stream().map(BusinessFinancialReportAssembler::row).toList();
        PeriodFlows flows = flows(records);
        MonthlyRecord latest = records.isEmpty() ? null : records.get(records.size() - 1);
        LatestBalances balances = new LatestBalances(BALANCE_BASIS,
                latest == null ? null : latest.getMonth(),
                latest == null ? null : amount(latest.getCashBalanceEom()),
                latest == null ? null : amount(latest.getReceivablesOutstanding()),
                latest == null ? null : amount(latest.getPayablesOutstanding()),
                latest == null ? null : amount(latest.getInventoryValue()),
                latest == null ? null : amount(latest.getLoanOutstanding()));
        LatestHealth health = health(score);
        Completeness completeness = completeness(request, records);

        return new BusinessFinancialReport(
                BusinessFinancialReport.SCHEMA_VERSION,
                new BusinessSummary(businessName, businessType),
                new Period(fromKey, toKey, request.monthCount()),
                BusinessFinancialReport.CURRENCY, BusinessFinancialReport.CURRENCY_BASIS,
                request.language(), generatedAt.truncatedTo(ChronoUnit.SECONDS).toString(),
                rows, flows, balances, health, completeness,
                limitations(records, latest, flows, completeness, health));
    }

    private static MonthlyRow row(MonthlyRecord r) {
        return new MonthlyRow(r.getMonth(), amount(r.getRevenue()), amount(r.getCogs()), amount(r.getOperatingExpenses()),
                amount(r.getInterestExpense()), amount(r.getCashInflow()), amount(r.getCashOutflow()), net(r),
                amount(r.getCashBalanceEom()), amount(r.getReceivablesOutstanding()), amount(r.getPayablesOutstanding()),
                amount(r.getInventoryValue()), amount(r.getLoanOutstanding()), r.getFinancingType());
    }

    private static BigDecimal netValue(MonthlyRecord r) {
        return r.getCashInflow() != null && r.getCashOutflow() != null ? r.getCashInflow().subtract(r.getCashOutflow()) : null;
    }

    private static String net(MonthlyRecord r) {
        return amount(netValue(r));
    }

    private static PeriodFlows flows(List<MonthlyRecord> records) {
        return new PeriodFlows(FLOW_BASIS, records.size(),
                flow(records, MonthlyRecord::getRevenue), flow(records, MonthlyRecord::getCogs),
                flow(records, MonthlyRecord::getOperatingExpenses), flow(records, MonthlyRecord::getInterestExpense),
                flow(records, MonthlyRecord::getCashInflow), flow(records, MonthlyRecord::getCashOutflow),
                flow(records, BusinessFinancialReportAssembler::netValue));
    }

    private static Flow flow(List<MonthlyRecord> records, Function<MonthlyRecord, BigDecimal> value) {
        BigDecimal total = null;
        int recorded = 0;
        for (MonthlyRecord record : records) {
            BigDecimal item = value.apply(record);
            if (item == null) continue;
            recorded++;
            total = total == null ? item : total.add(item);
        }
        String status = recorded == 0 ? "NOT_RECORDED" : recorded == records.size() ? "COMPLETE" : "PARTIAL";
        return new Flow(amount(total), recorded, records.size(), status);
    }

    private static LatestHealth health(ScoreResult score) {
        if (score == null) return null;
        ComponentScoresDto components = score.getComponentScores();
        List<ComponentScore> list = new ArrayList<>();
        for (String key : COMPONENT_ORDER) {
            BigDecimal value = components == null ? null : components.toMap().get(key);
            list.add(new ComponentScore(key, value == null ? null : value.toPlainString()));
        }
        return new LatestHealth(HEALTH_BASIS, score.getMonth(),
                score.getCompositeScore() == null ? null : score.getCompositeScore().toPlainString(), score.getBand(),
                List.copyOf(list), score.getWeakestComponent(),
                score.getDataCompleteness() == null ? null : score.getDataCompleteness().toPlainString(),
                score.getMethodologyVersion(),
                score.getComputedAt() == null ? null : score.getComputedAt().toString());
    }

    private static Completeness completeness(ValidatedRequest request, List<MonthlyRecord> records) {
        List<String> missing = new ArrayList<>();
        java.util.Set<String> recorded = new java.util.HashSet<>();
        records.forEach(record -> recorded.add(record.getMonth()));
        for (YearMonth cursor = request.from(); !cursor.isAfter(request.to()); cursor = cursor.plusMonths(1)) {
            if (!recorded.contains(cursor.toString())) missing.add(cursor.toString());
        }
        List<FieldCoverage> optional = List.of(
                coverage("cogs", records, MonthlyRecord::getCogs),
                coverage("interestExpense", records, MonthlyRecord::getInterestExpense),
                coverage("receivablesOutstanding", records, MonthlyRecord::getReceivablesOutstanding),
                coverage("payablesOutstanding", records, MonthlyRecord::getPayablesOutstanding),
                coverage("inventoryValue", records, MonthlyRecord::getInventoryValue),
                coverage("loanOutstanding", records, MonthlyRecord::getLoanOutstanding));
        return new Completeness(request.monthCount(), records.size(), List.copyOf(missing), missing.isEmpty(), optional);
    }

    private static FieldCoverage coverage(String field, List<MonthlyRecord> records, Function<MonthlyRecord, BigDecimal> value) {
        return new FieldCoverage(field, (int) records.stream().filter(record -> value.apply(record) != null).count(), records.size());
    }

    private static List<String> limitations(List<MonthlyRecord> records, MonthlyRecord latest, PeriodFlows flows,
                                            Completeness completeness, LatestHealth health) {
        List<String> codes = new ArrayList<>();
        if (records.isEmpty()) codes.add("NO_RECORDS_IN_PERIOD");
        else if (!completeness.complete()) codes.add("MONTHS_MISSING");
        if (!records.isEmpty()) {
            if (flows.cogs().status().equals("PARTIAL")) codes.add("COGS_PARTIAL");
            if (flows.cogs().status().equals("NOT_RECORDED")) codes.add("COGS_NOT_RECORDED");
            if (flows.interestExpense().status().equals("PARTIAL")) codes.add("INTEREST_EXPENSE_PARTIAL");
            if (flows.interestExpense().status().equals("NOT_RECORDED")) codes.add("INTEREST_EXPENSE_NOT_RECORDED");
            if (latest.getReceivablesOutstanding() == null || latest.getPayablesOutstanding() == null
                    || latest.getInventoryValue() == null || latest.getLoanOutstanding() == null) {
                codes.add("BALANCE_NOT_RECORDED_IN_LATEST_MONTH");
            }
        }
        if (health == null) codes.add("NO_HEALTH_SCORE_IN_PERIOD");
        else if (latest != null && health.month().compareTo(latest.getMonth()) < 0) {
            codes.add("HEALTH_SCORE_BEFORE_LATEST_RECORD");
        }
        return List.copyOf(codes);
    }

    /** Exact decimal string; at least two fraction digits, never scientific notation, null preserved. */
    static String amount(BigDecimal value) {
        if (value == null) return null;
        return (value.scale() < 2 ? value.setScale(2) : value).toPlainString();
    }
}
