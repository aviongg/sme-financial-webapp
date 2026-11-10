package com.app.sme_health_backend.reports;

import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.testsupport.ProductApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** F12B calendar/relationships/report pack and the intelligence overview/action center on real PostgreSQL. */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class OperationsIntelligencePostgreSqlIT extends ProductApiTestBase {

    private static List<String> values(JsonNode array, String field) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : array) out.add(n.get(field).asText());
        return out;
    }

    @Test
    void calendarRelationshipsPackAndActionCenterUseAuthoritativeSources() throws Exception {
        Actor owner = owner();
        UUID account = account(owner, "20000.00");
        UUID customer = counterparty(owner, "Late Payer", "CUSTOMER");
        UUID item = item(owner, "OPS-1");
        postJson(owner, "/api/inventory/movements/receipts", map("itemId", item, "quantity", "3", "value", "30.00"), key(), 201);
        UUID overdueInvoice = finalizedDocument(owner, "invoices", TODAY.minusDays(40), List.<Object[]>of(new Object[] {"Old work", "1", "900.00", item}));
        UUID obligation = recognize(owner, overdueInvoice, "INVOICE", customer);
        postJson(owner, "/api/inventory/documents/invoices/" + overdueInvoice + "/fulfil", map(), key(), 201);
        UUID pending = finalizedDocument(owner, "invoices", TODAY, List.<Object[]>of(new Object[] {"New work", "1", "400.00", null}));
        UUID projectId = project(owner, "PRJ-OPS");
        postTransaction(owner, account, "DIRECT_SALE_RECEIPT", "100.00", TODAY);

        JsonNode calendar = getJson(owner, "/api/operations/calendar?from=" + TODAY.minusDays(30) + "&to=" + TODAY.plusDays(90));
        List<String> types = values(calendar.get("events"), "type");
        assertTrue(types.contains("RECEIVABLE_DUE"), types.toString());
        assertTrue(types.contains("INVOICE_DUE_UNRECOGNIZED"), types.toString());
        assertTrue(types.contains("PROJECT_TARGET_END"), types.toString());

        JsonNode rel = getJson(owner, "/api/operations/relationships/DOCUMENT/" + overdueInvoice);
        List<String> kinds = values(rel.get("related"), "kind");
        assertTrue(kinds.contains("OBLIGATION") && kinds.contains("INVENTORY_MOVEMENT"), kinds.toString());
        JsonNode itemRel = getJson(owner, "/api/operations/relationships/ITEM/" + item);
        assertTrue(values(itemRel.get("related"), "kind").contains("DOCUMENT"));
        getJson(owner, "/api/operations/relationships/UNKNOWN/" + item, 400);
        getJson(owner(), "/api/operations/relationships/DOCUMENT/" + overdueInvoice, 404);

        String period = "from=" + TODAY.minusDays(45) + "&to=" + TODAY;
        JsonNode pack = getJson(owner, "/api/reports/financial-pack?" + period);
        assertEquals("f12b-pack-v1", pack.get("version").asText());
        assertEquals("1000.00", pack.get("profit_and_loss").get("revenue").asText());
        assertEquals("10.00", pack.get("inventory").get("costOfGoodsSold").asText());
        assertEquals("900.00", pack.get("aging").get("receivables").get("overdueOutstanding").asText());
        assertEquals("AVAILABLE", pack.get("cash").get("status").asText());
        assertEquals(1, pack.get("projects").size());

        var csv = mvc.perform(get("/api/reports/financial-pack.csv?" + period + "&section=profit_and_loss").cookie(owner.session()))
                .andReturn().getResponse();
        assertEquals(200, csv.getStatus());
        String text = csv.getContentAsString();
        assertTrue(text.contains("\"revenue\",\"1000.00\""), text);
        assertTrue(csv.getHeader("Content-Disposition").contains("attachment"));
        assertEquals(400, mvc.perform(get("/api/reports/financial-pack.csv?" + period + "&section=secrets").cookie(owner.session()))
                .andReturn().getResponse().getStatus());

        JsonNode overview = getJson(owner, "/api/intelligence/overview?" + period);
        assertEquals("1000.00", overview.get("revenue").get("recognizedRevenue").asText());
        assertEquals("900.00", overview.get("revenue").get("topCustomers").get(0).get("amount").asText());
        assertEquals("90.00", overview.get("revenue").get("topCustomers").get(0).get("sharePct").asText());
        assertEquals(6, overview.get("revenue").get("trend").size());
        assertEquals("NO_SCORE", overview.get("health").get("status").asText());

        JsonNode actions = getJson(owner, "/api/intelligence/actions");
        List<String> actionTypes = values(actions.get("actions"), "type");
        assertTrue(actionTypes.contains("OVERDUE_RECEIVABLES"), actionTypes.toString());
        assertTrue(actionTypes.contains("UNRECOGNIZED_DOCUMENT"), actionTypes.toString());
        assertTrue(actionTypes.contains("INVENTORY_REORDER"), actionTypes.toString());
        for (JsonNode a : actions.get("actions")) {
            assertFalse(a.get("reason").asText().isBlank(), "Every action explains itself");
            assertTrue(a.has("evidence"));
        }
        assertNotNull(obligation);
        assertNotNull(pending);
        assertNotNull(projectId);

        Actor salesperson = actor(owner.businessId(), MembershipRole.SALESPERSON);
        getJson(salesperson, "/api/intelligence/overview", 403);
        getJson(salesperson, "/api/reports/financial-pack", 403);
        getJson(salesperson, "/api/operations/calendar", 403);
        Actor viewer = actor(owner.businessId(), MembershipRole.VIEWER);
        getJson(viewer, "/api/intelligence/actions");
    }
}
