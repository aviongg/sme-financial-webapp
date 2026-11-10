package com.app.sme_health_backend.inventory;

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
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.scoring.service.ScoringService;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end PostgreSQL + MockMvc + session + CSRF tests for F08 Phase A: catalog lifecycle, permissions,
 * tenant isolation, audit events, and proof that neither catalog writes nor the valuation preview touch
 * any financial, score, project or commercial state.
 */
@SpringBootTest(properties = {"app.ai.enabled=true", "app.ai.provider=mock", "app.ai.demo-enabled=true"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class InventoryCatalogPostgreSqlIT extends DisposablePostgres {
    @Autowired AppUserRepository users;
    @Autowired BusinessRepository businesses;
    @Autowired BusinessMembershipRepository memberships;
    @Autowired BusinessProfileRepository profiles;
    @Autowired MonthlyRecordRepository records;
    @Autowired ScoringService scoring;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwordEncoder;

    static final String FIXTURE_PASSWORD = "F08-synthetic-password-2026!";
    /** Every domain table on this base except inventory_items, audit and session bookkeeping. */
    static final List<String> PROTECTED_TABLES = List.of("monthly_records", "score_results", "insights",
            "recommendations", "uploaded_documents", "document_corrections", "document_financial_contributions",
            "projects", "project_budget_versions", "project_budget_lines", "commercial_documents",
            "commercial_document_lines", "commercial_number_sequences", "business_profiles",
            "business_configuration", "dashboard_preferences", "whatsapp_deliveries");

    record Fixture(AppUser user, UUID businessId) { }

    Fixture fixture(MembershipRole role) {
        UUID businessId = UUID.randomUUID();
        businesses.saveAndFlush(new Business(businessId));
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(businessId);
        profile.setBusinessType("retail");
        profile.setLanguagePreference("en");
        profiles.saveAndFlush(profile);
        return member(businessId, role);
    }

    Fixture member(UUID businessId, MembershipRole role) {
        AppUser user = new AppUser();
        user.setEmail("f08." + role.name().toLowerCase() + "." + UUID.randomUUID() + "@example.com");
        user.setFullName("Synthetic F08 " + role.name());
        user.setPasswordHash(passwordEncoder.encode(FIXTURE_PASSWORD));
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        user = users.saveAndFlush(user);
        memberships.saveAndFlush(new BusinessMembership(user.getId(), businessId, role, MembershipStatus.ACTIVE));
        return new Fixture(user, businessId);
    }

    Cookie login(Fixture f) throws Exception {
        var csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        Cookie xsrf = csrf.getCookie("XSRF-TOKEN");
        Cookie initial = csrf.getCookie("FINSIGHT_SESSION");
        assertNotNull(xsrf);
        var request = post("/api/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", f.user.getEmail(), "password", FIXTURE_PASSWORD)));
        if (initial != null) request.cookie(initial);
        var session = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(session);
        mvc.perform(post("/api/businesses/active").cookie(session).with(csrf(session)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("businessId", f.businessId)))).andExpect(status().isOk());
        return session;
    }

    RequestPostProcessor csrf(Cookie session) throws Exception {
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

    Map<String, String> snapshot(List<String> tables) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : tables) {
            result.put(table, jdbc.queryForObject(
                    "SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text), '[]')::text FROM " + table + " t",
                    String.class));
        }
        return result;
    }

    void seedFinancialBaseline(UUID businessId) {
        var record = new MonthlyRecord();
        record.setUserId(businessId);
        record.setMonth("2026-10");
        record.setRevenue(new BigDecimal("500000.00"));
        record.setOperatingExpenses(new BigDecimal("300000.00"));
        record.setCashInflow(new BigDecimal("480000.00"));
        record.setCashOutflow(new BigDecimal("290000.00"));
        record.setCashBalanceEom(new BigDecimal("190000.00"));
        record.setInventoryValue(new BigDecimal("75000.00"));
        record.setUpdatedAt(LocalDateTime.now());
        records.saveAndFlush(record);
        scoring.calculateAndSaveScore(businessId, "2026-10");
    }

    @Test
    void catalogLifecycleIsVersionedAuditedAndHasNoFinancialEffect() throws Exception {
        var owner = fixture(MembershipRole.OWNER);
        seedFinancialBaseline(owner.businessId);
        var session = login(owner);
        Map<String, String> before = snapshot(PROTECTED_TABLES);

        var created = mvc.perform(post("/api/inventory/items").cookie(session).with(csrf(session)).contentType("application/json")
                        .content("""
                                {"sku":" rm-cotton-01 ","name":" Cotton yarn ","unit":"kg","itemKind":"RAW_MATERIAL",
                                 "category":"Yarn","reorderLevel":"25.500000"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.sku").value("RM-COTTON-01"))
                .andExpect(jsonPath("$.name").value("Cotton yarn"))
                .andExpect(jsonPath("$.reorderLevel").value("25.5"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.catalogOnly").value(true))
                .andExpect(jsonPath("$.stockTracking").value("NOT_IMPLEMENTED_PHASE_A"))
                .andExpect(jsonPath("$.quantity").doesNotExist())
                .andExpect(jsonPath("$.value").doesNotExist())
                .andReturn().getResponse();
        UUID itemId = UUID.fromString(json.readTree(created.getContentAsString()).get("id").asText());

        // Case-insensitive SKU uniqueness.
        mvc.perform(post("/api/inventory/items").cookie(session).with(csrf(session)).contentType("application/json")
                        .content("{\"sku\":\"Rm-Cotton-01\",\"name\":\"Dup\",\"unit\":\"kg\",\"itemKind\":\"CONSUMABLE\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("duplicate_inventory_sku"));
        // FINISHED_GOOD can be catalogued (production receipt is not implemented).
        mvc.perform(post("/api/inventory/items").cookie(session).with(csrf(session)).contentType("application/json")
                        .content("{\"sku\":\"FG-SHIRT\",\"name\":\"Finished shirt\",\"unit\":\"pcs\",\"itemKind\":\"FINISHED_GOOD\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.reorderLevel").doesNotExist());

        String update = "{\"sku\":\"RM-COTTON-01\",\"name\":\"Cotton yarn 30s\",\"unit\":\"kg\",\"itemKind\":\"RAW_MATERIAL\","
                + "\"category\":null,\"reorderLevel\":null,\"expectedVersion\":0}";
        mvc.perform(put("/api/inventory/items/" + itemId).cookie(session).with(csrf(session)).contentType("application/json").content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.name").value("Cotton yarn 30s"))
                .andExpect(jsonPath("$.category").doesNotExist());
        mvc.perform(put("/api/inventory/items/" + itemId).cookie(session).with(csrf(session)).contentType("application/json").content(update))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("stale_inventory_item_version"));
        mvc.perform(put("/api/inventory/items/" + itemId).cookie(session).with(csrf(session)).contentType("application/json")
                        .content(update.replace("RM-COTTON-01", "fg-shirt").replace("\"expectedVersion\":0", "\"expectedVersion\":1")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("duplicate_inventory_sku"));
        mvc.perform(post("/api/inventory/items/" + itemId + "/archive").cookie(session).with(csrf(session)).contentType("application/json")
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("stale_inventory_item_version"));
        mvc.perform(post("/api/inventory/items/" + itemId + "/archive").cookie(session).with(csrf(session)).contentType("application/json")
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED")).andExpect(jsonPath("$.version").value(2));
        mvc.perform(put("/api/inventory/items/" + itemId).cookie(session).with(csrf(session)).contentType("application/json")
                        .content(update.replace("\"expectedVersion\":0", "\"expectedVersion\":2")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("invalid_inventory_item_state"));

        // Archived items leave the default register but remain retrievable.
        mvc.perform(get("/api/inventory/items").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(owner.businessId.toString()))
                .andExpect(jsonPath("$.catalogOnly").value(true))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sku").value("FG-SHIRT"));
        mvc.perform(get("/api/inventory/items?includeArchived=true&q=cotton").cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value("ARCHIVED"));
        mvc.perform(get("/api/inventory/items?kind=FINISHED_GOOD&status=ACTIVE").cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/inventory/items?kind=WIP").cookie(session))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.kind").value("enum_invalid"));
        mvc.perform(get("/api/inventory/items?q=%25").cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));

        List<String> events = jdbc.queryForList("SELECT event_type FROM security_audit_events WHERE target_type='inventory_item' "
                + "AND target_id=? ORDER BY occurred_at, id", String.class, itemId.toString());
        assertEquals(List.of("INVENTORY_ITEM_CREATED", "INVENTORY_ITEM_UPDATED", "INVENTORY_ITEM_ARCHIVED"), events);
        assertEquals(owner.businessId, jdbc.queryForObject("SELECT DISTINCT business_id FROM security_audit_events WHERE target_id=?",
                UUID.class, itemId.toString()));

        assertEquals(before, snapshot(PROTECTED_TABLES),
                "Catalog writes may only change inventory_items and audit metadata");
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM inventory_items WHERE business_id=?", Integer.class, owner.businessId));
    }

    @Test
    void valuationPreviewIsStatelessNonAuthoritativeAndPersistsNothing() throws Exception {
        var owner = fixture(MembershipRole.OWNER);
        var viewer = member(owner.businessId, MembershipRole.VIEWER);
        seedFinancialBaseline(owner.businessId);
        var ownerSession = login(owner);
        var viewerSession = login(viewer);
        mvc.perform(post("/api/inventory/items").cookie(ownerSession).with(csrf(ownerSession)).contentType("application/json")
                .content("{\"sku\":\"RS-01\",\"name\":\"Resale good\",\"unit\":\"pcs\",\"itemKind\":\"RESALE_GOOD\"}"))
                .andExpect(status().isCreated());
        var ownerCsrf = csrf(ownerSession);
        var viewerCsrf = csrf(viewerSession);

        List<String> everything = new ArrayList<>(PROTECTED_TABLES);
        everything.addAll(List.of("inventory_items", "security_audit_events", "businesses", "business_memberships", "app_users"));
        Map<String, String> before = snapshot(everything);

        String body = """
                {"openingQuantity":"0","openingValue":"0","steps":[
                  {"type":"RECEIPT","quantity":"3","value":"10.0000"},
                  {"type":"ISSUE","quantity":"1"},{"type":"ISSUE","quantity":"1"},{"type":"ISSUE","quantity":"1"}]}
                """;
        for (var pair : List.of(Map.entry(ownerSession, ownerCsrf), Map.entry(viewerSession, viewerCsrf))) {
            mvc.perform(post("/api/inventory/valuation/preview").cookie(pair.getKey()).with(pair.getValue())
                            .contentType("application/json").content(body))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.mode").value("PLANNING_VALUATION_PREVIEW"))
                    .andExpect(jsonPath("$.authoritative").value(false))
                    .andExpect(jsonPath("$.persisted").value(false))
                    .andExpect(jsonPath("$.financialEffect").value("NONE"))
                    .andExpect(jsonPath("$.businessId").value(owner.businessId.toString()))
                    .andExpect(jsonPath("$.policy.version").value("f08-wac-v1"))
                    .andExpect(jsonPath("$.policy.status").value("APPROVED"))
                    .andExpect(jsonPath("$.steps[1].issueValue").value("3.3333"))
                    .andExpect(jsonPath("$.steps[2].issueValue").value("3.3334"))
                    .andExpect(jsonPath("$.steps[3].issueValue").value("3.3333"))
                    .andExpect(jsonPath("$.steps[3].fullDepletion").value(true))
                    .andExpect(jsonPath("$.closing.quantity").value("0"))
                    .andExpect(jsonPath("$.closing.value").value("0.0000"))
                    .andExpect(jsonPath("$.closing.averageUnitCost").doesNotExist())
                    .andExpect(jsonPath("$.totals.issuedValue").value("10.0000"))
                    .andExpect(jsonPath("$.totals.valueConserved").value(true));
        }
        mvc.perform(post("/api/inventory/valuation/preview").cookie(ownerSession).with(ownerCsrf).contentType("application/json")
                        .content("{\"openingQuantity\":\"1\",\"openingValue\":\"5\",\"steps\":[{\"type\":\"ISSUE\",\"quantity\":\"2\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['steps[0].quantity']").value("insufficient_stock"));
        mvc.perform(post("/api/inventory/valuation/preview").cookie(ownerSession).with(ownerCsrf).contentType("application/json")
                        .content("{\"openingQuantity\":\"1\",\"openingValue\":\"5.00001\",\"steps\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.openingValue").value("decimal_invalid"));

        assertEquals(before, snapshot(everything), "The valuation preview must persist nothing, including audit rows");
    }

    @Test
    void permissionsTenantIsolationAndCsrfAreEnforced() throws Exception {
        var ownerA = fixture(MembershipRole.OWNER);
        var accountantA = member(ownerA.businessId, MembershipRole.ACCOUNTANT);
        var managerA = member(ownerA.businessId, MembershipRole.MANAGER);
        var viewerA = member(ownerA.businessId, MembershipRole.VIEWER);
        var ownerB = fixture(MembershipRole.OWNER);
        var accountant = login(accountantA);
        var manager = login(managerA);
        var viewer = login(viewerA);
        var otherOwner = login(ownerB);

        mvc.perform(get("/api/inventory/items")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/inventory/valuation/preview").contentType("application/json")
                .content("{\"openingQuantity\":\"0\",\"openingValue\":\"0\",\"steps\":[]}")).andExpect(status().isForbidden());

        var created = mvc.perform(post("/api/inventory/items").cookie(accountant).with(csrf(accountant)).contentType("application/json")
                        .content("{\"sku\":\"CN-01\",\"name\":\"Packing tape\",\"unit\":\"roll\",\"itemKind\":\"CONSUMABLE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        UUID itemId = UUID.fromString(json.readTree(created.getContentAsString()).get("id").asText());
        mvc.perform(post("/api/inventory/items").cookie(accountant).contentType("application/json")
                        .content("{\"sku\":\"CN-02\",\"name\":\"No CSRF\",\"unit\":\"roll\",\"itemKind\":\"CONSUMABLE\"}"))
                .andExpect(status().isForbidden());

        // Another business may reuse the SKU and can never see, edit or archive business A's item.
        mvc.perform(post("/api/inventory/items").cookie(otherOwner).with(csrf(otherOwner)).contentType("application/json")
                        .content("{\"sku\":\"cn-01\",\"name\":\"Business B tape\",\"unit\":\"roll\",\"itemKind\":\"CONSUMABLE\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/inventory/items/" + itemId).cookie(otherOwner))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("inventory_item_not_found"));
        mvc.perform(get("/api/inventory/items/" + UUID.randomUUID()).cookie(otherOwner))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("inventory_item_not_found"));
        mvc.perform(put("/api/inventory/items/" + itemId).cookie(otherOwner).with(csrf(otherOwner)).contentType("application/json")
                        .content("{\"sku\":\"CN-01\",\"name\":\"Hijack\",\"unit\":\"roll\",\"itemKind\":\"CONSUMABLE\",\"expectedVersion\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/inventory/items/" + itemId + "/archive").cookie(otherOwner).with(csrf(otherOwner))
                        .contentType("application/json").content("{\"expectedVersion\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/inventory/items").cookie(otherOwner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Business B tape"));

        for (Cookie readOnly : List.of(manager, viewer)) {
            mvc.perform(get("/api/inventory/items").cookie(readOnly))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(itemId.toString()));
            mvc.perform(get("/api/inventory/items/" + itemId).cookie(readOnly)).andExpect(status().isOk());
            mvc.perform(post("/api/inventory/valuation/preview").cookie(readOnly).with(csrf(readOnly)).contentType("application/json")
                    .content("{\"openingQuantity\":\"2\",\"openingValue\":\"20\",\"steps\":[{\"type\":\"ISSUE\",\"quantity\":\"1\"}]}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.steps[0].issueValue").value("10.0000"));
            mvc.perform(post("/api/inventory/items").cookie(readOnly).with(csrf(readOnly)).contentType("application/json")
                            .content("{\"sku\":\"CN-09\",\"name\":\"Denied\",\"unit\":\"roll\",\"itemKind\":\"CONSUMABLE\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/inventory/items/" + itemId).cookie(readOnly).with(csrf(readOnly)).contentType("application/json")
                            .content("{\"sku\":\"CN-01\",\"name\":\"Denied\",\"unit\":\"roll\",\"itemKind\":\"CONSUMABLE\",\"expectedVersion\":0}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/inventory/items/" + itemId + "/archive").cookie(readOnly).with(csrf(readOnly))
                            .contentType("application/json").content("{\"expectedVersion\":0}"))
                    .andExpect(status().isForbidden());
        }
        assertEquals(0, jdbc.queryForObject("SELECT version FROM inventory_items WHERE id=?", Integer.class, itemId));

        // Closed schema: tenant, stock and status fields cannot be smuggled in.
        mvc.perform(post("/api/inventory/items").cookie(accountant).with(csrf(accountant)).contentType("application/json")
                        .content("{\"sku\":\"CN-03\",\"name\":\"x\",\"unit\":\"u\",\"itemKind\":\"CONSUMABLE\",\"businessId\":\"" + ownerB.businessId + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.request").value("unknown_field"));
        mvc.perform(post("/api/inventory/items").cookie(accountant).with(csrf(accountant)).contentType("application/json")
                        .content("{\"sku\":\"CN-04\",\"name\":\"x\",\"unit\":\"u\",\"itemKind\":\"CONSUMABLE\",\"reorderLevel\":\"-1\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reorderLevel").value("decimal_invalid"));
        mvc.perform(post("/api/inventory/items").cookie(accountant).with(csrf(accountant)).contentType("application/json")
                        .content("{\"sku\":\"bad sku\",\"name\":\"x\",\"unit\":\"u\",\"itemKind\":\"CONSUMABLE\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.sku").value("sku_invalid"));
        mvc.perform(post("/api/inventory/items").cookie(accountant).with(csrf(accountant)).contentType("application/json")
                        .content("{\"sku\":\"CN-05\",\"name\":\"" + "x".repeat(161) + "\",\"unit\":\"u\",\"itemKind\":\"CONSUMABLE\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").value("text_invalid"));
        mvc.perform(post("/api/inventory/items").cookie(accountant).with(csrf(accountant)).contentType("application/json")
                        .content("{\"sku\":\"CN-06\",\"name\":\"" + "x".repeat(70_000) + "\"}"))
                .andExpect(status().isPayloadTooLarge());
    }
}
