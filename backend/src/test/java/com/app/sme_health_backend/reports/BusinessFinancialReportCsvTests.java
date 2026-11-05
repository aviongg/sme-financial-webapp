package com.app.sme_health_backend.reports;

import com.app.sme_health_backend.reports.dto.BusinessFinancialReport;
import com.app.sme_health_backend.reports.dto.ReportRequest;
import com.app.sme_health_backend.reports.service.BusinessFinancialReportAssembler;
import com.app.sme_health_backend.reports.service.BusinessFinancialReportCsv;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.app.sme_health_backend.reports.ReportFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class BusinessFinancialReportCsvTests {
    static BusinessFinancialReport report(String businessName, String language) {
        var request = BusinessFinancialReportAssembler.validate(new ReportRequest("2026-01", "2026-03", language));
        var records = List.of(
                record("2026-01", "100.50", null, "10.00", "120.00", "300.00", "-50.00", "5.00", null, "0.00", "7.00", "1.25"),
                record("2026-03", "200.00", "20.00", "30.00", "250.00", "100.00", "400.00", "6.00", "8.00", "9.00", null, null));
        return BusinessFinancialReportAssembler.assemble(businessName, "retail", request, records, score("2026-03", "61.50"),
                Instant.parse("2026-10-25T05:55:50Z"));
    }

    /** Minimal RFC 4180 reader used only to verify the produced bytes. */
    static List<List<String>> parse(byte[] bytes) {
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);
        String text = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else if (c == '"') quoted = false;
                else cell.append(c);
            } else if (c == '"') quoted = true;
            else if (c == ',') { row.add(cell.toString()); cell.setLength(0); }
            else if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                row.add(cell.toString()); cell.setLength(0); rows.add(row); row = new ArrayList<>(); i++;
            } else cell.append(c);
        }
        assertFalse(quoted, "unterminated quoted cell");
        assertTrue(row.isEmpty() && cell.length() == 0, "file must end with a record terminator");
        return rows;
    }

    static String value(List<List<String>> rows, String section, String period, String key) {
        var matches = rows.stream().filter(r -> r.get(0).equals(section) && r.get(1).equals(period) && r.get(2).equals(key)).toList();
        assertEquals(1, matches.size(), section + "/" + period + "/" + key);
        return matches.get(0).get(4);
    }

    @Test void csvCarriesExactlyTheCanonicalFiguresAndEveryRowHasSevenCells() {
        var report = report("Test Traders", "en");
        var rows = parse(BusinessFinancialReportCsv.render(report));
        assertEquals(List.of("section", "period", "key", "label", "value", "status", "detail"), rows.get(0));
        rows.forEach(row -> assertEquals(7, row.size(), row.toString()));
        for (var monthly : report.monthlyRows()) {
            assertEquals(monthly.revenue(), value(rows, "monthly", monthly.month(), "revenue"));
            assertEquals(monthly.netCashMovement(), value(rows, "monthly", monthly.month(), "netCashMovement"));
            assertEquals(monthly.cashBalanceEom(), value(rows, "monthly", monthly.month(), "cashBalanceEom"));
        }
        assertEquals(report.periodFlows().revenue().total(), value(rows, "periodFlow", "2026-01..2026-03", "revenue"));
        assertEquals(report.periodFlows().cashOutflow().total(), value(rows, "periodFlow", "2026-01..2026-03", "cashOutflow"));
        assertEquals(report.periodFlows().netCashMovement().total(), value(rows, "periodFlow", "2026-01..2026-03", "netCashMovement"));
        assertEquals(report.periodFlows().cogs().total(), value(rows, "periodFlow", "2026-01..2026-03", "cogs"));
        assertEquals(report.latestBalances().cashBalanceEom(), value(rows, "latestBalance", "2026-03", "cashBalanceEom"));
        assertEquals("61.50", value(rows, "latestHealth", "2026-03", "compositeScore"));
        assertEquals("2026-02", value(rows, "completeness", "2026-02", "missingMonth"));
        assertEquals(report.monthlyRows().size() * 13L, rows.stream().filter(r -> r.get(0).equals("monthly")).count());
    }

    @Test void unknownIsAnEmptyCellWithExplicitStatusAndZeroIsWrittenAsZero() {
        var rows = parse(BusinessFinancialReportCsv.render(report("Test Traders", "en")));
        var january = rows.stream().filter(r -> r.get(0).equals("monthly") && r.get(1).equals("2026-01") && r.get(2).equals("cogs")).findFirst().orElseThrow();
        assertEquals("", january.get(4));
        assertEquals("NOT_RECORDED", january.get(5));
        var inventory = rows.stream().filter(r -> r.get(0).equals("monthly") && r.get(1).equals("2026-01") && r.get(2).equals("inventoryValue")).findFirst().orElseThrow();
        assertEquals("0.00", inventory.get(4));
        assertEquals("RECORDED", inventory.get(5));
        var cogsFlow = rows.stream().filter(r -> r.get(0).equals("periodFlow") && r.get(2).equals("cogs")).findFirst().orElseThrow();
        assertEquals("20.00", cogsFlow.get(4));
        assertEquals("PARTIAL", cogsFlow.get(5));
        assertTrue(cogsFlow.get(6).endsWith("(1/2)"));
    }

    @Test void legitimateNegativeAmountsRemainNumbersButHostileTextIsNeutralized() {
        var rows = parse(BusinessFinancialReportCsv.render(report("Test Traders", "en")));
        assertEquals("-50.00", value(rows, "monthly", "2026-01", "cashBalanceEom"));
        assertEquals("-180.00", value(rows, "monthly", "2026-01", "netCashMovement"));
        for (String hostile : new String[]{"=HYPERLINK(\"http://x\")", "+1+1", "-2+3", "@SUM(A1)", "\t=1", "\r=1", "=cmd|' /C calc'!A0"}) {
            var hostileRows = parse(BusinessFinancialReportCsv.render(report(hostile, "en")));
            String name = value(hostileRows, "report", "", "businessName");
            assertEquals("'" + hostile, name, "must be prefixed with an apostrophe: " + hostile.replace("\t", "\\t").replace("\r", "\\r"));
            assertTrue(name.startsWith("'"));
        }
        assertEquals("Plain Name", value(parse(BusinessFinancialReportCsv.render(report("Plain Name", "en"))), "report", "", "businessName"));
        assertEquals("A, \"quoted\"\nname", value(parse(BusinessFinancialReportCsv.render(report("A, \"quoted\"\nname", "en"))), "report", "", "businessName"));
    }

    @Test void cellNeutralizationHelperCoversEveryDangerousLeadingCharacter() {
        for (String start : new String[]{"=", "+", "-", "@", "\t", "\r"}) {
            assertTrue(BusinessFinancialReportCsv.cell(start + "x", false).replace("\"", "").startsWith("'" + start), start);
        }
        assertEquals("-12.50", BusinessFinancialReportCsv.cell("-12.50", true));
        assertEquals("'-12.50", BusinessFinancialReportCsv.cell("-12.50", false));
        assertEquals("'-1+2", BusinessFinancialReportCsv.cell("-1+2", true), "non-decimal text is never trusted as numeric");
        assertEquals("", BusinessFinancialReportCsv.cell(null, false));
    }

    @Test void urduLabelsAreUtf8AndEnglishKeysStayMachineReadable() {
        var bytes = BusinessFinancialReportCsv.render(report("نمونہ تاجر", "ur"));
        var rows = parse(bytes);
        assertEquals("نمونہ تاجر", value(rows, "report", "", "businessName"));
        var revenue = rows.stream().filter(r -> r.get(0).equals("periodFlow") && r.get(2).equals("revenue")).findFirst().orElseThrow();
        assertEquals("آمدنی", revenue.get(3));
        assertTrue(revenue.get(6).startsWith("ریکارڈ شدہ مہینوں کا مجموعہ"));
        assertEquals("300.50", revenue.get(4));
        var balance = rows.stream().filter(r -> r.get(0).equals("latestBalance") && r.get(2).equals("cashBalanceEom")).findFirst().orElseThrow();
        assertTrue(balance.get(6).contains("مدت کا کل نہیں"));
        assertTrue(rows.stream().anyMatch(r -> r.get(0).equals("limitation") && r.get(3).contains("مال کی لاگت")));
        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("آمدنی"));
        assertEquals("ur", value(rows, "report", "", "language"));
    }

    @Test void outputIsDeterministicAndCarriesNoInternalIdentifiersOrSecrets() {
        var report = report("Test Traders", "en");
        assertArrayEquals(BusinessFinancialReportCsv.render(report), BusinessFinancialReportCsv.render(report));
        String text = new String(BusinessFinancialReportCsv.render(report), StandardCharsets.UTF_8);
        assertFalse(java.util.regex.Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matcher(text).find());
        assertFalse(text.toLowerCase().contains("userid"));
        assertFalse(text.toLowerCase().contains("businessid"));
        assertTrue(text.endsWith("\r\n"));
    }

    @Test void fileNameIsSafeAscii() {
        assertEquals("finsight-business-financial-summary_2026-01_2026-03.csv", BusinessFinancialReportCsv.fileName(report("=evil\"\r\n", "en")));
        assertTrue(BusinessFinancialReportCsv.fileName(report("نمونہ", "ur")).matches("[A-Za-z0-9._-]+"));
    }

    @Test void emptyPeriodCsvIsStillWellFormedWithExplicitUnknownStatuses() {
        var request = BusinessFinancialReportAssembler.validate(new ReportRequest("2026-01", "2026-01", "en"));
        var empty = BusinessFinancialReportAssembler.assemble("Test Traders", null, request, List.of(), null, Instant.parse("2026-10-25T05:55:50Z"));
        var rows = parse(BusinessFinancialReportCsv.render(empty));
        rows.forEach(row -> assertEquals(7, row.size()));
        var revenue = rows.stream().filter(r -> r.get(0).equals("periodFlow") && r.get(2).equals("revenue")).findFirst().orElseThrow();
        assertEquals("", revenue.get(4));
        assertEquals("NOT_RECORDED", revenue.get(5));
        assertTrue(rows.stream().anyMatch(r -> r.get(0).equals("limitation") && r.get(2).equals("NO_RECORDS_IN_PERIOD")));
        assertEquals("NOT_RECORDED", rows.stream().filter(r -> r.get(0).equals("latestHealth")).findFirst().orElseThrow().get(5));
    }
}
