package com.app.sme_health_backend.security;

import com.app.sme_health_backend.documents.ocr.OcrClient;
import com.app.sme_health_backend.documents.ocr.OcrExtraction;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP, CSRF, JDBC sessions, migrations and services. Only the external OCR result is a fixture. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MvpCoreJourneyIT.LocalOcrProvider.class)
class MvpCoreJourneyIT extends DisposablePostgres {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private static final String PASSWORD = "mvp-http-fixture-password-123";

    @TestConfiguration
    static class LocalOcrProvider {
        @Bean OcrClient localOcr() {
            return request -> new OcrExtraction(LocalDate.of(2026, 9, 19), new BigDecimal("1250.50"),
                    "Fixture supplier", OcrExtraction.Category.sales, OcrExtraction.Confidence.medium,
                    OcrExtraction.DocumentType.invoice);
        }
    }

    private HttpClient browser() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> send(HttpClient client, String method, String path, Object body) throws Exception {
        JsonNode csrf = json.readTree(get(client, "/api/auth/csrf").body());
        return client.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode result(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), "Unexpected status at " + response.uri().getPath());
        return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
    }
    private String register(HttpClient client) throws Exception {
        String email = "mvp-" + UUID.randomUUID() + "@example.test";
        result(send(client, "POST", "/api/auth/register", Map.of("email", email, "password", PASSWORD, "fullName", "MVP Fixture")), 201);
        result(send(client, "POST", "/api/auth/login", Map.of("email", email, "password", PASSWORD)), 200);
        return email;
    }
    private String business(HttpClient client, String name) throws Exception {
        JsonNode created = result(send(client, "POST", "/api/businesses", Map.of("businessName", name,
                "businessType", "retail", "languagePreference", "en", "whatsappOptIn", false,
                "paymentBehavior", "2weeks", "ntnRegistered", true, "businessRegistered", true)), 201);
        assertEquals(name, created.get("businessName").asText());
        return created.get("businessId").asText();
    }
    private Map<String, Object> record(String month) {
        return Map.of("month", month, "cashInflow", 500000, "cashOutflow", 200000, "revenue", 500000,
                "cogs", 150000, "operatingExpenses", 50000, "cashBalanceEom", 300000, "financingType", "none");
    }
    private JsonNode search(HttpClient client, String query, String type) throws Exception {
        return result(send(client, "POST", "/api/search", Map.of("query", query, "type", type)), 200);
    }
    private JsonNode byId(JsonNode rows, String id) {
        for (JsonNode row : rows) if (id.equals(row.path("id").asText())) return row;
        fail("Expected scoped row missing"); return null;
    }
    private String upload(HttpClient client) throws Exception {
        var image = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", image);
        String boundary = "mvp-" + UUID.randomUUID();
        var bytes = new ByteArrayOutputStream();
        bytes.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"mvp-receipt.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bytes.write(image.toByteArray());
        bytes.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        JsonNode csrf = json.readTree(get(client, "/api/auth/csrf").body());
        var response = client.send(HttpRequest.newBuilder(uri("/api/documents/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
        String id = result(response, 201).get("id").asText();
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode document = result(get(client, "/api/documents/" + id), 200);
            if ("needs_review".equals(document.get("processingStatus").asText())) return id;
            Thread.sleep(50);
        }
        fail("Asynchronous OCR fixture did not finish"); return null;
    }

    @Test
    void realFinancialEvidenceActionsSearchAndDocumentProvenanceRemainTenantScoped() throws Exception {
        assertEquals("finsight_app", jdbc.queryForObject("SELECT current_user", String.class));
        HttpClient owner = browser();
        register(owner);
        String first = business(owner, "Cedar Supplies");
        result(send(owner, "POST", "/api/records/monthly", record("2026-09")), 201);
        result(send(owner, "POST", "/api/records/monthly", record("2026-07")), 201);
        JsonNode gap = result(get(owner, "/api/scores/latest"), 200);
        assertEquals("health-score-v1", gap.get("methodologyVersion").asText());
        assertTrue(gap.at("/explanation/previousScore").isNull(), "A missing August is not replaced by July");
        assertEquals(2, result(get(owner, "/api/scores/history"), 200).size());
        result(send(owner, "POST", "/api/records/monthly", record("2026-08")), 201);
        JsonNode score = result(get(owner, "/api/scores/latest"), 200);
        assertEquals("2026-08", score.at("/explanation/previousMonth").asText());
        assertEquals(3, score.at("/explanation/historyMonthsAvailable").asInt());
        assertEquals("SELF_DECLARED", score.at("/explanation/components/repayment/evidenceType").asText());
        assertEquals("AVAILABLE", score.at("/explanation/components/trend/status").asText());
        assertEquals(3, result(get(owner, "/api/scores/history"), 200).size());
        assertTrue(result(get(owner, "/api/insights"), 200).size() > 0);
        JsonNode actions = result(get(owner, "/api/recommendations"), 200);
        String actionId = actions.get(0).get("id").asText();
        assertEquals("DONE", result(send(owner, "PATCH", "/api/recommendations/" + actionId + "/status", Map.of("status", "DONE")), 200).get("status").asText());
        result(send(owner, "PATCH", "/api/profile/language", Map.of("languagePreference", "ur")), 200);
        JsonNode urdu = byId(result(get(owner, "/api/recommendations"), 200), actionId);
        assertEquals("DONE", urdu.get("status").asText());
        assertNotEquals(actions.get(0).get("text").asText(), urdu.get("text").asText());
        result(send(owner, "PATCH", "/api/profile/language", Map.of("languagePreference", "en")), 200);
        assertEquals("DONE", byId(result(get(owner, "/api/recommendations"), 200), actionId).get("status").asText());
        assertEquals("Cedar Trading", result(send(owner, "PATCH", "/api/businesses/active/name", Map.of("businessName", "Cedar Trading", "businessType", "services")), 200).get("businessName").asText());
        assertEquals("retail", result(get(owner, "/api/businesses/active"), 200).get("businessType").asText());
        assertEquals(score, result(get(owner, "/api/dashboard"), 200).get("score"), "Renaming/language must not rescore");
        assertEquals("/records/2026-09", search(owner, "2026-09", "transaction").get(0).get("href").asText());
        assertEquals("/health/components?month=2026-09", search(owner, "2026-09", "score").get(0).get("href").asText());

        String documentId = upload(owner);
        JsonNode original = result(get(owner, "/api/documents/" + documentId), 200);
        JsonNode reviewed = result(send(owner, "PATCH", "/api/documents/" + documentId, Map.of("amount", 1500)), 200);
        assertEquals(original.get("extractedData"), reviewed.get("extractedData"));
        assertEquals("ORIGINAL_OCR", reviewed.get("extractionProvenance").asText());
        assertEquals("medium", json.readTree(reviewed.get("reviewedData").asText()).get("confidence").asText());
        assertEquals(1500, json.readTree(reviewed.get("reviewedData").asText()).get("amount").asInt());
        assertEquals("2026-09-19", json.readTree(reviewed.get("reviewedData").asText()).get("date").asText());
        assertEquals("Fixture supplier", json.readTree(reviewed.get("reviewedData").asText()).get("vendor_or_party").asText());
        assertEquals(1, result(get(owner, "/api/documents/" + documentId + "/corrections"), 200).size());
        assertEquals(new BigDecimal("500000.00"), jdbc.queryForObject("SELECT revenue FROM monthly_records WHERE user_id=? AND month='2026-09'", BigDecimal.class, UUID.fromString(first)));
        assertEquals(documentId, search(owner, "mvp-receipt", "document").get(0).get("id").asText());
        Map<String, Object> confirmation = Map.of("targetMonth", "2026-09", "confirmedAmount", 1500,
                "targetClassification", "revenue", "cashFlowImpact", "cash_inflow");
        result(send(owner, "POST", "/api/documents/" + documentId + "/confirm", confirmation), 200);
        result(send(owner, "POST", "/api/documents/" + documentId + "/confirm", confirmation), 409);
        assertEquals(new BigDecimal("501500.00"), jdbc.queryForObject("SELECT revenue FROM monthly_records WHERE user_id=? AND month='2026-09'", BigDecimal.class, UUID.fromString(first)));
        assertEquals(original.get("extractedData"), result(get(owner, "/api/documents/" + documentId), 200).get("extractedData"));

        String second = business(owner, "Separate Tenant");
        assertNotEquals(first, second);
        assertEquals(0, result(get(owner, "/api/scores/history"), 200).size());
        assertEquals(0, search(owner, "2026-09", "score").size());
        assertEquals(0, search(owner, "mvp-receipt", "document").size());
        result(get(owner, "/api/documents/" + documentId), 404);
        result(get(owner, "/api/documents/" + documentId + "/corrections"), 404);
        result(send(owner, "PATCH", "/api/recommendations/" + actionId + "/status", Map.of("status", "DISMISSED")), 404);
        result(send(owner, "POST", "/api/businesses/active", Map.of("businessId", first)), 200);
        assertEquals(3, result(get(owner, "/api/scores/history"), 200).size());
    }

    @Test
    void historyReturnsOnlyTheNewestTwelveActuallyStoredMonths() throws Exception {
        HttpClient owner = browser(); register(owner); business(owner, "History Fixture");
        for (int i = 0; i < 13; i++) {
            String month = java.time.YearMonth.of(2024, 1).plusMonths(i * 2L).toString();
            result(send(owner, "POST", "/api/records/monthly", record(month)), 201);
        }
        JsonNode history = result(get(owner, "/api/scores/history"), 200);
        assertEquals(12, history.size());
        for (int i = 0; i < 12; i++) {
            assertEquals(java.time.YearMonth.of(2026, 1).minusMonths(i * 2L).toString(), history.get(i).get("month").asText());
            assertTrue(history.get(i).at("/explanation/previousScore").isNull(), "No invented intervening calendar months");
        }
        assertEquals("2024-01", result(send(owner, "POST", "/api/scores/query", Map.of("month", "2024-01")), 200).get("month").asText());
    }

    @Test
    void existingAccountInvitationsAndNewEndpointsRespectAllFourRoles() throws Exception {
        HttpClient owner = browser(); register(owner);
        String businessId = business(owner, "Team Fixture");
        result(send(owner, "POST", "/api/records/monthly", record("2026-09")), 201);
        String recommendationId = result(get(owner, "/api/recommendations"), 200).get(0).get("id").asText();
        String documentId = upload(owner);
        for (String role : List.of("ACCOUNTANT", "MANAGER", "VIEWER")) {
            HttpClient member = browser(); String email = register(member);
            JsonNode invitation = result(send(owner, "POST", "/api/memberships", Map.of("email", email, "role", role)), 201);
            String id = invitation.get("id").asText();
            assertEquals("INVITED", invitation.get("status").asText());
            assertEquals(0, result(get(member, "/api/businesses"), 200).size());
            result(send(member, "POST", "/api/businesses/active", Map.of("businessId", businessId)), 403);
            result(send(owner, "POST", "/api/invitations/" + id + "/accept", Map.of()), 404);
            assertEquals("Team Fixture", result(get(member, "/api/invitations"), 200).get(0).get("businessName").asText());
            result(send(member, "POST", "/api/invitations/" + id + "/accept", Map.of()), 204);
            result(send(member, "POST", "/api/businesses/active", Map.of("businessId", businessId)), 200);
            assertEquals(1, result(get(member, "/api/scores/history"), 200).size());
            result(get(member, "/api/memberships"), 403);
            result(send(member, "PATCH", "/api/businesses/active/name", Map.of("businessName", "Forbidden")), 403);
            result(send(member, "PATCH", "/api/recommendations/" + recommendationId + "/status", Map.of("status", "VIEWED")), "ACCOUNTANT".equals(role) ? 200 : 403);
            if ("VIEWER".equals(role)) {
                result(send(member, "POST", "/api/search", Map.of("query", "mvp-receipt", "type", "document")), 403);
                assertEquals(0, search(member, "mvp-receipt", "all").size());
                result(get(member, "/api/documents/" + documentId), 403);
            } else assertEquals(1, search(member, "mvp-receipt", "document").size());
            result(send(owner, "PATCH", "/api/memberships/" + id, Map.of("status", "SUSPENDED")), 200);
            result(get(member, "/api/scores/history"), 409);
            result(send(owner, "PATCH", "/api/memberships/" + id, Map.of("status", "ACTIVE")), 200);
            result(send(member, "POST", "/api/businesses/active", Map.of("businessId", businessId)), 200);
            result(send(owner, "DELETE", "/api/memberships/" + id, Map.of()), 204);
            result(get(member, "/api/scores/history"), 409);
        }
    }
}
