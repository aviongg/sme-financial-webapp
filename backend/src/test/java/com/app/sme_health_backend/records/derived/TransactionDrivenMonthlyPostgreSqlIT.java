package com.app.sme_health_backend.records.derived;

import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.testsupport.ProductApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * monthly-projection-v1 on real PostgreSQL through the HTTP API. Daily activity (invoices, bills, cash sales,
 * expenses, collections, settlements, stock receipts/issues, reversals, late entries) is the only input; the monthly
 * record, the health score, the F12B report, the dashboard and the P&amp;L must all agree for the same month.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class TransactionDrivenMonthlyPostgreSqlIT extends ProductApiTestBase {

    private static final YearMonth M0 = YearMonth.from(TODAY);
    private static final YearMonth M1 = M0.minusMonths(1);
    private static final YearMonth M2 = M0.minusMonths(2);

    @Test
    void monthlyRecordsAreDerivedFromDailyActivityAndStayConsistent() throws Exception {
        Actor owner = owner();
        UUID businessId = owner.businessId();
        JsonNode accountNode = postJson(owner, "/api/finance/accounts", map("accountName", "Main bank", "accountType", "BANK",
                "currency", "PKR", "openingBalance", "10000.00", "openingDate", M2.atDay(1).toString()), key(), 201);
        UUID account = UUID.fromString(accountNode.get("id").asText());
        UUID customer = counterparty(owner, "Customer M", "CUSTOMER");
        UUID supplier = counterparty(owner, "Supplier M", "SUPPLIER");

        // ------------------------------------------------ (1)(2)(4)(5) multiple daily invoices/bills; draft ignored
        UUID inv1 = finalizedDocument(owner, "invoices", M1.atDay(5), List.<Object[]>of(new Object[] {"Job 1", "1", "1000.00"}));
        UUID inv1Ob = recognize(owner, inv1, "INVOICE", customer);
        UUID inv2 = finalizedDocument(owner, "invoices", M1.atDay(20), List.<Object[]>of(new Object[] {"Job 2", "1", "500.00"}));
        recognize(owner, inv2, "INVOICE", customer);
        UUID bill1 = finalizedDocument(owner, "bills", M1.atDay(3), List.<Object[]>of(new Object[] {"Rent", "1", "300.00"}));
        UUID bill1Ob = recognize(owner, bill1, "BILL", supplier);
        UUID bill2 = finalizedDocument(owner, "bills", M1.atDay(25), List.<Object[]>of(new Object[] {"Utilities", "1", "200.00"}));
        recognize(owner, bill2, "BILL", supplier);
        postTransaction(owner, account, "DIRECT_SALE_RECEIPT", "400.00", M1.atDay(10));
        postTransaction(owner, account, "OPERATING_EXPENSE_PAYMENT", "100.00", M1.atDay(12));
        postJson(owner, "/api/invoices", map("counterpartyName", "Draft only", "documentDate", M1.atDay(15).toString(),
                "dueDate", M1.atDay(28).toString(), "notes", null, "lines",
                List.of(map("description", "Draft", "quantity", "1", "unit", "pcs", "unitPrice", "9999.00"))), null, 201);

        // (17) Manual monthly figures are refused; supplemental financing is the only manual monthly input.
        postJson(owner, "/api/records/monthly", map("month", M1.toString(), "cashInflow", 1, "cashOutflow", 1, "revenue", 1,
                "operatingExpenses", 1, "cashBalanceEom", 1, "financingType", "none"), null, 409);
        postJson(owner, "/api/finance/ledger-bridge/apply-projection", map("yearMonth", M1.toString(), "manualCogs", "5.00"),
                null, 409);

        JsonNode first = postJson(owner, "/api/records/monthly/recalculate", map(), null, 200);
        // Any read of a monthly path already refreshed the dirty projection; the explicit rebuild reports coverage.
        assertEquals(M2.toString(), first.get("coverageFrom").asText(), first.toString());
        assertEquals(M0.toString(), first.get("coverageThrough").asText(), first.toString());

        Map<String, JsonNode> months = records(owner);
        assertEquals(3, months.size(), "coverage starts at the account opening month: " + months.keySet());
        JsonNode m1 = months.get(M1.toString());
        assertEquals("DERIVED", m1.get("source").asText());
        assertTrue(m1.get("periodComplete").asBoolean());
        money("1900.00", m1, "revenue");               // 1000 + 500 invoices + 400 cash sale; draft 9999 ignored
        money("600.00", m1, "operatingExpenses");      // 300 + 200 bills + 100 cash expense
        money("400.00", m1, "cashInflow");
        money("100.00", m1, "cashOutflow");
        money("10300.00", m1, "cashBalanceEom");
        money("1500.00", m1, "receivablesOutstanding");
        money("500.00", m1, "payablesOutstanding");
        // (15) null stays null: no inventory ledger yet and no loan input.
        assertTrue(m1.get("cogs").isNull(), "COGS unknown without inventory ledger");
        assertTrue(m1.get("inventoryValue").isNull(), "inventory unknown without inventory ledger");
        assertTrue(m1.get("loanOutstanding").isNull());
        JsonNode m2 = months.get(M2.toString());
        money("0.00", m2, "revenue");
        money("10000.00", m2, "cashBalanceEom");
        money("0.00", m2, "receivablesOutstanding");
        assertFalse(months.get(M0.toString()).get("periodComplete").asBoolean());
        Map<String, Object> m1ScoreBefore = jdbc.queryForMap(
                "SELECT composite_score, computed_at FROM score_results WHERE user_id = ? AND month = ?", businessId, M1.toString());

        // (14) Rebuilding with no source change writes nothing.
        long versionBefore = m1.get("version").asLong();
        JsonNode again = postJson(owner, "/api/records/monthly/recalculate", map(), null, 200);
        assertEquals(0, again.get("changedMonths").size(), again.toString());
        assertEquals(versionBefore, records(owner).get(M1.toString()).get("version").asLong());

        // ------------------------------------------------ (6)(7) collection and supplier payment are cash only
        settle(owner, inv1Ob, account, "1000.00");
        settle(owner, bill1Ob, account, "300.00");
        // ------------------------------------------------ (8)(9)(10) stock purchase capitalizes; issues create COGS once
        UUID item = item(owner, "MONTHLY-1");
        UUID projectId = project(owner, "PRJ-MONTHLY");
        UUID bill3 = finalizedDocument(owner, "bills", TODAY, List.<Object[]>of(new Object[] {"Stock", "10", "50.00", item}));
        recognize(owner, bill3, "BILL", supplier);
        postJson(owner, "/api/inventory/documents/bills/" + bill3 + "/receive", map(), key(), 201);
        UUID inv3 = finalizedDocument(owner, "invoices", TODAY, List.<Object[]>of(new Object[] {"Goods", "4", "200.00", item}));
        recognize(owner, inv3, "INVOICE", customer);
        postJson(owner, "/api/inventory/documents/invoices/" + inv3 + "/fulfil", map(), key(), 201);
        postJson(owner, "/api/inventory/movements/issues", map("itemId", item, "quantity", "1", "purpose", "PROJECT",
                "projectId", projectId), key(), 201);

        months = records(owner);   // GET refreshes automatically (dirty projection)
        JsonNode m0 = months.get(M0.toString());
        money("800.00", m0, "revenue");                // collection of 1000 is NOT revenue again
        money("250.00", m0, "cogs");                   // 4 x 50 sold + 1 x 50 project material, once
        money("0.00", m0, "operatingExpenses");        // 300 supplier payment is NOT cost again; stock bill capitalized
        money("1000.00", m0, "cashInflow");
        money("300.00", m0, "cashOutflow");
        money("250.00", m0, "inventoryValue");         // 500 received - 200 sold - 50 project
        money("1300.00", m0, "receivablesOutstanding"); // 500 inv2 + 800 inv3
        money("700.00", m0, "payablesOutstanding");    // 200 bill2 + 500 bill3
        money("11000.00", m0, "cashBalanceEom");
        m1 = months.get(M1.toString());
        money("1900.00", m1, "revenue");
        money("1500.00", m1, "receivablesOutstanding"); // month-end snapshot is not rewritten by a later collection
        assertTrue(m1.get("inventoryValue").isNull());

        // ------------------------------------------------ (3)(16) late entry with a prior accounting date
        UUID inv4 = finalizedDocument(owner, "invoices", M1.atDay(28), List.<Object[]>of(new Object[] {"Late job", "1", "700.00"}));
        recognize(owner, inv4, "INVOICE", customer);
        JsonNode late = postJson(owner, "/api/records/monthly/recalculate", map(), null, 200);
        assertTrue(late.get("changedMonths").toString().contains(M1.toString()), late.toString());
        assertTrue(late.get("rescoredMonths").toString().contains(M1.toString()), late.toString());
        assertFalse(late.get("changedMonths").toString().contains(M2.toString()), late.toString());
        months = records(owner);
        money("2600.00", months.get(M1.toString()), "revenue");
        money("2200.00", months.get(M1.toString()), "receivablesOutstanding");
        money("800.00", months.get(M0.toString()), "revenue");
        Map<String, Object> m1ScoreAfter = jdbc.queryForMap(
                "SELECT composite_score, computed_at FROM score_results WHERE user_id = ? AND month = ?", businessId, M1.toString());
        assertNotEquals(0, ((BigDecimal) m1ScoreBefore.get("composite_score"))
                .compareTo((BigDecimal) m1ScoreAfter.get("composite_score")), "prior-month score must be recomputed");
        assertTrue(((Timestamp) m1ScoreAfter.get("computed_at")).after((Timestamp) m1ScoreBefore.get("computed_at")));

        // ------------------------------------------------ (13) reversal removes the effect from its original month
        UUID mistake = postTransaction(owner, account, "OPERATING_EXPENSE_PAYMENT", "250.00", M1.atDay(15));
        months = records(owner);
        money("850.00", months.get(M1.toString()), "operatingExpenses");
        money("350.00", months.get(M1.toString()), "cashOutflow");
        reverseTransaction(owner, mistake);
        months = records(owner);
        money("600.00", months.get(M1.toString()), "operatingExpenses");
        money("100.00", months.get(M1.toString()), "cashOutflow");
        money("10300.00", months.get(M1.toString()), "cashBalanceEom");
        money("300.00", months.get(M0.toString()), "cashOutflow");

        // ------------------------------------------------ supplemental financing (manual, labelled) flows in
        putJson(owner, "/api/records/monthly/financing/" + M1, map("loanOutstanding", "5000.00",
                "financingType", "conventional"), 200);
        months = records(owner);
        money("5000.00", months.get(M1.toString()), "loanOutstanding");
        assertEquals("conventional", months.get(M0.toString()).get("financingType").asText());
        assertTrue(months.get(M0.toString()).get("loanOutstanding").isNull(), "a balance is never carried forward");
        JsonNode sources = getJson(owner, "/api/records/monthly/" + M1 + "/sources");
        assertTrue(sources.get("storedMatchesDerivation").asBoolean(), sources.toString());
        assertEquals("SUPPLEMENTAL_MANUAL", field(sources, "loanOutstanding").get("basis").asText());
        assertEquals("ACCRUAL", field(sources, "revenue").get("basis").asText());
        assertEquals("CASH", field(sources, "cashInflow").get("basis").asText());
        assertEquals("SNAPSHOT", field(sources, "receivablesOutstanding").get("basis").asText());

        // ------------------------------------------------ (18) report / P&L / dashboard / score reconciliation
        JsonNode pnl = getJson(owner, "/api/planning/profit-and-loss?from=" + M1.atDay(1) + "&to=" + M1.atEndOfMonth());
        money("2600.00", pnl, "revenue");
        money("600.00", pnl, "operatingExpenses");
        JsonNode report = postJson(owner, "/api/reports/business-financial-summary",
                map("fromMonth", M1.toString(), "toMonth", M1.toString(), "language", "en"), null, 200);
        JsonNode row = report.get("monthlyRows").get(0);
        assertEquals(M1.toString(), row.get("month").asText());
        assertEquals(0, new BigDecimal("2600.00").compareTo(new BigDecimal(row.get("revenue").asText())), row.toString());
        assertEquals(0, new BigDecimal("600.00").compareTo(new BigDecimal(row.get("operatingExpenses").asText())));
        money("2600.00", field(sources, "revenue"), "value");
        JsonNode dashboard = getJson(owner, "/api/dashboard");
        assertEquals(M0.toString(), dashboard.get("score").get("month").asText());
        boolean m1Charted = false;
        for (JsonNode point : dashboard.get("cashFlowHistory")) {
            if (M1.toString().equals(point.get("month").asText())) {
                assertEquals(0, new BigDecimal("400.00").compareTo(new BigDecimal(point.get("inflow").asText())));
                m1Charted = true;
            }
        }
        assertTrue(m1Charted, dashboard.get("cashFlowHistory").toString());
        JsonNode score = postJson(owner, "/api/scores/query", map("month", M1.toString()), null, 200);
        assertEquals(0, ((BigDecimal) jdbc.queryForObject("SELECT composite_score FROM score_results WHERE user_id = ? AND month = ?",
                BigDecimal.class, businessId, M1.toString())).compareTo(new BigDecimal(score.get("compositeScore").asText())));

        // ------------------------------------------------ (17) cross-business isolation
        Actor other = owner();
        postJson(other, "/api/finance/accounts", map("accountName", "Other", "accountType", "BANK", "currency", "PKR",
                "openingBalance", "50.00", "openingDate", M1.atDay(1).toString()), key(), 201);
        Map<String, JsonNode> otherMonths = records(other);
        assertEquals(2, otherMonths.size(), otherMonths.keySet().toString());
        money("0.00", otherMonths.get(M1.toString()), "revenue");
        money("50.00", otherMonths.get(M1.toString()), "cashBalanceEom");
        getJson(other, "/api/records/monthly/" + M1 + "/sources");
        long otherSeq = jdbc.queryForObject("SELECT dirty_seq FROM monthly_projection_state WHERE business_id = ?",
                Long.class, other.businessId());
        postTransaction(owner, account, "DIRECT_SALE_RECEIPT", "1.00", TODAY);
        assertEquals(otherSeq, jdbc.queryForObject("SELECT dirty_seq FROM monthly_projection_state WHERE business_id = ?",
                Long.class, other.businessId()), "another tenant's activity never invalidates this tenant");
        Actor viewer = actor(businessId, MembershipRole.VIEWER);
        postJson(viewer, "/api/records/monthly/recalculate", map(), null, 403);
        putJson(viewer, "/api/records/monthly/financing/" + M1, map("loanOutstanding", "1.00"), 403);
        getJson(viewer, "/api/records/monthly/projection-status");
    }

    private Map<String, JsonNode> records(Actor actor) throws Exception {
        Map<String, JsonNode> out = new HashMap<>();
        for (JsonNode node : getJson(actor, "/api/records/monthly")) {
            out.put(node.get("month").asText(), node);
        }
        return out;
    }

    private static JsonNode field(JsonNode sources, String name) {
        for (JsonNode f : sources.get("fields")) {
            if (name.equals(f.get("field").asText())) return f;
        }
        throw new AssertionError("missing field " + name + " in " + sources);
    }

    private static void money(String expected, JsonNode node, String field) {
        assertNotNull(node.get(field), field + " missing in " + node);
        assertFalse(node.get(field).isNull(), field + " is null in " + node);
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(node.get(field).asText())),
                field + " expected " + expected + " but was " + node.get(field).asText());
    }
}
