package com.app.sme_health_backend.reports.service;

import com.app.sme_health_backend.reports.dto.BusinessFinancialReport;
import com.app.sme_health_backend.reports.dto.BusinessFinancialReport.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Renders the canonical report as a tidy, deterministic, UTF-8 CSV (with BOM so spreadsheet tools detect Urdu).
 * It reads only the {@link BusinessFinancialReport}; it never recomputes a figure.
 *
 * <p>Columns: section, period, key, label, value, status, detail. Unknown values are an empty cell with status
 * NOT_RECORDED — a recorded zero is written as 0.00.
 */
public final class BusinessFinancialReportCsv {
    public static final String CONTENT_TYPE = "text/csv; charset=UTF-8";
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String EOL = "\r\n";
    private static final Pattern DECIMAL = Pattern.compile("^-?\\d+(?:\\.\\d+)?$");
    static final List<String> HEADER = List.of("section", "period", "key", "label", "value", "status", "detail");

    private BusinessFinancialReportCsv() { }

    public static String fileName(BusinessFinancialReport report) {
        // Only validated YYYY-MM values are ever used, so the name is always safe ASCII.
        return "finsight-business-financial-summary_" + report.period().fromMonth() + "_" + report.period().toMonth() + ".csv";
    }

    public static byte[] render(BusinessFinancialReport report) {
        String language = report.language();
        List<List<String>> rows = new ArrayList<>();
        List<boolean[]> numeric = new ArrayList<>();
        Builder out = new Builder(rows, numeric, language);

        out.text("report", "", "schemaVersion", report.schemaVersion());
        out.text("report", "", "businessName", report.business().name());
        out.text("report", "", "businessType", report.business().businessType());
        out.text("report", "", "fromMonth", report.period().fromMonth());
        out.text("report", "", "toMonth", report.period().toMonth());
        out.textWithStatus("report", "", "currency", report.currency(), report.currencyBasis());
        out.text("report", "", "generatedAt", report.generatedAt());
        out.text("report", "", "language", report.language());

        for (MonthlyRow row : report.monthlyRows()) {
            out.amount("monthly", row.month(), "revenue", row.revenue(), "");
            out.amount("monthly", row.month(), "cogs", row.cogs(), "");
            out.amount("monthly", row.month(), "operatingExpenses", row.operatingExpenses(), "");
            out.amount("monthly", row.month(), "interestExpense", row.interestExpense(), "");
            out.amount("monthly", row.month(), "cashInflow", row.cashInflow(), "");
            out.amount("monthly", row.month(), "cashOutflow", row.cashOutflow(), "");
            out.amount("monthly", row.month(), "netCashMovement", row.netCashMovement(), "");
            out.amount("monthly", row.month(), "cashBalanceEom", row.cashBalanceEom(), "");
            out.amount("monthly", row.month(), "receivablesOutstanding", row.receivablesOutstanding(), "");
            out.amount("monthly", row.month(), "payablesOutstanding", row.payablesOutstanding(), "");
            out.amount("monthly", row.month(), "inventoryValue", row.inventoryValue(), "");
            out.amount("monthly", row.month(), "loanOutstanding", row.loanOutstanding(), "");
            out.text("monthly", row.month(), "financingType", row.financingType());
        }

        String periodKey = report.period().fromMonth() + ".." + report.period().toMonth();
        PeriodFlows flows = report.periodFlows();
        String flowBasis = ReportCsvLabels.label(language, "basis.flow");
        out.flow(periodKey, "revenue", flows.revenue(), flowBasis);
        out.flow(periodKey, "cogs", flows.cogs(), flowBasis);
        out.flow(periodKey, "operatingExpenses", flows.operatingExpenses(), flowBasis);
        out.flow(periodKey, "interestExpense", flows.interestExpense(), flowBasis);
        out.flow(periodKey, "cashInflow", flows.cashInflow(), flowBasis);
        out.flow(periodKey, "cashOutflow", flows.cashOutflow(), flowBasis);
        out.flow(periodKey, "netCashMovement", flows.netCashMovement(), flowBasis);

        LatestBalances balances = report.latestBalances();
        String balanceBasis = ReportCsvLabels.label(language, "basis.balance");
        String asOf = balances.asOfMonth() == null ? "" : balances.asOfMonth();
        out.amount("latestBalance", asOf, "cashBalanceEom", balances.cashBalanceEom(), balanceBasis);
        out.amount("latestBalance", asOf, "receivablesOutstanding", balances.receivablesOutstanding(), balanceBasis);
        out.amount("latestBalance", asOf, "payablesOutstanding", balances.payablesOutstanding(), balanceBasis);
        out.amount("latestBalance", asOf, "inventoryValue", balances.inventoryValue(), balanceBasis);
        out.amount("latestBalance", asOf, "loanOutstanding", balances.loanOutstanding(), balanceBasis);

        LatestHealth health = report.latestHealth();
        String healthBasis = ReportCsvLabels.label(language, "basis.health");
        if (health == null) {
            out.amount("latestHealth", "", "latestHealth", null, healthBasis);
        } else {
            out.amount("latestHealth", health.month(), "compositeScore", health.compositeScore(), healthBasis);
            out.textWithStatus("latestHealth", health.month(), "band", health.band(), "RECORDED");
            for (ComponentScore component : health.components()) {
                out.amountLabelled("latestHealth", health.month(), "component." + component.key(), component.score(), healthBasis);
            }
            out.textWithStatus("latestHealth", health.month(), "weakestComponent", health.weakestComponent(), "RECORDED");
            out.amount("latestHealth", health.month(), "dataCompleteness", health.dataCompleteness(), healthBasis);
            out.text("latestHealth", health.month(), "methodologyVersion", health.methodologyVersion());
            out.text("latestHealth", health.month(), "computedAt", health.computedAt());
        }

        Completeness completeness = report.completeness();
        out.text("completeness", "", "requestedMonths", String.valueOf(completeness.requestedMonths()));
        out.text("completeness", "", "recordedMonths", String.valueOf(completeness.recordedMonths()));
        out.text("completeness", "", "complete", String.valueOf(completeness.complete()));
        for (String month : completeness.missingMonths()) {
            out.textWithStatus("completeness", month, "missingMonth", month, "MISSING");
        }
        for (FieldCoverage field : completeness.optionalFields()) {
            out.coverage(field);
        }
        for (String limitation : report.limitations()) {
            out.limitation(limitation);
        }

        StringBuilder text = new StringBuilder();
        text.append(String.join(",", HEADER)).append(EOL);
        for (int i = 0; i < rows.size(); i++) {
            List<String> cells = rows.get(i);
            boolean[] decimal = numeric.get(i);
            List<String> encoded = new ArrayList<>();
            for (int c = 0; c < cells.size(); c++) {
                encoded.add(cell(cells.get(c), decimal[c]));
            }
            text.append(String.join(",", encoded)).append(EOL);
        }
        byte[] body = text.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, result, 0, BOM.length);
        System.arraycopy(body, 0, result, BOM.length, body.length);
        return result;
    }

    /**
     * Spreadsheet formula injection defence: any text cell starting with = + - @ TAB or CR is prefixed with an
     * apostrophe. Only strict decimal strings produced by the backend (value column) are written as numbers, so
     * legitimate negative amounts stay numeric.
     */
    public static String cell(String value, boolean decimalValue) {
        if (value == null) return "";
        String safe = value;
        if (!(decimalValue && DECIMAL.matcher(value).matches()) && !value.isEmpty()) {
            char first = value.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
                safe = "'" + value;
            }
        }
        boolean quote = safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0
                || safe.indexOf('\r') >= 0 || safe.indexOf('\t') >= 0;
        return quote ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }

    private static final class Builder {
        private final List<List<String>> rows;
        private final List<boolean[]> numeric;
        private final String language;

        Builder(List<List<String>> rows, List<boolean[]> numeric, String language) {
            this.rows = rows;
            this.numeric = numeric;
            this.language = language;
        }

        private void add(String section, String period, String key, String label, String value, String status, String detail,
                         boolean decimalValue) {
            rows.add(List.of(section, period, key, label, value == null ? "" : value, status, detail));
            numeric.add(new boolean[]{false, false, false, false, decimalValue, false, false});
        }

        void text(String section, String period, String key, String value) {
            add(section, period, key, ReportCsvLabels.label(language, key), value, value == null ? "NOT_RECORDED" : "RECORDED", "", false);
        }

        void textWithStatus(String section, String period, String key, String value, String status) {
            add(section, period, key, ReportCsvLabels.label(language, key), value, value == null ? "NOT_RECORDED" : status, "", false);
        }

        void amount(String section, String period, String key, String value, String detail) {
            add(section, period, key, ReportCsvLabels.label(language, key), value, value == null ? "NOT_RECORDED" : "RECORDED", detail, true);
        }

        void amountLabelled(String section, String period, String key, String value, String detail) {
            amount(section, period, key, value, detail);
        }

        void flow(String period, String key, Flow flow, String basis) {
            add("periodFlow", period, key, ReportCsvLabels.label(language, key), flow.total(), flow.status(),
                    basis + " (" + flow.recordedMonths() + "/" + flow.includedMonths() + ")", true);
        }

        void coverage(FieldCoverage field) {
            String status = field.includedMonths() == 0 || field.recordedMonths() == 0 ? "NOT_RECORDED"
                    : field.recordedMonths() == field.includedMonths() ? "COMPLETE" : "PARTIAL";
            add("completeness", "", "coverage." + field.field(), ReportCsvLabels.label(language, field.field()),
                    null, status, field.recordedMonths() + "/" + field.includedMonths(), false);
        }

        void limitation(String code) {
            add("limitation", "", code, ReportCsvLabels.label(language, code), null, "NOTE", "", false);
        }
    }
}
