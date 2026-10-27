package com.app.sme_health_backend.commercial;

import com.app.sme_health_backend.commercial.dto.CommercialDocumentDraftRequest;
import com.app.sme_health_backend.commercial.dto.CommercialLineRequest;
import com.app.sme_health_backend.commercial.dto.CommercialTransitionRequest;
import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.service.CommercialDocumentService;
import com.app.sme_health_backend.commercial.service.CommercialException;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
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
import com.app.sme_health_backend.projects.dto.ProjectCreateRequest;
import com.app.sme_health_backend.projects.model.ProjectKind;
import com.app.sme_health_backend.projects.service.ProjectPlanningService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real PostgreSQL + MockMvc + session + CSRF verification of F04 Phase A invoices and bills:
 * lifecycle, numbering, permissions, tenant isolation, encryption at rest, concurrency and
 * proof that no financial, score, project or estimate data changes.
 */
@SpringBootTest(properties = {"app.ai.enabled=true", "app.ai.provider=mock", "app.ai.demo-enabled=true"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class CommercialDocumentPostgreSqlIT extends DisposablePostgres {
    private static final String FIXTURE_PASSWORD = "F04-synthetic-password-2026!";
    private static final List<String> ISOLATED_TABLES = List.of(
            "monthly_records", "document_financial_contributions", "score_results", "insights", "recommendations",
            "projects", "project_budget_versions", "project_budget_lines", "uploaded_documents",
            "business_configuration", "dashboard_preferences");
    private static final String LINES = """
            [{"description":"Design work","quantity":"2","unit":"day","unitPrice":"500.00"},
             {"description":"Cabling","quantity":"3.5","unit":"metre","unitPrice":"120.40"},
             {"description":"Goodwill visit","quantity":"1","unit":null,"unitPrice":"0"}]""";

    @Autowired AppUserRepository users;
    @Autowired BusinessRepository businesses;
    @Autowired BusinessMembershipRepository memberships;
    @Autowired BusinessProfileRepository profiles;
    @Autowired MonthlyRecordRepository records;
    @Autowired ScoringService scoring;
    @Autowired ProjectPlanningService projects;
    @Autowired CommercialDocumentService service;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwordEncoder;

    record Fixture(AppUser user, UUID businessId, MembershipRole role) {
        BusinessAccessContext actor() {
            return new BusinessAccessContext(user.getId(), businessId, role);
        }
    }

    Fixture fixture(MembershipRole role) {
        UUID businessId = UUID.randomUUID();
        businesses.saveAndFlush(new Business(businessId));
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(businessId);
        profile.setBusinessType("services");
        profile.setLanguagePreference("en");
        profiles.saveAndFlush(profile);
        return member(businessId, role);
    }

    Fixture member(UUID businessId, MembershipRole role) {
        AppUser user = new AppUser();
        user.setEmail("f04." + role.name().toLowerCase() + "." + UUID.randomUUID() + "@example.com");
        user.setFullName("Synthetic F04 " + role.name());
        user.setPasswordHash(passwordEncoder.encode(FIXTURE_PASSWORD));
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        user = users.saveAndFlush(user);
        memberships.saveAndFlush(new BusinessMembership(user.getId(), businessId, role, MembershipStatus.ACTIVE));
        return new Fixture(user, businessId, role);
    }

    Cookie login(Fixture f) throws Exception {
        var csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        Cookie xsrf = csrf.getCookie("XSRF-TOKEN");
        Cookie initial = csrf.getCookie("FINSIGHT_SESSION");
        assertNotNull(xsrf);
        var request = post("/api/auth/login")
                .cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", f.user.getEmail(), "password", FIXTURE_PASSWORD)));
        if (initial != null) {
            request.cookie(initial);
        }
        var session = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(session);
        mvc.perform(post("/api/businesses/active").cookie(session).with(csrf(session))
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("businessId", f.businessId))))
                .andExpect(status().isOk());
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

    MockHttpServletRequestBuilder mutate(MockHttpServletRequestBuilder builder, Cookie session, String body) throws Exception {
        builder.cookie(session).with(csrf(session));
        return body == null ? builder : builder.contentType("application/json").content(body);
    }

    JsonNode body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    static String draftBody(String counterparty, String lines, Long expectedVersion) {
        return "{" + (counterparty == null ? "" : "\"counterpartyName\":\"" + counterparty + "\",")
                + "\"documentDate\":\"2026-10-28\",\"dueDate\":\"2026-11-27\",\"notes\":\"Net 30\",\"lines\":" + lines
                + (expectedVersion == null ? "" : ",\"expectedVersion\":" + expectedVersion) + "}";
    }

    @Test
    void invoiceAndBillLifecycleNumberingEncryptionAuditAndFinancialIsolationWorkEndToEnd() throws Exception {
        var owner = fixture(MembershipRole.OWNER);
        var session = login(owner);
        MonthlyRecord record = new MonthlyRecord();
        record.setUserId(owner.businessId);
        record.setMonth("2026-10");
        record.setRevenue(new BigDecimal("500000.00"));
        record.setOperatingExpenses(new BigDecimal("300000.00"));
        record.setCashInflow(new BigDecimal("480000.00"));
        record.setCashOutflow(new BigDecimal("290000.00"));
        record.setCashBalanceEom(new BigDecimal("190000.00"));
        record.setReceivablesOutstanding(new BigDecimal("15000.00"));
        record.setPayablesOutstanding(new BigDecimal("5000.00"));
        record.setUpdatedAt(LocalDateTime.now());
        records.saveAndFlush(record);
        scoring.calculateAndSaveScore(owner.businessId, "2026-10");
        projects.createProject(owner.actor(), new ProjectCreateRequest("PRJ-F04", "Isolation fixture", ProjectKind.PROJECT, null, null, null));
        Map<String, String> before = snapshot();
        installWriteProbe();
        try {
            // Backend-authoritative preview, including the exactness rule and explicit zero-priced lines.
            body(mvc.perform(mutate(post("/api/invoices/preview"), session, "{\"lines\":" + LINES + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lines[0].lineAmount").value("1000.00"))
                    .andExpect(jsonPath("$.lines[1].lineAmount").value("421.40"))
                    .andExpect(jsonPath("$.lines[2].lineAmount").value("0.00"))
                    .andExpect(jsonPath("$.subtotal").value("1421.40"))
                    .andExpect(jsonPath("$.total").value("1421.40"))
                    .andExpect(jsonPath("$.financialEffect").value("NONE")));
            mvc.perform(mutate(post("/api/invoices/preview"), session,
                            "{\"lines\":[{\"description\":\"x\",\"quantity\":\"1.5\",\"unitPrice\":\"333.33\"}]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("commercial_document_invalid"))
                    .andExpect(jsonPath("$.errors['lines[0].lineAmount']").value("line_amount_not_exact"));
            mvc.perform(mutate(post("/api/invoices/preview"), session, "{\"lines\":[]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.subtotal").isEmpty())
                    .andExpect(jsonPath("$.total").isEmpty());

            // Draft without lines: null totals, never zero; no number.
            JsonNode created = body(mvc.perform(mutate(post("/api/invoices"), session, draftBody("Acme Traders", "[]", null)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.kind").value("INVOICE"))
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.documentNumber").isEmpty())
                    .andExpect(jsonPath("$.subtotal").isEmpty())
                    .andExpect(jsonPath("$.total").isEmpty())
                    .andExpect(jsonPath("$.lineCount").value(0))
                    .andExpect(jsonPath("$.financialEffect").value("NONE"))
                    .andExpect(jsonPath("$.settlementAvailability").value("NOT_AVAILABLE_PHASE_A"))
                    .andExpect(jsonPath("$.finalizeBlockers[0]").value("lines_required"))
                    .andExpect(jsonPath("$.version").value(0)));
            UUID invoiceId = UUID.fromString(created.get("id").asText());
            assertEquals(owner.businessId.toString(), created.get("businessId").asText());
            assertEquals(owner.user.getId().toString(), created.get("createdByUserId").asText());
            for (String forbidden : List.of("amountPaid", "amountDue", "outstandingBalance", "paymentStatus")) {
                assertFalse(created.has(forbidden), forbidden);
            }
            String ciphertext = jdbc.queryForObject("SELECT counterparty_name_ciphertext FROM commercial_documents WHERE id=?", String.class, invoiceId);
            assertTrue(ciphertext.startsWith("enc:v1:"), "Counterparty name is stored only as an encrypted envelope");
            assertFalse(ciphertext.contains("Acme"));

            mvc.perform(mutate(post("/api/invoices/" + invoiceId + "/finalize"), session, "{\"expectedVersion\":0}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("lines_required"));

            // Edit draft; stale versions are rejected without replay or lost update.
            body(mvc.perform(mutate(put("/api/invoices/" + invoiceId), session, draftBody("Acme Traders", LINES, 0L)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(1))
                    .andExpect(jsonPath("$.subtotal").value("1421.40"))
                    .andExpect(jsonPath("$.total").value("1421.40"))
                    .andExpect(jsonPath("$.lines[1].quantity").value("3.5"))
                    .andExpect(jsonPath("$.lines[1].unitPrice").value("120.40"))
                    .andExpect(jsonPath("$.lines[2].unit").isEmpty())
                    .andExpect(jsonPath("$.finalizeBlockers").isEmpty()));
            mvc.perform(mutate(put("/api/invoices/" + invoiceId), session, draftBody("Stale Overwrite", "[]", 0L)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("stale_document_version"));
            mvc.perform(mutate(post("/api/invoices/" + invoiceId + "/finalize"), session, "{\"expectedVersion\":0}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("stale_document_version"));

            // Finalize assigns the first tenant/kind number and freezes the document.
            JsonNode finalized = body(mvc.perform(mutate(post("/api/invoices/" + invoiceId + "/finalize"), session, "{\"expectedVersion\":1}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FINALIZED"))
                    .andExpect(jsonPath("$.documentNumber").value("INV-000001"))
                    .andExpect(jsonPath("$.counterpartyName").value("Acme Traders"))
                    .andExpect(jsonPath("$.total").value("1421.40"))
                    .andExpect(jsonPath("$.financialEffect").value("NONE"))
                    .andExpect(jsonPath("$.version").value(2)));
            assertEquals(owner.user.getId().toString(), finalized.get("finalizedByUserId").asText());
            assertFalse(finalized.get("finalizedAt").isNull());
            mvc.perform(mutate(put("/api/invoices/" + invoiceId), session, draftBody("Changed", LINES, 2L)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("document_not_draft"));
            mvc.perform(mutate(delete("/api/invoices/" + invoiceId).param("expectedVersion", "2"), session, null))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("document_delete_forbidden"));
            mvc.perform(mutate(post("/api/invoices/" + invoiceId + "/finalize"), session, "{\"expectedVersion\":2}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("document_already_finalized"));
            assertEquals(2L, sequence(owner.businessId, "INVOICE"), "A repeated finalize never consumes another number");

            // Void requires a reason, retains the number and is then fully immutable.
            mvc.perform(mutate(post("/api/invoices/" + invoiceId + "/void"), session, "{\"expectedVersion\":2,\"reason\":\"  \"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reason").value("required"));
            body(mvc.perform(mutate(post("/api/invoices/" + invoiceId + "/void"), session, "{\"expectedVersion\":2,\"reason\":\"Customer cancelled the order\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("VOID"))
                    .andExpect(jsonPath("$.documentNumber").value("INV-000001"))
                    .andExpect(jsonPath("$.voidReason").value("Customer cancelled the order"))
                    .andExpect(jsonPath("$.voidedByUserId").value(owner.user.getId().toString()))
                    .andExpect(jsonPath("$.total").value("1421.40")));
            mvc.perform(mutate(post("/api/invoices/" + invoiceId + "/void"), session, "{\"expectedVersion\":3,\"reason\":\"again\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("document_already_void"));
            mvc.perform(mutate(post("/api/invoices/" + invoiceId + "/finalize"), session, "{\"expectedVersion\":3}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("invalid_document_transition"));
            mvc.perform(mutate(put("/api/invoices/" + invoiceId), session, draftBody("Changed", LINES, 3L)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("document_not_draft"));

            // A draft cannot be voided; it is deleted instead.
            JsonNode draftOnly = body(mvc.perform(mutate(post("/api/invoices"), session, draftBody("Draft Co", LINES, null))).andExpect(status().isCreated()));
            UUID draftOnlyId = UUID.fromString(draftOnly.get("id").asText());
            mvc.perform(mutate(post("/api/invoices/" + draftOnlyId + "/void"), session, "{\"expectedVersion\":0,\"reason\":\"x\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("invalid_document_transition"));
            mvc.perform(mutate(delete("/api/invoices/" + draftOnlyId).param("expectedVersion", "1"), session, null))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("stale_document_version"));
            mvc.perform(mutate(delete("/api/invoices/" + draftOnlyId).param("expectedVersion", "0"), session, null))
                    .andExpect(status().isNoContent());
            mvc.perform(get("/api/invoices/" + draftOnlyId).cookie(session))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("document_not_found"));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM commercial_document_lines WHERE document_id=?", Integer.class, draftOnlyId));

            // Explicit zero total is stored as 0.00 but cannot be finalized.
            JsonNode zero = body(mvc.perform(mutate(post("/api/invoices"), session,
                            draftBody("Zero Co", "[{\"description\":\"Free sample\",\"quantity\":\"1\",\"unitPrice\":\"0.00\"}]", null)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.subtotal").value("0.00"))
                    .andExpect(jsonPath("$.total").value("0.00"))
                    .andExpect(jsonPath("$.finalizeBlockers[0]").value("total_must_be_positive")));
            mvc.perform(mutate(post("/api/invoices/" + zero.get("id").asText() + "/finalize"), session, "{\"expectedVersion\":0}"))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("total_must_be_positive"));
            JsonNode noCounterparty = body(mvc.perform(mutate(post("/api/invoices"), session, draftBody(null, LINES, null))).andExpect(status().isCreated()));
            mvc.perform(mutate(post("/api/invoices/" + noCounterparty.get("id").asText() + "/finalize"), session, "{\"expectedVersion\":0}"))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("counterparty_required"));
            assertEquals(2L, sequence(owner.businessId, "INVOICE"), "Rejected finalizations consume no numbers");

            // Supplier bills number independently; the next invoice continues its own sequence.
            JsonNode bill = body(mvc.perform(mutate(post("/api/bills"), session, draftBody("Steel Supplier", LINES, null)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("BILL")));
            UUID billId = UUID.fromString(bill.get("id").asText());
            mvc.perform(get("/api/invoices/" + billId).cookie(session)).andExpect(status().isNotFound());
            mvc.perform(mutate(post("/api/invoices/" + billId + "/finalize"), session, "{\"expectedVersion\":0}"))
                    .andExpect(status().isNotFound());
            mvc.perform(mutate(post("/api/bills/" + billId + "/finalize"), session, "{\"expectedVersion\":0}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.documentNumber").value("BILL-000001"));
            JsonNode second = body(mvc.perform(mutate(post("/api/invoices"), session, draftBody("Beta Retail", LINES, null))).andExpect(status().isCreated()));
            mvc.perform(mutate(post("/api/invoices/" + second.get("id").asText() + "/finalize"), session, "{\"expectedVersion\":0}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.documentNumber").value("INV-000002"));

            JsonNode list = body(mvc.perform(get("/api/invoices").cookie(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.businessId").value(owner.businessId.toString()))
                    .andExpect(jsonPath("$.kind").value("INVOICE"))
                    .andExpect(jsonPath("$.financialEffect").value("NONE"))
                    .andExpect(jsonPath("$.items.length()").value(4)));
            for (JsonNode item : list.get("items")) {
                assertTrue(item.get("lines").isNull(), "List items carry lineCount, not lines");
                assertEquals("INVOICE", item.get("kind").asText());
            }
            mvc.perform(get("/api/invoices").param("status", "VOID").cookie(session))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].documentNumber").value("INV-000001"));
            mvc.perform(get("/api/bills").cookie(session))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
            mvc.perform(get("/api/invoices").param("status", "ISSUED").cookie(session)).andExpect(status().isBadRequest());

            // Audit events are attributed to the authenticated actor and business and never contain counterparty names.
            List<Map<String, Object>> audits = admin().queryForList(
                    "SELECT event_type, actor_user_id, business_id, CAST(metadata AS text) AS metadata FROM security_audit_events "
                            + "WHERE target_type='commercial_document' AND target_id=? ORDER BY occurred_at", invoiceId.toString());
            assertEquals(List.of("COMMERCIAL_DOCUMENT_CREATED", "COMMERCIAL_DOCUMENT_UPDATED", "COMMERCIAL_DOCUMENT_FINALIZED", "COMMERCIAL_DOCUMENT_VOIDED"),
                    audits.stream().map(row -> row.get("event_type")).toList());
            for (Map<String, Object> audit : audits) {
                assertEquals(owner.user.getId(), audit.get("actor_user_id"));
                assertEquals(owner.businessId, audit.get("business_id"));
                assertFalse(String.valueOf(audit.get("metadata")).contains("Acme"));
            }

            assertEquals(before, snapshot(), "F04 must not change any financial, score, project, document or configuration row");
            assertEquals(List.of(), probedWrites(), "F04 must issue zero write statements on financial, score or project tables");
        } finally {
            dropWriteProbe();
        }
    }

    @Test
    void rolesCsrfTenantIsolationAndAuthenticatedAttributionAreEnforced() throws Exception {
        var ownerA = fixture(MembershipRole.OWNER);
        var accountantA = member(ownerA.businessId, MembershipRole.ACCOUNTANT);
        var managerA = member(ownerA.businessId, MembershipRole.MANAGER);
        var viewerA = member(ownerA.businessId, MembershipRole.VIEWER);
        var ownerB = fixture(MembershipRole.OWNER);
        var sessionOwnerA = login(ownerA);
        var sessionAccountant = login(accountantA);
        var sessionManager = login(managerA);
        var sessionViewer = login(viewerA);
        var sessionOwnerB = login(ownerB);

        mvc.perform(get("/api/invoices")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/bills")).andExpect(status().isUnauthorized());

        // ACCOUNTANT creates; OWNER finalizes: each actor is attributed from the session, not the request.
        JsonNode created = body(mvc.perform(mutate(post("/api/bills"), sessionAccountant, draftBody("Supplier A", LINES, null)))
                .andExpect(status().isCreated()));
        UUID billId = UUID.fromString(created.get("id").asText());
        assertEquals(accountantA.user.getId().toString(), created.get("createdByUserId").asText());
        assertEquals(ownerA.businessId.toString(), created.get("businessId").asText());
        JsonNode finalized = body(mvc.perform(mutate(post("/api/bills/" + billId + "/finalize"), sessionOwnerA, "{\"expectedVersion\":0}"))
                .andExpect(status().isOk()));
        assertEquals(ownerA.user.getId().toString(), finalized.get("finalizedByUserId").asText());
        assertEquals(ownerA.user.getId().toString(), finalized.get("updatedByUserId").asText());
        assertEquals(accountantA.user.getId().toString(), finalized.get("createdByUserId").asText());
        JsonNode draft = body(mvc.perform(mutate(post("/api/invoices"), sessionAccountant, draftBody("Customer A", LINES, null)))
                .andExpect(status().isCreated()));
        UUID invoiceId = UUID.fromString(draft.get("id").asText());

        // Client-controlled authority fields are rejected outright.
        for (String field : List.of("businessId", "actorUserId", "createdBy", "finalizedBy", "status", "documentNumber", "subtotal", "total", "financialEffect")) {
            mvc.perform(mutate(post("/api/invoices"), sessionAccountant, "{\"lines\":[],\"" + field + "\":\"" + ownerB.businessId + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors." + field).value("unknown_field"));
        }

        // MANAGER and VIEWER read and preview, but every mutation is forbidden.
        for (Cookie readOnly : List.of(sessionManager, sessionViewer)) {
            mvc.perform(get("/api/invoices").cookie(readOnly)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].id").value(invoiceId.toString()));
            mvc.perform(get("/api/invoices/" + invoiceId).cookie(readOnly)).andExpect(status().isOk());
            mvc.perform(get("/api/bills/" + billId).cookie(readOnly)).andExpect(status().isOk());
            mvc.perform(mutate(post("/api/invoices/preview"), readOnly, "{\"lines\":" + LINES + "}")).andExpect(status().isOk());
            mvc.perform(mutate(post("/api/invoices"), readOnly, draftBody("Denied", LINES, null))).andExpect(status().isForbidden());
            mvc.perform(mutate(put("/api/invoices/" + invoiceId), readOnly, draftBody("Denied", LINES, 0L))).andExpect(status().isForbidden());
            mvc.perform(mutate(delete("/api/invoices/" + invoiceId).param("expectedVersion", "0"), readOnly, null)).andExpect(status().isForbidden());
            mvc.perform(mutate(post("/api/invoices/" + invoiceId + "/finalize"), readOnly, "{\"expectedVersion\":0}")).andExpect(status().isForbidden());
            mvc.perform(mutate(post("/api/bills/" + billId + "/void"), readOnly, "{\"expectedVersion\":1,\"reason\":\"x\"}")).andExpect(status().isForbidden());
        }

        // CSRF is required for every mutation, including preview.
        mvc.perform(post("/api/invoices").cookie(sessionOwnerA).contentType("application/json").content(draftBody("No CSRF", LINES, null)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/invoices/" + invoiceId + "/finalize").cookie(sessionOwnerA).contentType("application/json").content("{\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/invoices/preview").cookie(sessionOwnerA).contentType("application/json").content("{\"lines\":[]}"))
                .andExpect(status().isForbidden());

        // Business B cannot read, list or mutate business A's documents.
        mvc.perform(get("/api/invoices").cookie(sessionOwnerB)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/invoices/" + invoiceId).cookie(sessionOwnerB))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("document_not_found"));
        mvc.perform(mutate(put("/api/invoices/" + invoiceId), sessionOwnerB, draftBody("Hijack", LINES, 0L))).andExpect(status().isNotFound());
        mvc.perform(mutate(post("/api/invoices/" + invoiceId + "/finalize"), sessionOwnerB, "{\"expectedVersion\":0}")).andExpect(status().isNotFound());
        mvc.perform(mutate(post("/api/bills/" + billId + "/void"), sessionOwnerB, "{\"expectedVersion\":1,\"reason\":\"x\"}")).andExpect(status().isNotFound());
        mvc.perform(mutate(delete("/api/invoices/" + invoiceId).param("expectedVersion", "0"), sessionOwnerB, null)).andExpect(status().isNotFound());
        mvc.perform(get("/api/invoices/" + invoiceId).cookie(sessionAccountant)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.counterpartyName").value("Customer A"));
        // B's own first numbers are independent of A's.
        JsonNode bDraft = body(mvc.perform(mutate(post("/api/bills"), sessionOwnerB, draftBody("Supplier B", LINES, null))).andExpect(status().isCreated()));
        mvc.perform(mutate(post("/api/bills/" + bDraft.get("id").asText() + "/finalize"), sessionOwnerB, "{\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.documentNumber").value("BILL-000001"))
                .andExpect(jsonPath("$.businessId").value(ownerB.businessId.toString()));
        mvc.perform(get("/api/invoices/not-a-uuid").cookie(sessionOwnerA)).andExpect(status().isBadRequest());
    }

    @Test
    void concurrentFinalizationNumberingDoubleFinalizeStaleUpdatesAndRollbackAreSafe() throws Exception {
        var owner = fixture(MembershipRole.OWNER);
        var actor = owner.actor();
        List<CommercialLineRequest> lines = List.of(new CommercialLineRequest("Item", "2", "pc", "50.00"));

        // 1. Parallel finalization of eight invoices plus one bill: unique, gap-free, kind-independent numbers.
        List<UUID> invoices = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            invoices.add(service.createDraft(actor, CommercialDocumentKind.INVOICE, draft("Customer " + i, lines, null)).id());
        }
        UUID bill = service.createDraft(actor, CommercialDocumentKind.BILL, draft("Supplier", lines, null)).id();
        var start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(9)) {
            for (UUID id : invoices) {
                results.add(pool.submit(() -> {
                    start.await();
                    return service.finalizeDraft(actor, CommercialDocumentKind.INVOICE, id, new CommercialTransitionRequest(0L, null)).documentNumber();
                }));
            }
            results.add(pool.submit(() -> {
                start.await();
                return service.finalizeDraft(actor, CommercialDocumentKind.BILL, bill, new CommercialTransitionRequest(0L, null)).documentNumber();
            }));
            start.countDown();
            Set<String> numbers = new TreeSet<>();
            for (var result : results) numbers.add(result.get(60, TimeUnit.SECONDS));
            assertEquals(new TreeSet<>(List.of("BILL-000001", "INV-000001", "INV-000002", "INV-000003", "INV-000004",
                    "INV-000005", "INV-000006", "INV-000007", "INV-000008")), numbers);
        }
        assertEquals(9L, sequence(owner.businessId, "INVOICE"));
        assertEquals(2L, sequence(owner.businessId, "BILL"));

        // 2. The same document finalized concurrently: exactly one number is consumed.
        UUID twice = service.createDraft(actor, CommercialDocumentKind.INVOICE, draft("Twice", lines, null)).id();
        List<String> outcomes = race(2, () -> service.finalizeDraft(actor, CommercialDocumentKind.INVOICE, twice,
                new CommercialTransitionRequest(0L, null)).documentNumber());
        assertEquals(1, outcomes.stream().filter("INV-000009"::equals).count(), outcomes.toString());
        assertEquals(1, outcomes.stream().filter(o -> o.equals("document_already_finalized") || o.equals("stale_document_version")).count(), outcomes.toString());
        assertEquals(10L, sequence(owner.businessId, "INVOICE"));

        // 3. Concurrent edits with the same version: one wins, the other is a stale conflict; no lost update.
        UUID edited = service.createDraft(actor, CommercialDocumentKind.BILL, draft("Original", lines, null)).id();
        List<String> edits = race(2, () -> {
            String name = "Writer " + Thread.currentThread().threadId();
            service.updateDraft(actor, CommercialDocumentKind.BILL, edited, draft(name, lines, 0L));
            return name;
        });
        List<String> winners = edits.stream().filter(e -> e.startsWith("Writer ")).toList();
        assertEquals(1, winners.size(), edits.toString());
        assertTrue(edits.contains("stale_document_version"), edits.toString());
        var current = service.get(actor, CommercialDocumentKind.BILL, edited);
        assertEquals(winners.getFirst(), current.counterpartyName());
        assertEquals(1L, current.version());

        // 4. A rolled-back finalization also rolls back its number allocation.
        UUID rolledBack = service.createDraft(actor, CommercialDocumentKind.INVOICE, draft("Rollback", lines, null)).id();
        var template = new TransactionTemplate(transactions);
        assertThrows(IllegalStateException.class, () -> template.executeWithoutResult(status -> {
            assertEquals("INV-000010", service.finalizeDraft(actor, CommercialDocumentKind.INVOICE, rolledBack,
                    new CommercialTransitionRequest(0L, null)).documentNumber());
            throw new IllegalStateException("simulated failure after number allocation");
        }));
        assertEquals("DRAFT", service.get(actor, CommercialDocumentKind.INVOICE, rolledBack).status().name());
        assertEquals(10L, sequence(owner.businessId, "INVOICE"));
        assertEquals("INV-000010", service.finalizeDraft(actor, CommercialDocumentKind.INVOICE, rolledBack,
                new CommercialTransitionRequest(0L, null)).documentNumber(), "No gap after rollback");
        assertEquals(List.of(), jdbc.queryForList("""
                SELECT document_number FROM commercial_documents WHERE business_id=? AND document_number IS NOT NULL
                GROUP BY kind, document_number HAVING count(*) > 1""", String.class, owner.businessId));
    }

    private List<String> race(int threads, Callable<String> task) throws Exception {
        var start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return task.call();
                    } catch (CommercialException ex) {
                        return ex.code();
                    }
                }));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (var future : futures) outcomes.add(future.get(60, TimeUnit.SECONDS));
            return outcomes;
        }
    }

    private static CommercialDocumentDraftRequest draft(String counterparty, List<CommercialLineRequest> lines, Long version) {
        return new CommercialDocumentDraftRequest(counterparty, "2026-10-28", null, null, lines, version);
    }

    private long sequence(UUID businessId, String kind) {
        return jdbc.queryForObject("SELECT next_value FROM commercial_number_sequences WHERE business_id=? AND kind=?",
                Long.class, businessId, kind);
    }

    private Map<String, String> snapshot() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : ISOLATED_TABLES) result.put(table, jdbc.queryForObject(
                "SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text), '[]'::jsonb)::text FROM " + table + " t", String.class));
        return result;
    }

    /** Test-only statement probes detect writes even when they would leave identical rows. */
    private void installWriteProbe() throws Exception {
        try (Connection connection = POSTGRES.getPostgresDatabase().getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE f04_it_financial_writes (table_name text NOT NULL, operation text NOT NULL)");
            sql.execute("""
                    CREATE FUNCTION f04_it_capture_write() RETURNS trigger LANGUAGE plpgsql
                    SECURITY DEFINER SET search_path = pg_catalog, public AS $$
                    BEGIN INSERT INTO public.f04_it_financial_writes VALUES (TG_TABLE_NAME, TG_OP); RETURN NULL; END $$
                    """);
            for (String table : List.of("monthly_records", "score_results", "document_financial_contributions",
                    "projects", "project_budget_versions", "project_budget_lines")) {
                sql.execute("CREATE TRIGGER f04_it_write_probe AFTER INSERT OR UPDATE OR DELETE OR TRUNCATE ON " + table
                        + " FOR EACH STATEMENT EXECUTE FUNCTION f04_it_capture_write()");
            }
        }
    }

    /** Superuser view for test-only tables and audit rows outside the runtime role's grants. */
    private static JdbcTemplate admin() {
        return new JdbcTemplate(POSTGRES.getPostgresDatabase());
    }

    private List<String> probedWrites() {
        return admin().queryForList("SELECT table_name || ' ' || operation FROM f04_it_financial_writes", String.class);
    }

    private void dropWriteProbe() throws Exception {
        try (Connection connection = POSTGRES.getPostgresDatabase().getConnection(); Statement sql = connection.createStatement()) {
            for (String table : List.of("monthly_records", "score_results", "document_financial_contributions",
                    "projects", "project_budget_versions", "project_budget_lines")) {
                sql.execute("DROP TRIGGER IF EXISTS f04_it_write_probe ON " + table);
            }
            sql.execute("DROP FUNCTION IF EXISTS f04_it_capture_write()");
            sql.execute("DROP TABLE IF EXISTS f04_it_financial_writes");
        }
    }
}
