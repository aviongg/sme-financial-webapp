package com.app.sme_health_backend.testsupport;

import com.app.sme_health_backend.identity.dto.CsrfResponse;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authenticated (session + CSRF + active business) HTTP fixture for product-completion integration tests.
 * Subclasses add @SpringBootTest, @ActiveProfiles("dev") and @AutoConfigureMockMvc.
 */
public abstract class ProductApiTestBase extends DisposablePostgres {
    public static final String PASSWORD = "Product-complete-synthetic-2026!";
    public static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Karachi"));

    @Autowired protected AppUserRepository users;
    @Autowired protected BusinessRepository businesses;
    @Autowired protected BusinessMembershipRepository memberships;
    @Autowired protected BusinessProfileRepository profiles;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected PasswordEncoder passwordEncoder;

    public record Fixture(AppUser user, UUID businessId) { }

    /** A logged-in actor with its own session cookie. */
    public record Actor(Fixture fixture, Cookie session) {
        public UUID businessId() {
            return fixture.businessId();
        }
    }

    protected Fixture fixture(MembershipRole role) {
        UUID businessId = UUID.randomUUID();
        businesses.saveAndFlush(new Business(businessId));
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(businessId);
        profile.setBusinessType("retail");
        profile.setLanguagePreference("en");
        profiles.saveAndFlush(profile);
        return member(businessId, role);
    }

    protected Fixture member(UUID businessId, MembershipRole role) {
        AppUser user = new AppUser();
        user.setEmail("pc." + role.name().toLowerCase() + "." + UUID.randomUUID() + "@example.com");
        user.setFullName("Synthetic " + role.name());
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        user = users.saveAndFlush(user);
        memberships.saveAndFlush(new BusinessMembership(user.getId(), businessId, role, MembershipStatus.ACTIVE));
        return new Fixture(user, businessId);
    }

    protected Actor owner() throws Exception {
        Fixture f = fixture(MembershipRole.OWNER);
        return new Actor(f, login(f));
    }

    protected Actor actor(UUID businessId, MembershipRole role) throws Exception {
        Fixture f = member(businessId, role);
        return new Actor(f, login(f));
    }

    protected Cookie login(Fixture f) throws Exception {
        var csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        Cookie xsrf = csrf.getCookie("XSRF-TOKEN");
        Cookie initial = csrf.getCookie("FINSIGHT_SESSION");
        assertNotNull(xsrf);
        var request = post("/api/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", f.user().getEmail(), "password", PASSWORD)));
        if (initial != null) request.cookie(initial);
        var session = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(session);
        mvc.perform(post("/api/businesses/active").cookie(session).with(csrf(session)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("businessId", f.businessId())))).andExpect(status().isOk());
        return session;
    }

    protected RequestPostProcessor csrf(Cookie session) throws Exception {
        var result = mvc.perform(get("/api/auth/csrf").cookie(session)).andExpect(status().isOk()).andReturn().getResponse();
        var xsrf = result.getCookie("XSRF-TOKEN");
        assertNotNull(xsrf);
        var token = json.readValue(result.getContentAsString(), CsrfResponse.class);
        return request -> {
            var cookies = new ArrayList<>(Arrays.asList(request.getCookies() == null ? new Cookie[0] : request.getCookies()));
            cookies.removeIf(cookie -> cookie.getName().equals(xsrf.getName()));
            cookies.add(xsrf);
            request.setCookies(cookies.toArray(Cookie[]::new));
            request.addHeader(token.headerName(), token.token());
            return request;
        };
    }

    // ------------------------------------------------------------------ HTTP helpers

    protected JsonNode call(Actor a, MockHttpServletRequestBuilder builder, int expectedStatus) throws Exception {
        var response = mvc.perform(builder.cookie(a.session())).andReturn().getResponse();
        assertEquals(expectedStatus, response.getStatus(), () -> {
            try {
                return "Unexpected status for request; body=" + response.getContentAsString();
            } catch (Exception e) {
                return "Unexpected status";
            }
        });
        String body = response.getContentAsString();
        return body == null || body.isBlank() ? null : json.readTree(body);
    }

    protected JsonNode getJson(Actor a, String path, int expected) throws Exception {
        return call(a, get(path), expected);
    }

    protected JsonNode getJson(Actor a, String path) throws Exception {
        return getJson(a, path, 200);
    }

    protected JsonNode postJson(Actor a, String path, Object body, String idempotencyKey, int expected) throws Exception {
        var builder = post(path).with(csrf(a.session())).contentType("application/json")
                .content(json.writeValueAsString(body == null ? Map.of() : body));
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return call(a, builder, expected);
    }

    protected JsonNode putJson(Actor a, String path, Object body, int expected) throws Exception {
        var builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                .with(csrf(a.session())).contentType("application/json").content(json.writeValueAsString(body));
        return call(a, builder, expected);
    }

    protected static String key() {
        return "it-" + UUID.randomUUID();
    }

    protected static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    // ------------------------------------------------------------------ domain fixtures through real APIs

    protected UUID account(Actor a, String openingBalance) throws Exception {
        JsonNode node = postJson(a, "/api/finance/accounts", map("accountName", "Main " + UUID.randomUUID().toString().substring(0, 6),
                "accountType", "BANK", "currency", "PKR", "openingBalance", openingBalance,
                "openingDate", TODAY.minusDays(60).toString()), key(), 201);
        return UUID.fromString(node.get("id").asText());
    }

    protected UUID counterparty(Actor a, String name, String type) throws Exception {
        JsonNode node = postJson(a, "/api/finance/counterparties", map("displayName", name, "type", type), null, 201);
        return UUID.fromString(node.get("id").asText());
    }

    protected UUID item(Actor a, String sku) throws Exception {
        JsonNode node = postJson(a, "/api/inventory/items", map("sku", sku, "name", "Item " + sku, "unit", "pcs",
                "itemKind", "RESALE_GOOD", "reorderLevel", "2"), null, 201);
        return UUID.fromString(node.get("id").asText());
    }

    /** Creates and finalizes an invoice ("invoices") or bill ("bills"); lines = [description, qty, price, itemId?]. */
    protected UUID finalizedDocument(Actor a, String resource, LocalDate date, List<Object[]> lines) throws Exception {
        List<Map<String, Object>> body = new ArrayList<>();
        for (Object[] l : lines) {
            Map<String, Object> line = map("description", l[0], "quantity", l[1], "unit", "pcs", "unitPrice", l[2]);
            if (l.length > 3 && l[3] != null) line.put("inventoryItemId", l[3].toString());
            body.add(line);
        }
        JsonNode draft = postJson(a, "/api/" + resource, map("counterpartyName", "Synthetic Party",
                "documentDate", date.toString(), "dueDate", date.plusDays(30).toString(), "notes", null, "lines", body), null, 201);
        UUID id = UUID.fromString(draft.get("id").asText());
        postJson(a, "/api/" + resource + "/" + id + "/finalize", map("expectedVersion", draft.get("version").asLong()), null, 200);
        return id;
    }

    protected UUID recognize(Actor a, UUID documentId, String kind, UUID counterpartyId) throws Exception {
        JsonNode node = postJson(a, "/api/finance/obligations/recognize-commercial-document", map("documentId", documentId,
                "documentKind", kind, "counterpartyId", counterpartyId, "notes", "IT recognition"), key(), 201);
        return UUID.fromString(node.get("obligationId").asText());
    }

    protected UUID settle(Actor a, UUID obligationId, UUID accountId, String amount) throws Exception {
        JsonNode node = postJson(a, "/api/finance/obligations/" + obligationId + "/settlements", map("accountId", accountId,
                "amount", amount, "settlementDate", TODAY.toString(), "reference", "IT", "description", "IT settlement"),
                key(), 201);
        return UUID.fromString(node.get("transactionId").asText());
    }

    protected UUID postTransaction(Actor a, UUID accountId, String eventType, String amount, LocalDate date) throws Exception {
        JsonNode node = postJson(a, "/api/finance/transactions", map("accountId", accountId, "eventType", eventType,
                "amount", amount, "effectiveDate", date.toString(), "description", "IT " + eventType), key(), 201);
        return UUID.fromString(node.get("id").asText());
    }

    protected void reverseTransaction(Actor a, UUID transactionId) throws Exception {
        postJson(a, "/api/finance/transactions/" + transactionId + "/reverse", map("reason", "IT reversal"), key(), 201);
    }

    protected UUID project(Actor a, String code) throws Exception {
        JsonNode node = postJson(a, "/api/projects", map("code", code, "name", "Project " + code, "kind", "PROJECT",
                "description", null, "startDate", TODAY.minusDays(30).toString(), "targetEndDate", TODAY.plusDays(60).toString()),
                null, 201);
        return UUID.fromString(node.get("id").asText());
    }

    protected java.math.BigDecimal decimal(JsonNode node, String field) {
        return new java.math.BigDecimal(node.get(field).asText());
    }
}
