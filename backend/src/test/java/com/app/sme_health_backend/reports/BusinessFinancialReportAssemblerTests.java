package com.app.sme_health_backend.reports;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.reports.dto.BusinessFinancialReport;
import com.app.sme_health_backend.reports.dto.ReportRequest;
import com.app.sme_health_backend.reports.service.BusinessFinancialReportAssembler;
import com.app.sme_health_backend.reports.service.ReportValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.app.sme_health_backend.reports.ReportFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class BusinessFinancialReportAssemblerTests {
    static final Instant NOW = Instant.parse("2026-10-25T05:55:50.987Z");

    static BusinessFinancialReport report(String from, String to, List<MonthlyRecord> records, com.app.sme_health_backend.scoring.entity.ScoreResult score) {
        var request = BusinessFinancialReportAssembler.validate(new ReportRequest(from, to, "en"));
        return BusinessFinancialReportAssembler.assemble("Test Traders", "retail", request, records, score, NOW);
    }

    @Test void singleMonthPeriodExposesCanonicalFields() {
        var report = report("2026-03", "2026-03", List.of(full("2026-03", "1000.00", "400.00", "300.00", "900.00", "700.00", "5000.00")), null);
        assertEquals("business-financial-summary-v1", report.schemaVersion());
        assertEquals("PKR", report.currency());
        assertEquals("2026-10-25T05:55:50Z", report.generatedAt());
        assertEquals(1, report.period().monthCount());
        assertEquals(1, report.monthlyRows().size());
        var row = report.monthlyRows().get(0);
        assertEquals("1000.00", row.revenue());
        assertEquals("200.00", row.netCashMovement());
        assertEquals("1000.00", report.periodFlows().revenue().total());
        assertEquals("COMPLETE", report.periodFlows().revenue().status());
        assertTrue(report.completeness().complete());
        assertTrue(report.completeness().missingMonths().isEmpty());
    }

    @Test void multipleMonthsSumFlowsAndAreOrderedByMonthRegardlessOfInputOrder() {
        var records = new ArrayList<>(List.of(
                full("2026-03", "300.00", "30.00", "3.00", "310.00", "100.00", "9.00"),
                full("2026-01", "100.00", "10.00", "1.00", "110.00", "100.00", "7.00"),
                full("2026-02", "200.00", "20.00", "2.00", "210.00", "50.00", "8.00")));
        var report = report("2026-01", "2026-03", records, null);
        assertEquals(List.of("2026-01", "2026-02", "2026-03"), report.monthlyRows().stream().map(r -> r.month()).toList());
        var flows = report.periodFlows();
        assertEquals(3, flows.monthsIncluded());
        assertEquals("600.00", flows.revenue().total());
        assertEquals("60.00", flows.cogs().total());
        assertEquals("6.00", flows.operatingExpenses().total());
        assertEquals("630.00", flows.cashInflow().total());
        assertEquals("250.00", flows.cashOutflow().total());
        assertEquals("380.00", flows.netCashMovement().total());
        assertEquals("15.00", flows.interestExpense().total());
        for (int shuffle = 0; shuffle < 4; shuffle++) {
            Collections.shuffle(records, new java.util.Random(shuffle));
            assertEquals(report, report("2026-01", "2026-03", records, null), "Output must be stable for any source order");
        }
    }

    @Test void balancesAreTheLatestRecordedSnapshotAndNeverSummed() {
        var january = record("2026-01", "1", "1", "1", "1", "1", "1000.00", "10.00", "20.00", "30.00", "40.00", "1");
        var february = record("2026-02", "1", "1", "1", "1", "1", "2000.00", "11.00", "21.00", "31.00", "41.00", "1");
        var report = report("2026-01", "2026-02", List.of(january, february), null);
        var balances = report.latestBalances();
        assertEquals("2026-02", balances.asOfMonth());
        assertEquals("2000.00", balances.cashBalanceEom());
        assertEquals("11.00", balances.receivablesOutstanding());
        assertEquals("21.00", balances.payablesOutstanding());
        assertEquals("31.00", balances.inventoryValue());
        assertEquals("41.00", balances.loanOutstanding());
        assertEquals(BusinessFinancialReportAssembler.BALANCE_BASIS, balances.basis());
        assertEquals(BusinessFinancialReportAssembler.FLOW_BASIS, report.periodFlows().basis());
    }

    @Test void unknownBalancesStayNullAndAreNotBackFilledFromEarlierMonths() {
        var january = record("2026-01", "1", "1", "1", "1", "1", "1000.00", "10.00", "20.00", "30.00", "40.00", "1");
        var february = record("2026-02", "1", "1", "1", "1", "1", "2000.00", null, "21.00", null, "41.00", "1");
        var report = report("2026-01", "2026-02", List.of(january, february), null);
        assertNull(report.latestBalances().receivablesOutstanding());
        assertNull(report.latestBalances().inventoryValue());
        assertEquals("21.00", report.latestBalances().payablesOutstanding());
        assertTrue(report.limitations().contains("BALANCE_NOT_RECORDED_IN_LATEST_MONTH"));
        assertNull(report.monthlyRows().get(1).receivablesOutstanding());
        assertEquals("10.00", report.monthlyRows().get(0).receivablesOutstanding());
    }

    @Test void cogsPartialMissingAndCompleteAreDistinguished() {
        var partial = report("2026-01", "2026-03", List.of(
                full("2026-01", "100", "40", "1", "1", "1", "1"), full("2026-02", "100", null, "1", "1", "1", "1"),
                full("2026-03", "100", "60", "1", "1", "1", "1")), null);
        assertEquals("100.00", partial.periodFlows().cogs().total());
        assertEquals("PARTIAL", partial.periodFlows().cogs().status());
        assertEquals(2, partial.periodFlows().cogs().recordedMonths());
        assertEquals(3, partial.periodFlows().cogs().includedMonths());
        assertTrue(partial.limitations().contains("COGS_PARTIAL"));

        var missing = report("2026-01", "2026-02", List.of(
                full("2026-01", "100", null, "1", "1", "1", "1"), full("2026-02", "100", null, "1", "1", "1", "1")), null);
        assertNull(missing.periodFlows().cogs().total(), "unknown COGS must not become zero");
        assertEquals("NOT_RECORDED", missing.periodFlows().cogs().status());
        assertTrue(missing.limitations().contains("COGS_NOT_RECORDED"));

        var complete = report("2026-01", "2026-01", List.of(full("2026-01", "100", "40", "1", "1", "1", "1")), null);
        assertEquals("COMPLETE", complete.periodFlows().cogs().status());
        assertFalse(complete.limitations().stream().anyMatch(code -> code.startsWith("COGS")));
    }

    @Test void recordedZeroIsDistinctFromUnknown() {
        var zeroCogs = report("2026-01", "2026-01", List.of(
                record("2026-01", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00")), null);
        assertEquals("0.00", zeroCogs.periodFlows().cogs().total());
        assertEquals("COMPLETE", zeroCogs.periodFlows().cogs().status());
        assertEquals("0.00", zeroCogs.latestBalances().receivablesOutstanding());
        assertEquals("0.00", zeroCogs.latestBalances().loanOutstanding());
        var unknown = report("2026-01", "2026-01", List.of(
                record("2026-01", "0.00", null, "0.00", "0.00", "0.00", "0.00", null, null, null, null, null)), null);
        assertNull(unknown.periodFlows().cogs().total());
        assertNull(unknown.latestBalances().receivablesOutstanding());
        assertNull(unknown.periodFlows().interestExpense().total());
    }

    @Test void healthIsTheSuppliedLatestStoredScoreNeverAveragedOrRecalculated() {
        var records = List.of(full("2026-01", "1", "1", "1", "1", "1", "1"), full("2026-02", "1", "1", "1", "1", "1", "1"));
        var report = report("2026-01", "2026-02", records, score("2026-02", "61.50"));
        var health = report.latestHealth();
        assertEquals(BusinessFinancialReportAssembler.HEALTH_BASIS, health.basis());
        assertEquals("2026-02", health.month());
        assertEquals("61.50", health.compositeScore());
        assertEquals("moderate", health.band());
        assertEquals("health-score-v1", health.methodologyVersion());
        assertEquals("0.85", health.dataCompleteness());
        assertEquals(List.of("cashflow", "profitability", "repayment", "trend", "compliance"),
                health.components().stream().map(c -> c.key()).toList());
        assertEquals("70.00", health.components().get(0).score());
        assertNull(health.components().get(2).score(), "an unknown component must stay unknown");
        assertFalse(report.limitations().contains("HEALTH_SCORE_BEFORE_LATEST_RECORD"));
    }

    @Test void healthAbsentOrOlderThanLatestRecordIsDisclosed() {
        var records = List.of(full("2026-01", "1", "1", "1", "1", "1", "1"), full("2026-02", "1", "1", "1", "1", "1", "1"));
        assertNull(report("2026-01", "2026-02", records, null).latestHealth());
        assertTrue(report("2026-01", "2026-02", records, null).limitations().contains("NO_HEALTH_SCORE_IN_PERIOD"));
        assertTrue(report("2026-01", "2026-02", records, score("2026-01", "50.00")).limitations().contains("HEALTH_SCORE_BEFORE_LATEST_RECORD"));
    }

    @Test void periodBoundariesAreInclusiveAndOutsideRecordsAreExcluded() {
        var records = List.of(full("2025-12", "1000", "1", "1", "1", "1", "1"), full("2026-01", "10", "1", "1", "1", "1", "1"),
                full("2026-03", "30", "1", "1", "1", "1", "1"), full("2026-04", "1000", "1", "1", "1", "1", "1"));
        var report = report("2026-01", "2026-03", records, null);
        assertEquals(List.of("2026-01", "2026-03"), report.monthlyRows().stream().map(r -> r.month()).toList());
        assertEquals("40.00", report.periodFlows().revenue().total());
        assertEquals(List.of("2026-02"), report.completeness().missingMonths());
        assertFalse(report.completeness().complete());
        assertEquals(3, report.completeness().requestedMonths());
        assertEquals(2, report.completeness().recordedMonths());
        assertTrue(report.limitations().contains("MONTHS_MISSING"));
        assertEquals("2026-03", report.latestBalances().asOfMonth());
    }

    @Test void yearBoundaryPeriodListsEveryMonth() {
        var report = report("2025-11", "2026-02", List.of(), null);
        assertEquals(4, report.period().monthCount());
        assertEquals(List.of("2025-11", "2025-12", "2026-01", "2026-02"), report.completeness().missingMonths());
    }

    @Test void emptyPeriodKeepsEverythingUnknownInsteadOfZero() {
        var report = report("2026-01", "2026-03", List.of(), null);
        assertTrue(report.monthlyRows().isEmpty());
        assertEquals(0, report.periodFlows().monthsIncluded());
        for (var flow : List.of(report.periodFlows().revenue(), report.periodFlows().cogs(), report.periodFlows().operatingExpenses(),
                report.periodFlows().cashInflow(), report.periodFlows().cashOutflow(), report.periodFlows().netCashMovement())) {
            assertNull(flow.total());
            assertEquals("NOT_RECORDED", flow.status());
        }
        assertNull(report.latestBalances().asOfMonth());
        assertNull(report.latestBalances().cashBalanceEom());
        assertNull(report.latestHealth());
        assertEquals(List.of("NO_RECORDS_IN_PERIOD", "NO_HEALTH_SCORE_IN_PERIOD"), report.limitations());
        assertEquals(3, report.completeness().missingMonths().size());
    }

    @Test void decimalPrecisionIsExactAndNeverScientific() {
        var report = report("2026-01", "2026-03", List.of(
                full("2026-01", "0.10", "0.10", "0.10", "0.10", "0.30", "1"),
                full("2026-02", "0.20", "0.20", "0.20", "0.20", "0.30", "1"),
                full("2026-03", "99999999999.99", "0.00", "0.00", "99999999999.99", "0.01", "1")), null);
        assertEquals("100000000000.29", report.periodFlows().revenue().total());
        assertEquals("100000000000.29", report.periodFlows().cashInflow().total());
        assertEquals("0.61", report.periodFlows().cashOutflow().total());
        assertEquals("99999999999.98", report.monthlyRows().get(2).netCashMovement());
        assertEquals("-0.20", report.monthlyRows().get(0).netCashMovement());
        assertEquals("-0.10", report.monthlyRows().get(1).netCashMovement());
        assertEquals("99999999999.68", report.periodFlows().netCashMovement().total());
        var scaled = report("2026-04", "2026-04", List.of(record("2026-04", "1E+6", "0.3", "0", "0", "0", "0", "0", "0", "0", "0", "0")), null);
        assertEquals("1000000.00", scaled.monthlyRows().get(0).revenue());
        assertEquals("0.30", scaled.monthlyRows().get(0).cogs());
        assertEquals("0.00", scaled.monthlyRows().get(0).operatingExpenses());
    }

    @Test void invalidRangesAndMonthsAreRejectedWithStableCodes() {
        assertCode("range_reversed", "2026-05", "2026-04", "en");
        for (String bad : new String[]{"2026-13", "2026-00", "2026-1", "26-01", "2026/01", " 2026-01", "2026-01 ", "abcd-ef", "2026-01-01", "٢٠٢٦-٠١"}) {
            assertCode("month_invalid", bad, "2026-06", "en");
            assertCode("month_invalid", "2026-01", bad, "en");
        }
        assertCode("required", null, "2026-01", "en");
        assertCode("required", "2026-01", "", "en");
        assertCode("unsupported_language", "2026-01", "2026-02", "fr");
        assertCode("unsupported_language", "2026-01", "2026-02", "EN");
        assertThrows(ReportValidationException.class, () -> BusinessFinancialReportAssembler.validate(null));
    }

    @Test void maximumRangeIsTwentyFourMonthsInclusive() {
        assertEquals(24, BusinessFinancialReportAssembler.validate(new ReportRequest("2024-01", "2025-12", "ur")).monthCount());
        assertCode("range_too_long", "2024-01", "2026-01", "en");
        assertCode("range_too_long", "1999-01", "2026-01", "en");
        assertEquals("en", BusinessFinancialReportAssembler.validate(new ReportRequest("2026-01", "2026-01", null)).language());
    }

    static void assertCode(String expected, String from, String to, String language) {
        var exception = assertThrows(ReportValidationException.class,
                () -> BusinessFinancialReportAssembler.validate(new ReportRequest(from, to, language)));
        assertEquals(expected, exception.errors().values().iterator().next());
    }
}
