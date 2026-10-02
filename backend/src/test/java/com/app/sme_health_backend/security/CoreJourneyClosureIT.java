package com.app.sme_health_backend.security;

import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP, CSRF, JDBC sessions, PostgreSQL, scoring/advice; no mocked application services. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CoreJourneyClosureIT extends DisposablePostgres {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired BusinessMembershipRepository memberships;
    private final ObjectMapper json = new ObjectMapper();
    private static final String PASSWORD = "closure-http-fixture-password-123";
    private static final Map<String, Object> RECORD = Map.of("month", "2026-09", "cashInflow", 500000,
            "cashOutflow", 200000, "revenue", 500000, "cogs", 150000, "operatingExpenses", 50000,
            "cashBalanceEom", 300000, "financingType", "none");

    private HttpClient browser() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
    private HttpResponse<String> get(HttpClient browser, String path) throws Exception {
        return browser.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(HttpClient browser, String path, Object body) throws Exception {
        JsonNode csrf = json.readTree(get(browser, "/api/auth/csrf").body());
        return browser.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode result(HttpResponse<String> response, int expected) throws Exception {
        assertEquals(expected, response.statusCode(), "Unexpected HTTP status for closure journey");
        return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
    }
    private String register(HttpClient browser) throws Exception {
        String email = "journey-" + UUID.randomUUID() + "@example.test";
        result(post(browser, "/api/auth/register", Map.of("email", email, "password", PASSWORD, "fullName", "Journey Fixture")), 201);
        return email;
    }
    private void login(HttpClient browser, String email) throws Exception {
        assertEquals("FULLY_AUTHENTICATED", result(post(browser, "/api/auth/login", Map.of("email", email, "password", PASSWORD)), 200).get("authStage").asText());
    }

    @Test
    void registerPersistScoreAdviceReloginSwitchTenantAndDenyViewerManagerWrites() throws Exception {
        assertEquals("finsight_app", jdbc.queryForObject("SELECT current_user", String.class));
        HttpClient owner = browser();
        String email = register(owner);
        login(owner, email);
        String first = result(post(owner, "/api/businesses", Map.of("businessType", "retail", "languagePreference", "en", "whatsappOptIn", false)), 201).get("businessId").asText();
        String recordId = result(post(owner, "/api/records/monthly", RECORD), 201).get("id").asText();
        JsonNode dashboard = result(get(owner, "/api/dashboard"), 200);
        assertEquals(first, dashboard.get("businessId").asText());
        assertNotNull(dashboard.get("score"));
        assertFalse(dashboard.get("score").isNull());
        assertFalse(dashboard.get("topInsight").isNull());
        assertFalse(dashboard.get("topRecommendation").isNull());
        assertEquals(1, dashboard.get("cashFlowHistory").size());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM monthly_records WHERE user_id=?", Integer.class, UUID.fromString(first)));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM score_results WHERE user_id=?", Integer.class, UUID.fromString(first)));
        result(post(owner, "/api/auth/logout", Map.of()), 204);
        assertEquals(401, get(owner, "/api/records/monthly").statusCode());
        login(owner, email);
        assertEquals(recordId, result(get(owner, "/api/records/monthly"), 200).get(0).get("id").asText());
        assertEquals(dashboard.get("score"), result(get(owner, "/api/dashboard"), 200).get("score"));

        String second = result(post(owner, "/api/businesses", Map.of("businessType", "services", "whatsappOptIn", false)), 201).get("businessId").asText();
        assertNotEquals(first, second);
        assertEquals(0, result(get(owner, "/api/records/monthly"), 200).size());
        assertEquals(404, get(owner, "/api/records/monthly/id/" + recordId).statusCode());
        result(post(owner, "/api/businesses/active", Map.of("businessId", first)), 200);
        assertEquals(recordId, result(get(owner, "/api/records/monthly/id/" + recordId), 200).get("id").asText());

        for (MembershipRole role : new MembershipRole[] {MembershipRole.VIEWER, MembershipRole.MANAGER}) {
            HttpClient restricted = browser();
            String restrictedEmail = register(restricted);
            UUID userId = users.findByEmail(restrictedEmail).orElseThrow().getId();
            memberships.saveAndFlush(new BusinessMembership(userId, UUID.fromString(first), role, MembershipStatus.ACTIVE));
            login(restricted, restrictedEmail);
            assertEquals(200, get(restricted, "/api/records/monthly").statusCode());
            assertEquals(403, post(restricted, "/api/records/monthly", RECORD).statusCode());
            assertEquals(403, post(restricted, "/api/businesses/active", Map.of("businessId", second)).statusCode());
        }
    }
}
