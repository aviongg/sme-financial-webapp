package com.app.sme_health_backend.intelligence;

import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.testsupport.ProductApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end financial chain on real PostgreSQL through the HTTP API: bill -> AP -> stock capitalization ->
 * invoice -> AR -> fulfilment (COGS) -> credit note -> collections/settlements -> cash sale/expense/cash stock
 * purchase -> classification -> accrual P&amp;L, break-even, budget variance, goals, F05 project profitability and F09
 * sample actuals. Proves cash timing (collections/settlements) never double counts revenue or cost and capitalized
 * purchases appear only through inventory consumption.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class FinancialChainPostgreSqlIT extends ProductApiTestBase {

    private static void money(String expected, JsonNode node, String field) {
        assertNotNull(node.get(field), field + " missing in " + node);
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(node.get(field).asText())),
                field + " expected " + expected + " but was " + node.get(field).asText());
    }

    @Test
    void accrualChainHasNoDoubleCountingAndDrivesPlanningAndProjects() throws Exception {
        Actor owner = owner();
        UUID account = account(owner, "100000.00");
        UUID customer = counterparty(owner, "Customer A", "CUSTOMER");
        UUID supplier = counterparty(owner, "Supplier B", "SUPPLIER");
        UUID item = item(owner, "CHAIN-1");
        UUID projectId = project(owner, "PRJ-CHAIN");

        // Supplier bill: 10 units stock (500) + freight (100); recognize, receive stock, settle in full.
        UUID bill = finalizedDocument(owner, "bills", TODAY, List.<Object[]>of(new Object[] {"Stock", "10", "50.00", item},
                new Object[] {"Freight", "1", "100.00", null}));
        UUID billOb = recognize(owner, bill, "BILL", supplier);
        postJson(owner, "/api/inventory/documents/bills/" + bill + "/receive", map(), key(), 201);
        settle(owner, billOb, account, "600.00");

        // Customer invoice: 4 units @ 200, linked to the project, fulfilled from stock for the project.
        UUID invoice = finalizedDocument(owner, "invoices", TODAY, List.<Object[]>of(new Object[] {"Goods", "4", "200.00", item}));
        UUID invOb = recognize(owner, invoice, "INVOICE", customer);
        postJson(owner, "/api/finance/projects/" + projectId + "/commercial-document-links",
                map("documentId", invoice, "allocatedAmount", "800.00", "notes", "order"), null, 201);
        postJson(owner, "/api/inventory/documents/invoices/" + invoice + "/fulfil", map("projectId", projectId), key(), 201);
        postJson(owner, "/api/commercial/documents/" + invoice + "/adjustments", map("amount", "50.00", "reason", "Discount"), key(), 201);
        settle(owner, invOb, account, "750.00");

        UUID cashSale = postTransaction(owner, account, "DIRECT_SALE_RECEIPT", "300.00", TODAY);
        UUID rent = postTransaction(owner, account, "OPERATING_EXPENSE_PAYMENT", "1000.00", TODAY);
        UUID stockPayment = postTransaction(owner, account, "OPERATING_EXPENSE_PAYMENT", "250.00", TODAY);
        postJson(owner, "/api/inventory/movements/receipts", map("itemId", item, "quantity", "5", "value", "250.00",
                "transactionId", stockPayment), key(), 201);
        postJson(owner, "/api/inventory/movements/issues", map("itemId", item, "quantity", "1", "purpose", "PROJECT",
                "projectId", projectId), key(), 201);

        String month = "from=" + TODAY.withDayOfMonth(1) + "&to=" + TODAY;
        JsonNode pnl = getJson(owner, "/api/planning/profit-and-loss?" + month);
        money("300.00", pnl, "cashSales");
        money("800.00", pnl, "invoiceRevenue");
        money("50.00", pnl, "creditNotes");
        money("1050.00", pnl, "revenue");
        money("200.00", pnl, "costOfGoodsSold");
        money("50.00", pnl, "projectMaterial");
        money("250.00", pnl, "costOfSales");
        money("800.00", pnl, "grossProfit");
        money("1000.00", pnl, "cashOperatingExpenses");   // rent only; the 250 stock payment is capitalized
        money("100.00", pnl, "billExpenses");             // freight only; 500 of the bill is capitalized
        money("1100.00", pnl, "operatingExpenses");
        money("-300.00", pnl, "operatingProfit");
        money("750.00", pnl, "capitalizedPurchases");
        assertEquals("PARTIAL_UNCLASSIFIED", pnl.get("completeness").asText());
        JsonNode register = getJson(owner, "/api/inventory/register");
        money("500.00", register, "totalValue");          // 500 - 200 + 250 - 50

        // Break-even refuses to guess cost behaviour.
        JsonNode beBefore = getJson(owner, "/api/planning/break-even?" + month);
        assertEquals("UNAVAILABLE", beBefore.get("status").asText());
        assertEquals("UNCLASSIFIED_COST_BEHAVIOR", beBefore.get("reason").asText());
        assertEquals(2, beBefore.get("missingCount").asInt());

        Actor viewer = actor(owner.businessId(), MembershipRole.VIEWER);
        putJson(viewer, "/api/planning/classifications", map("sourceKind", "TRANSACTION", "sourceId", rent,
                "category", "RENT", "costBehavior", "FIXED"), 403);
        putJson(owner, "/api/planning/classifications", map("sourceKind", "TRANSACTION", "sourceId", rent,
                "category", "RENT", "costBehavior", "FIXED"), 200);
        putJson(owner, "/api/planning/category-behaviors", map("category", "TRANSPORT_LOGISTICS", "costBehavior", "VARIABLE"), 200);
        putJson(owner, "/api/planning/classifications", map("sourceKind", "COMMERCIAL_DOCUMENT", "sourceId", bill,
                "category", "TRANSPORT_LOGISTICS"), 200);
        putJson(owner, "/api/planning/classifications", map("sourceKind", "TRANSACTION", "sourceId", cashSale,
                "category", "PRODUCT_SALES"), 200);
        putJson(owner, "/api/planning/classifications", map("sourceKind", "COMMERCIAL_DOCUMENT", "sourceId", invoice,
                "category", "PRODUCT_SALES"), 200);
        putJson(owner, "/api/planning/classifications", map("sourceKind", "TRANSACTION", "sourceId", cashSale,
                "category", "PRODUCT_SALES", "costBehavior", "FIXED"), 400);

        JsonNode be = getJson(owner, "/api/planning/break-even?" + month);
        assertEquals("AVAILABLE", be.get("status").asText(), be.toString());
        money("350.00", be, "variableCosts");
        money("1000.00", be, "fixedCosts");
        money("700.00", be, "contribution");
        money("1500.00", be, "breakEvenRevenue");
        money("-450.00", be, "marginOfSafety");
        assertEquals("AVAILABLE", be.get("unitEconomics").get("status").asText(), be.toString());
        assertEquals("6", be.get("unitEconomics").get("breakEvenUnits").asText());
        assertEquals("COMPLETE", getJson(owner, "/api/planning/profit-and-loss?" + month).get("completeness").asText());

        // Budget with revisions and variance.
        String ym = YearMonth.from(TODAY).toString();
        JsonNode budget = postJson(owner, "/api/planning/budgets", map("name", "Monthly plan", "periodStart", ym, "periodEnd", ym,
                "lines", List.of(map("lineKind", "REVENUE", "category", "PRODUCT_SALES", "amount", "2000.00"),
                        map("lineKind", "EXPENSE", "category", "RENT", "amount", "800.00"),
                        map("lineKind", "EXPENSE", "category", "COST_OF_SALES", "amount", "300.00"))), null, 201);
        String budgetId = budget.get("id").asText();
        postJson(viewer, "/api/planning/budgets/" + budgetId + "/approve", map(), null, 403);
        JsonNode approved = postJson(owner, "/api/planning/budgets/" + budgetId + "/approve", map(), null, 200);
        assertEquals("ACTIVE", approved.get("status").asText());
        JsonNode lines = approved.get("variance").get("lines");
        assertEquals("BELOW_PLAN", line(lines, "PRODUCT_SALES").get("status").asText());
        money("1050.00", line(lines, "PRODUCT_SALES"), "actual");
        assertEquals("OVER_BUDGET", line(lines, "RENT").get("status").asText());
        assertEquals("WITHIN_BUDGET", line(lines, "COST_OF_SALES").get("status").asText());
        money("250.00", line(lines, "COST_OF_SALES"), "actual");
        putJson(owner, "/api/planning/budgets/" + budgetId + "/draft", map("lines",
                List.of(map("lineKind", "EXPENSE", "category", "RENT", "amount", "1000.00"))), 200);
        JsonNode rev2 = postJson(owner, "/api/planning/budgets/" + budgetId + "/approve", map(), null, 200);
        assertEquals(2, rev2.get("basisRevisionNo").asInt());
        assertEquals("SUPERSEDED", rev2.get("revisions").get(1).get("status").asText());
        assertThrows(Exception.class, () -> jdbc.update("UPDATE business_budget_lines SET amount = 1 WHERE business_id = ?",
                owner.businessId()));
        postJson(owner, "/api/planning/budgets", map("name", "Bad", "periodStart", ym, "periodEnd", ym,
                "lines", List.of(map("lineKind", "REVENUE", "category", "RENT", "amount", "1.00"))), null, 400);

        // Goals derive progress from authoritative data.
        JsonNode goal = postJson(owner, "/api/planning/goals", map("name", "Revenue 1k", "metric", "REVENUE", "targetValue", "1000.00",
                "periodStart", TODAY.withDayOfMonth(1).toString(), "periodEnd", TODAY.withDayOfMonth(TODAY.lengthOfMonth()).toString()), null, 201);
        assertEquals("ACHIEVED", goal.get("progressStatus").asText());
        money("1050.00", goal, "actualValue");
        JsonNode ceiling = postJson(owner, "/api/planning/goals", map("name", "AR ceiling", "metric", "RECEIVABLES_CEILING",
                "targetValue", "10.00", "periodStart", TODAY.toString(), "periodEnd", TODAY.plusDays(10).toString()), null, 201);
        assertEquals("ON_TRACK", ceiling.get("progressStatus").asText(), "AR fully collected and credited -> 0 outstanding");

        // F05: project profitability from recognized revenue and inventory material.
        JsonNode prof = getJson(owner, "/api/projects/" + projectId + "/profitability");
        money("750.00", prof.get("revenue"), "total");
        money("250.00", prof.get("costs"), "material");
        money("500.00", prof.get("profitability"), "profit");
        assertEquals("AVAILABLE", prof.get("profitability").get("status").asText(), prof.toString());
        assertEquals("NO_APPROVED_BUDGET", prof.get("plan").get("status").asText());
        assertTrue(prof.get("plan").get("plannedRevenue").isNull(), "Missing budget is unknown, never zero");
        JsonNode costing = getJson(owner, "/api/finance/projects/" + projectId + "/costing-summary");
        assertTrue(costing.get("plannedCost").isNull(), "C05 costing no longer reports a missing budget as zero");
    }

    @Test
    void sampleActualsUseInventoryAndLedgerWithoutDoubleAttribution() throws Exception {
        Actor owner = owner();
        UUID account = account(owner, "50000.00");
        UUID customer = counterparty(owner, "Sample buyer", "CUSTOMER");
        UUID item = item(owner, "SMP-1");
        postJson(owner, "/api/inventory/movements/receipts", map("itemId", item, "quantity", "10", "value", "500.00"), key(), 201);
        JsonNode sample = postJson(owner, "/api/samples", map("code", "SMP-001", "name", "Swatch", "kind", "SAMPLE",
                "recoveryIntent", "PAID"), null, 201);
        String sampleId = sample.get("id").asText();
        postJson(owner, "/api/inventory/movements/issues", map("itemId", item, "quantity", "1", "purpose", "SAMPLE",
                "sampleId", sampleId), key(), 201);
        UUID payment = postTransaction(owner, account, "OPERATING_EXPENSE_PAYMENT", "120.00", TODAY);
        postJson(owner, "/api/samples/" + sampleId + "/actuals/costs", map("transactionId", payment, "amount", "100.00",
                "costCategory", "LABOUR"), key(), 201);
        JsonNode over = postJson(owner, "/api/samples/" + sampleId + "/actuals/costs", map("transactionId", payment,
                "amount", "30.00", "costCategory", "OTHER"), key(), 409);
        assertEquals("attribution_exceeds_payment", over.get("error").asText());
        // The remaining 20 cannot be project-attributed beyond the sample claim either.
        UUID projectId = project(owner, "PRJ-SMP");
        postJson(owner, "/api/finance/projects/" + projectId + "/attributions", map("sourceType", "TRANSACTION", "sourceId", payment,
                "amount", "21.00", "costCategory", "OTHER"), key(), 400);

        UUID invoice = finalizedDocument(owner, "invoices", TODAY, List.<Object[]>of(new Object[] {"Sample fee", "1", "200.00", null}));
        postJson(owner, "/api/samples/" + sampleId + "/actuals/recoveries", map("documentId", invoice, "amount", "150.00"), null, 409);
        recognize(owner, invoice, "INVOICE", customer);
        JsonNode linked = postJson(owner, "/api/samples/" + sampleId + "/actuals/recoveries", map("documentId", invoice,
                "amount", "150.00"), null, 201);
        money("50.00", linked.get("cost"), "inventoryMaterial");
        money("100.00", linked.get("cost"), "directCosts");
        money("150.00", linked.get("cost"), "total");
        money("150.00", linked.get("recovery"), "recognized");
        money("0.00", linked, "contribution");
        assertEquals("FULLY_RECOVERED", linked.get("recovery").get("status").asText());

        JsonNode free = postJson(owner, "/api/samples", map("code", "SMP-002", "name", "Free swatch", "kind", "SAMPLE",
                "recoveryIntent", "FREE"), null, 201);
        assertEquals("free_sample_no_recovery", postJson(owner, "/api/samples/" + free.get("id").asText() + "/actuals/recoveries",
                map("documentId", invoice, "amount", "10.00"), null, 409).get("error").asText());

        String costId = linked.get("cost").get("entries").get(0).get("id").asText();
        JsonNode reversed = postJson(owner, "/api/samples/" + sampleId + "/actuals/costs/" + costId + "/reverse",
                map("reason", "Wrong sample"), null, 200);
        money("50.00", reversed.get("cost"), "total");
        assertThrows(Exception.class, () -> jdbc.update("DELETE FROM sample_actual_costs WHERE business_id = ?", owner.businessId()));
        Actor other = owner();
        getJson(other, "/api/samples/" + sampleId + "/actuals", 404);
    }

    private static JsonNode line(JsonNode lines, String category) {
        for (JsonNode l : lines) if (l.get("category").asText().equals(category)) return l;
        throw new AssertionError("no line " + category);
    }
}
