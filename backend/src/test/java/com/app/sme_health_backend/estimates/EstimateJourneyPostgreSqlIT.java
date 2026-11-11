package com.app.sme_health_backend.estimates;

import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.testsupport.ProductApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** F06 journeys on real PostgreSQL: estimate -> new project (atomic), estimate -> quotation draft, feedback. */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class EstimateJourneyPostgreSqlIT extends ProductApiTestBase {

    private String[] approvedEstimate(Actor owner, String code, List<Map<String, Object>> lines, String qty, String price) throws Exception {
        JsonNode estimate = postJson(owner, "/api/estimates", map("code", code, "name", "Estimate " + code), null, 201).get("estimate");
        String id = estimate.get("id").asText();
        JsonNode revision = postJson(owner, "/api/estimates/" + id + "/versions", map("lines", lines, "saleableQuantity", qty,
                "pricingMode", "PROPOSED_PRICE", "pricingValue", price, "expectedVersion", estimate.get("version").asLong()), null, 201);
        String rid = revision.get("id").asText();
        postJson(owner, "/api/estimates/" + id + "/versions/" + rid + "/approve",
                map("expectedVersion", revision.get("version").asLong()), null, 200);
        return new String[] {id, rid};
    }

    @Test
    void approvedEstimateBecomesNewProjectAndQuotationAndFeedsActuals() throws Exception {
        Actor owner = owner();
        String[] e = approvedEstimate(owner, "EST-J1", List.of(map("costGroup", "MATERIAL", "description", "Board",
                "quantity", "2", "unit", "pcs", "unitCost", "100")), "4", "500");
        JsonNode converted = postJson(owner, "/api/estimates/" + e[0] + "/versions/" + e[1] + "/convert-to-new-project",
                map("code", "PRJ-FROM-EST", "name", "From estimate", "kind", "ORDER"), null, 201);
        UUID projectId = UUID.fromString(converted.get("projectId").asText());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM estimate_project_conversions WHERE project_id = ?", Integer.class, projectId));

        JsonNode quote = postJson(owner, "/api/estimates/" + e[0] + "/versions/" + e[1] + "/quotation-invoice",
                map("customerName", "Buyer"), null, 201);
        assertEquals("DRAFT", quote.get("status").asText());
        assertEquals("500.00", quote.get("total").asText());
        JsonNode invoice = getJson(owner, "/api/invoices/" + quote.get("documentId").asText());
        assertEquals("4", invoice.get("lines").get(0).get("quantity").asText().replaceAll("\\.0+$", ""));
        assertEquals("NONE", invoice.get("financialEffect").asText(), "A quotation draft never posts");

        JsonNode feedback = getJson(owner, "/api/estimates/" + e[0] + "/feedback");
        assertEquals(1, feedback.get("projects").size());
        assertEquals("CONVERTED_ESTIMATE", feedback.get("projects").get(0).get("estimate").get("status").asText());
        assertEquals(1, feedback.get("quotations").size());

        Actor viewer = actor(owner.businessId(), MembershipRole.VIEWER);
        postJson(viewer, "/api/estimates/" + e[0] + "/versions/" + e[1] + "/quotation-invoice", map(), null, 403);
        getJson(viewer, "/api/estimates/" + e[0] + "/feedback");
        getJson(owner(), "/api/estimates/" + e[0] + "/feedback", 404);
    }

    @Test
    void failedConversionLeavesNoOrphanProjectAndUnapprovedRevisionIsRefused() throws Exception {
        Actor owner = owner();
        String[] e = approvedEstimate(owner, "EST-J2", List.of(map("costGroup", "MATERIAL", "description", "Resin",
                "quantity", "1.0000001", "unit", "kg", "unitCost", "1000")), "7", "1500.25");
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/estimates/" + e[0] + "/versions/" + e[1] + "/convert-to-new-project").cookie(owner.session())
                .with(csrf(owner.session())).contentType("application/json")
                .content(json.writeValueAsString(map("code", "PRJ-ORPHAN", "name", "Should roll back")))).andReturn().getResponse();
        assertTrue(response.getStatus() == 201 || (response.getStatus() >= 400 && response.getStatus() < 500),
                "A non-representable estimate is a typed client error, never a 500: " + response.getStatus());
        if (response.getStatus() >= 400) {
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM projects WHERE business_id = ? AND code = 'PRJ-ORPHAN'",
                    Integer.class, owner.businessId()), "Atomic: a failed conversion leaves no project");
        } else {
            assertEquals(201, response.getStatus());
        }
        JsonNode estimate = postJson(owner, "/api/estimates", map("code", "EST-J3", "name", "Draft only"), null, 201).get("estimate");
        JsonNode draft = postJson(owner, "/api/estimates/" + estimate.get("id").asText() + "/versions", map("lines",
                List.of(map("costGroup", "MATERIAL", "description", "x", "quantity", "1", "unit", "u", "unitCost", "1")),
                "saleableQuantity", "1", "pricingMode", "PROPOSED_PRICE", "pricingValue", "2",
                "expectedVersion", estimate.get("version").asLong()), null, 201);
        assertEquals("revision_not_approved", postJson(owner, "/api/estimates/" + estimate.get("id").asText() + "/versions/"
                + draft.get("id").asText() + "/quotation-invoice", map(), null, 409).get("error").asText());
    }
}
