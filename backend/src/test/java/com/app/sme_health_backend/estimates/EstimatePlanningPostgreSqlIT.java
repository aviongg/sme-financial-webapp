package com.app.sme_health_backend.estimates;

import com.app.sme_health_backend.estimates.dto.EstimateConversionRequest;
import com.app.sme_health_backend.estimates.dto.EstimateCreateRequest;
import com.app.sme_health_backend.estimates.dto.EstimateRevisionRequest;
import com.app.sme_health_backend.estimates.dto.EstimateTransitionRequest;
import com.app.sme_health_backend.estimates.dto.PricingPreviewRequest;
import com.app.sme_health_backend.estimates.service.EstimateConversionService;
import com.app.sme_health_backend.estimates.service.EstimateException;
import com.app.sme_health_backend.estimates.service.EstimateService;
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
import com.app.sme_health_backend.projects.service.ProjectException;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.app.sme_health_backend.estimates.EstimateTestRevisions.line;
import static com.app.sme_health_backend.estimates.EstimateTestRevisions.request;
import static com.app.sme_health_backend.estimates.dto.EstimateLineRequest.CostGroup.*;
import static com.app.sme_health_backend.estimates.dto.PricingPreviewRequest.PricingMode.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real PostgreSQL + MockMvc + session + CSRF verification of F06 Phase B: persisted revisions, approval and
 * supersession, f06-preview-v1 parity, exact-fit F05 conversion, idempotent lineage, permissions, tenant isolation,
 * real concurrency and proof that nothing outside estimates plus the one F05 DRAFT planning budget changes.
 */
@SpringBootTest(properties = {"app.ai.enabled=true", "app.ai.provider=mock", "app.ai.demo-enabled=true"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class EstimatePlanningPostgreSqlIT extends DisposablePostgres {
    private static final String FIXTURE_PASSWORD = "F06B-synthetic-password-2026!";
    private static final List<String> ISOLATED_TABLES = List.of(
            "monthly_records", "document_financial_contributions", "score_results", "insights", "recommendations",
            "commercial_documents", "commercial_document_lines", "commercial_number_sequences", "uploaded_documents",
            "business_configuration", "dashboard_preferences");
    private static final List<String> PROBED_TABLES = List.of("monthly_records", "score_results",
            "document_financial_contributions", "commercial_documents", "commercial_document_lines", "commercial_number_sequences");
    private static final String MARKUP_CONTENT = """
            "lines":[{"costGroup":"MATERIAL","description":"Steel sheet","quantity":"2.5","unit":"sheet","unitCost":"120.125"},
                     {"costGroup":"LABOUR","description":"Fabrication","quantity":"8","unit":"hour","unitCost":"62.5"}],
            "saleableQuantity":"10","pricingMode":"TARGET_MARKUP","pricingValue":"0.25"
            """;
    private static final String PROPOSED_CONTENT = """
            "lines":[{"costGroup":"MATERIAL","description":"Steel sheet","quantity":"2.5","unit":"sheet","unitCost":"120.125"},
                     {"costGroup":"FREIGHT","description":"Delivery","quantity":"1","unit":"trip","unitCost":"99.6875"}],
            "saleableQuantity":"10","pricingMode":"PROPOSED_PRICE","pricingValue":"1500.25"
            """;
    private static final String INCOMPATIBLE_CONTENT = """
            "lines":[{"costGroup":"MATERIAL","description":"Precision resin","quantity":"1.0000001","unit":"kg","unitCost":"1000"}],
            "saleableQuantity":"7","pricingMode":"TARGET_MARGIN","pricingValue":"0.30"
            """;

    @Autowired AppUserRepository users;
    @Autowired BusinessRepository businesses;
    @Autowired BusinessMembershipRepository memberships;
    @Autowired BusinessProfileRepository profiles;
    @Autowired MonthlyRecordRepository records;
    @Autowired ScoringService scoring;
    @Autowired ProjectPlanningService projects;
    @Autowired EstimateService estimates;
    @Autowired EstimateConversionService conversions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwordEncoder;

    record Fixture(AppUser user, UUID businessId, MembershipRole role) {
        BusinessAccessContext actor() { return new BusinessAccessContext(user.getId(), businessId, role); }
    }

    Fixture fixture(MembershipRole role) {
        UUID businessId = UUID.randomUUID();
        businesses.saveAndFlush(new Business(businessId));
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(businessId);
        profile.setBusinessType("manufacturing");
        profile.setLanguagePreference("en");
        profiles.saveAndFlush(profile);
        return member(businessId, role);
    }

    Fixture member(UUID businessId, MembershipRole role) {
        AppUser user = new AppUser();
        user.setEmail("f06b." + role.name().toLowerCase() + "." + UUID.randomUUID() + "@example.com");
        user.setFullName("Synthetic F06B " + role.name());
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
        var request = post("/api/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()).contentType("application/json")
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

    MockHttpServletRequestBuilder mutate(MockHttpServletRequestBuilder builder, Cookie session, String body) throws Exception {
        builder.cookie(session).with(csrf(session));
        return body == null ? builder : builder.contentType("application/json").content(body);
    }

    JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    UUID project(Fixture owner, String code) {
        return projects.createProject(owner.actor(), new ProjectCreateRequest(code, "Project " + code, ProjectKind.ORDER, null, null, null)).id();
    }

    long projectVersion(UUID projectId) {
        return jdbc.queryForObject("SELECT version FROM projects WHERE id=?", Long.class, projectId);
    }

    @Test
    void persistedLifecycleApprovalExactConversionLineageAndFinancialIsolation() throws Exception {
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
        UUID target = project(owner, "PRJ-F06B");
        UUID secondTarget = project(owner, "PRJ-F06B-2");
        Map<String, String> before = snapshot();
        installWriteProbe();

        // Create an estimate with its first DRAFT revision; the stored result is exactly the stateless preview.
        JsonNode preview = body(mvc.perform(mutate(post("/api/estimates/preview"), session, "{" + MARKUP_CONTENT + "}")).andExpect(status().isOk()));
        JsonNode created = body(mvc.perform(mutate(post("/api/estimates"), session,
                        "{\"code\":\"est-order-1\",\"name\":\"Fabricated order\",\"description\":\"Planning only\",\"draft\":{" + MARKUP_CONTENT + "}}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.estimate.code").value("EST-ORDER-1"))
                .andExpect(jsonPath("$.estimate.status").value("ACTIVE"))
                .andExpect(jsonPath("$.estimate.planningOnly").value(true))
                .andExpect(jsonPath("$.estimate.baseCurrency").value("PKR"))
                .andExpect(jsonPath("$.estimate.version").value(1))
                .andExpect(jsonPath("$.revisions[0].revision").value(1))
                .andExpect(jsonPath("$.revisions[0].status").value("DRAFT")));
        UUID estimateId = UUID.fromString(created.at("/estimate/id").asText());
        JsonNode draft = created.at("/revisions/0");
        UUID r1 = UUID.fromString(draft.get("id").asText());
        assertEquals(preview, draft.get("result"), "Persisted output is byte-for-byte the f06-preview-v1 response");
        assertEquals("1000.390625", draft.at("/result/plannedBatchPrice").asText());
        assertEquals(owner.businessId.toString(), created.at("/estimate/businessId").asText());
        assertEquals(owner.user.getId().toString(), created.at("/estimate/createdByUserId").asText());
        mvc.perform(mutate(post("/api/estimates"), session, "{\"code\":\"EST-ORDER-1\",\"name\":\"Duplicate\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("duplicate_estimate_code"));
        mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions"), session, "{" + MARKUP_CONTENT + ",\"expectedVersion\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("draft_revision_already_exists"));

        // Edit the DRAFT; a stale revision version is rejected without a lost update.
        body(mvc.perform(mutate(put("/api/estimates/" + estimateId + "/versions/" + r1), session, "{" + PROPOSED_CONTENT + ",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.result.plannedBatchPrice").value("1500.25"))
                .andExpect(jsonPath("$.result.totalPlannedCost").value("400.00"))
                .andExpect(jsonPath("$.conversionReadiness.convertible").value(true)));
        mvc.perform(mutate(put("/api/estimates/" + estimateId + "/versions/" + r1), session, "{" + MARKUP_CONTENT + ",\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("stale_revision_version"));
        mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions/" + r1 + "/convert-to-project"), session,
                        "{\"targetProjectId\":\"" + target + "\",\"expectedProjectVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("revision_not_approved"));

        // Approve; the APPROVED content is immutable.
        mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions/" + r1 + "/approve"), session, "{\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("stale_revision_version"));
        JsonNode approved = body(mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions/" + r1 + "/approve"), session, "{\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedByUserId").value(owner.user.getId().toString())));
        assertFalse(approved.get("approvedAt").isNull());
        mvc.perform(mutate(put("/api/estimates/" + estimateId + "/versions/" + r1), session, "{" + MARKUP_CONTENT + ",\"expectedVersion\":2}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("revision_immutable"));
        mvc.perform(mutate(delete("/api/estimates/" + estimateId + "/versions/" + r1).param("expectedVersion", "2"), session, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("revision_immutable"));

        // Revision 2 replaces it; revision 1 becomes SUPERSEDED with history preserved.
        long estimateVersion = body(mvc.perform(get("/api/estimates/" + estimateId).cookie(session))).at("/estimate/version").asLong();
        JsonNode r2Body = body(mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions"), session,
                        "{" + PROPOSED_CONTENT + ",\"expectedVersion\":" + estimateVersion + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.revision").value(2)).andExpect(jsonPath("$.status").value("DRAFT")));
        UUID r2 = UUID.fromString(r2Body.get("id").asText());
        mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions/" + r2 + "/approve"), session, "{\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        JsonNode r1After = body(mvc.perform(get("/api/estimates/" + estimateId + "/versions/" + r1).cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUPERSEDED")));
        assertEquals(r2.toString(), r1After.get("supersededByVersionId").asText());
        assertEquals(approved.get("result"), r1After.get("result"), "Superseded content is unchanged");
        mvc.perform(get("/api/estimates/" + estimateId + "/versions").cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].revision").value(2)).andExpect(jsonPath("$[1].status").value("SUPERSEDED"));

        // Conversion of the SUPERSEDED revision is refused; the APPROVED one converts exactly into an F05 DRAFT budget.
        mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions/" + r1 + "/convert-to-project"), session,
                        "{\"targetProjectId\":\"" + target + "\",\"expectedProjectVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("revision_not_approved"));
        mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions/" + r2 + "/convert-to-project"), session,
                        "{\"targetProjectId\":\"" + target + "\",\"expectedProjectVersion\":5}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("stale_project_version"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM project_budget_versions WHERE project_id=?", Integer.class, target),
                "A failed conversion creates no F05 budget");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM estimate_project_conversions WHERE estimate_id = ?", Integer.class, estimateId));
        mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions/" + r2 + "/convert-to-project"), session,
                        "{\"targetProjectId\":\"" + target + "\",\"expectedProjectVersion\":0,\"plannedRevenue\":\"1\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.request").value("unknown_field"));
        JsonNode converted = body(mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions/" + r2 + "/convert-to-project"), session,
                        "{\"targetProjectId\":\"" + target + "\",\"expectedProjectVersion\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.financialEffect").value("NONE"))
                .andExpect(jsonPath("$.planningStatus").value("PLANNED_ONLY"))
                .andExpect(jsonPath("$.budget.status").value("DRAFT"))
                .andExpect(jsonPath("$.budget.planningOnly").value(true))
                .andExpect(jsonPath("$.budget.plannedRevenue").value("1500.25"))
                .andExpect(jsonPath("$.budget.plannedCost").value("400.00"))
                .andExpect(jsonPath("$.budget.plannedMargin").value("1100.25"))
                .andExpect(jsonPath("$.budget.lines[0].category").value("MATERIAL"))
                .andExpect(jsonPath("$.budget.lines[0].quantity").value("2.5"))
                .andExpect(jsonPath("$.budget.lines[0].unitCost").value("120.125"))
                .andExpect(jsonPath("$.budget.lines[1].category").value("FREIGHT"))
                .andExpect(jsonPath("$.budget.lines[1].unitCost").value("99.6875"))
                .andExpect(jsonPath("$.conversion.conversionContractVersion").value("f06-f05-exact-v1"))
                .andExpect(jsonPath("$.conversion.convertedByUserId").value(owner.user.getId().toString())));
        UUID budgetId = UUID.fromString(converted.at("/budget/id").asText());
        assertEquals("F06/EST-ORDER-1/R2/" + r2, jdbc.queryForObject("SELECT source_estimate_ref FROM project_budget_versions WHERE id=?", String.class, budgetId));
        assertEquals(List.of(target + "|" + budgetId + "|" + r2), jdbc.queryForList(
                "SELECT project_id || '|' || project_budget_version_id || '|' || estimate_version_id FROM estimate_project_conversions WHERE estimate_id = ?", String.class, estimateId));
        mvc.perform(get("/api/projects/" + target + "/budgets").cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andExpect(jsonPath("$[0].sourceEstimateRef").value("F06/EST-ORDER-1/R2/" + r2));

        // Retry with the same target replays the original result; a different target conflicts; nothing new is created.
        JsonNode replay = body(mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions/" + r2 + "/convert-to-project"), session,
                        "{\"targetProjectId\":\"" + target + "\",\"expectedProjectVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true)));
        assertEquals(converted.get("conversion"), replay.get("conversion"));
        assertEquals(budgetId.toString(), replay.at("/budget/id").asText());
        mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions/" + r2 + "/convert-to-project"), session,
                        "{\"targetProjectId\":\"" + secondTarget + "\",\"expectedProjectVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("revision_already_converted"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM project_budget_versions WHERE business_id=?", Integer.class, owner.businessId));
        mvc.perform(get("/api/estimates/" + estimateId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversions.length()").value(1))
                .andExpect(jsonPath("$.revisions[0].conversion.projectBudgetVersionId").value(budgetId.toString()))
                .andExpect(jsonPath("$.estimate.conversionCount").value(1));

        // A valid estimate whose values do not fit F05 exactly stays readable and is refused for conversion.
        JsonNode resin = body(mvc.perform(mutate(post("/api/estimates"), session, "{\"code\":\"EST-RESIN\",\"name\":\"Resin\",\"draft\":{" + INCOMPATIBLE_CONTENT + "}}"))
                .andExpect(status().isCreated()));
        UUID resinId = UUID.fromString(resin.at("/estimate/id").asText());
        UUID resinRevision = UUID.fromString(resin.at("/revisions/0/id").asText());
        assertEquals(body(mvc.perform(mutate(post("/api/estimates/preview"), session, "{" + INCOMPATIBLE_CONTENT + "}"))), resin.at("/revisions/0/result"));
        mvc.perform(mutate(post("/api/estimates/" + resinId + "/versions/" + resinRevision + "/approve"), session, "{\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/estimates/" + resinId + "/versions/" + resinRevision).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.result.plannedBatchPrice").value("1428.571571428571428571428571"))
                .andExpect(jsonPath("$.conversionReadiness.convertible").value(false))
                .andExpect(jsonPath("$.conversionReadiness.code").value("conversion_precision_unrepresentable"));
        long secondVersion = projectVersion(secondTarget);
        mvc.perform(mutate(post("/api/estimates/" + resinId + "/versions/" + resinRevision + "/convert-to-project"), session,
                        "{\"targetProjectId\":\"" + secondTarget + "\",\"expectedProjectVersion\":" + secondVersion + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("conversion_precision_unrepresentable"))
                .andExpect(jsonPath("$.estimateValid").value(true))
                .andExpect(jsonPath("$.details[0].lineNumber").value(1))
                .andExpect(jsonPath("$.details[0].field").value("lines[0].quantity"))
                .andExpect(jsonPath("$.details[0].sourceValue").value("1.0000001"))
                .andExpect(jsonPath("$.details[0].target.column").value("project_budget_lines.quantity"))
                .andExpect(jsonPath("$.details[0].target.scale").value(6))
                .andExpect(jsonPath("$.details[1].field").value("plannedBatchPrice"))
                .andExpect(jsonPath("$.details[1].reason").value("inexact_division"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM project_budget_versions WHERE project_id=?", Integer.class, secondTarget));
        assertEquals(secondVersion, projectVersion(secondTarget), "A precision rejection has zero F05 side effects");

        // The target must accept planning: an open F05 draft, an archived project, or another business's project are refused.
        UUID other = UUID.fromString(body(mvc.perform(mutate(post("/api/estimates"), session,
                "{\"code\":\"EST-OTHER\",\"name\":\"Other\",\"draft\":{" + PROPOSED_CONTENT + "}}"))).at("/revisions/0/id").asText());
        UUID otherEstimate = UUID.fromString(jdbc.queryForObject("SELECT estimate_id::text FROM estimate_versions WHERE id=?", String.class, other));
        mvc.perform(mutate(post("/api/estimates/" + otherEstimate + "/versions/" + other + "/approve"), session, "{\"expectedVersion\":0}")).andExpect(status().isOk());
        mvc.perform(mutate(post("/api/estimates/" + otherEstimate + "/versions/" + other + "/convert-to-project"), session,
                        "{\"targetProjectId\":\"" + target + "\",\"expectedProjectVersion\":" + projectVersion(target) + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("draft_budget_already_exists"));
        UUID archived = project(owner, "PRJ-ARCHIVED");
        mvc.perform(mutate(post("/api/projects/" + archived + "/transition"), session, "{\"targetStatus\":\"ARCHIVED\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        mvc.perform(mutate(post("/api/estimates/" + otherEstimate + "/versions/" + other + "/convert-to-project"), session,
                        "{\"targetProjectId\":\"" + archived + "\",\"expectedProjectVersion\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("invalid_project_state"));
        var foreign = fixture(MembershipRole.OWNER);
        UUID foreignProject = project(foreign, "PRJ-FOREIGN");
        mvc.perform(mutate(post("/api/estimates/" + otherEstimate + "/versions/" + other + "/convert-to-project"), session,
                        "{\"targetProjectId\":\"" + foreignProject + "\",\"expectedProjectVersion\":0}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("project_not_found"));

        // Archive and delete rules.
        UUID draftOnly = UUID.fromString(body(mvc.perform(mutate(post("/api/estimates"), session,
                "{\"code\":\"EST-DRAFT\",\"name\":\"Draft only\",\"draft\":{" + PROPOSED_CONTENT + "}}"))).at("/estimate/id").asText());
        mvc.perform(mutate(post("/api/estimates/" + draftOnly + "/archive"), session, "{\"expectedVersion\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("open_draft_revision_exists"));
        mvc.perform(mutate(delete("/api/estimates/" + estimateId).param("expectedVersion", "99"), session, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("estimate_delete_forbidden"));
        mvc.perform(mutate(delete("/api/estimates/" + draftOnly).param("expectedVersion", "1"), session, null)).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM estimate_versions WHERE estimate_id=?", Integer.class, draftOnly));
        long current = body(mvc.perform(get("/api/estimates/" + estimateId).cookie(session))).at("/estimate/version").asLong();
        mvc.perform(mutate(post("/api/estimates/" + estimateId + "/archive"), session, "{\"expectedVersion\":" + current + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estimate.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.revisions.length()").value(2));
        mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions"), session, "{" + PROPOSED_CONTENT + ",\"expectedVersion\":" + (current + 1) + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("estimate_archived"));
        mvc.perform(get("/api/estimates").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.code == 'EST-ORDER-1')]").isEmpty());
        mvc.perform(get("/api/estimates").param("includeArchived", "true").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.code == 'EST-ORDER-1')].status").value("ARCHIVED"));
        mvc.perform(get("/api/estimates/preview").cookie(session)).andExpect(status().isBadRequest());

        // Only estimate rows and the one F05 DRAFT budget changed; financial, score and commercial rows are untouched.
        assertEquals(before, snapshot(), "F06 must not change any financial, score, commercial or configuration row");
        try (Connection connection = POSTGRES.getPostgresDatabase().getConnection(); Statement sql = connection.createStatement();
             var rows = sql.executeQuery("SELECT count(*) FROM f06b_it_financial_writes")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1),
                    "Zero INSERT/UPDATE/DELETE/TRUNCATE statements on records, scores, contributions or commercial documents");
        }
        assertEquals(List.of("DRAFT|PRJ-F06B"), jdbc.queryForList("""
                SELECT v.status || '|' || p.code FROM project_budget_versions v JOIN projects p ON p.id = v.project_id
                WHERE v.business_id = ?""", String.class, owner.businessId));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM project_budget_versions WHERE status <> 'DRAFT' AND business_id=?", Integer.class, owner.businessId));
        assertTrue(jdbc.queryForList("SELECT DISTINCT event_type FROM security_audit_events WHERE business_id=?", String.class, owner.businessId)
                .containsAll(List.of("ESTIMATE_CREATED", "ESTIMATE_REVISION_UPDATED", "ESTIMATE_REVISION_APPROVED", "ESTIMATE_REVISION_CONVERTED",
                        "ESTIMATE_ARCHIVED", "ESTIMATE_DELETED", "PROJECT_BUDGET_CREATED")));
    }

    @Test
    void rolesTenantIsolationAndServerAttribution() throws Exception {
        var owner = fixture(MembershipRole.OWNER);
        var accountant = member(owner.businessId, MembershipRole.ACCOUNTANT);
        var manager = member(owner.businessId, MembershipRole.MANAGER);
        var viewer = member(owner.businessId, MembershipRole.VIEWER);
        var outsider = fixture(MembershipRole.OWNER);
        UUID target = project(owner, "PRJ-ROLES");

        Cookie accountantSession = login(accountant);
        JsonNode created = body(mvc.perform(mutate(post("/api/estimates"), accountantSession,
                "{\"code\":\"EST-ROLE\",\"name\":\"Role check\",\"draft\":{" + PROPOSED_CONTENT + "}}")).andExpect(status().isCreated()));
        UUID estimateId = UUID.fromString(created.at("/estimate/id").asText());
        UUID revisionId = UUID.fromString(created.at("/revisions/0/id").asText());
        assertEquals(accountant.user.getId().toString(), created.at("/estimate/createdByUserId").asText(), "Actor comes from the session");
        assertEquals(owner.businessId.toString(), created.at("/estimate/businessId").asText(), "Business comes from the session");
        mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions/" + revisionId + "/approve"), accountantSession, "{\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approvedByUserId").value(accountant.user.getId().toString()));

        for (Fixture readOnly : List.of(manager, viewer)) {
            Cookie session = login(readOnly);
            mvc.perform(get("/api/estimates").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
            mvc.perform(get("/api/estimates/" + estimateId).cookie(session)).andExpect(status().isOk());
            mvc.perform(get("/api/estimates/" + estimateId + "/versions/" + revisionId).cookie(session)).andExpect(status().isOk());
            mvc.perform(mutate(post("/api/estimates/preview"), session, "{" + PROPOSED_CONTENT + "}")).andExpect(status().isOk());
            mvc.perform(mutate(post("/api/estimates"), session, "{\"code\":\"X\",\"name\":\"X\"}")).andExpect(status().isForbidden());
            mvc.perform(mutate(put("/api/estimates/" + estimateId), session, "{\"code\":\"X\",\"name\":\"X\",\"expectedVersion\":0}")).andExpect(status().isForbidden());
            mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions"), session, "{" + PROPOSED_CONTENT + ",\"expectedVersion\":2}")).andExpect(status().isForbidden());
            mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions/" + revisionId + "/approve"), session, "{\"expectedVersion\":1}")).andExpect(status().isForbidden());
            mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions/" + revisionId + "/convert-to-project"), session,
                    "{\"targetProjectId\":\"" + target + "\",\"expectedProjectVersion\":0}")).andExpect(status().isForbidden());
            mvc.perform(mutate(post("/api/estimates/" + estimateId + "/archive"), session, "{\"expectedVersion\":2}")).andExpect(status().isForbidden());
            mvc.perform(mutate(delete("/api/estimates/" + estimateId).param("expectedVersion", "2"), session, null)).andExpect(status().isForbidden());
        }
        // CSRF remains mandatory for mutations.
        Cookie ownerSession = login(owner);
        mvc.perform(post("/api/estimates").cookie(ownerSession).contentType("application/json").content("{\"code\":\"X\",\"name\":\"X\"}"))
                .andExpect(status().isForbidden());

        Cookie outsiderSession = login(outsider);
        mvc.perform(get("/api/estimates").cookie(outsiderSession)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/estimates/" + estimateId).cookie(outsiderSession)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("estimate_not_found"));
        mvc.perform(get("/api/estimates/" + estimateId + "/versions/" + revisionId).cookie(outsiderSession)).andExpect(status().isNotFound());
        mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions/" + revisionId + "/convert-to-project"), outsiderSession,
                "{\"targetProjectId\":\"" + target + "\",\"expectedProjectVersion\":0}")).andExpect(status().isNotFound());
        // The owner cannot target the outsider's project either.
        UUID foreignProject = project(outsider, "PRJ-OUT");
        mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions/" + revisionId + "/convert-to-project"), ownerSession,
                        "{\"targetProjectId\":\"" + foreignProject + "\",\"expectedProjectVersion\":0}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("project_not_found"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM project_budget_versions WHERE business_id IN (?, ?)", Integer.class,
                owner.businessId, outsider.businessId));
        mvc.perform(mutate(post("/api/estimates/" + estimateId + "/versions/" + revisionId + "/convert-to-project"), ownerSession,
                        "{\"targetProjectId\":\"" + target + "\",\"expectedProjectVersion\":0}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.conversion.convertedByUserId").value(owner.user.getId().toString()));
        assertEquals(owner.user.getId(), jdbc.queryForObject("SELECT created_by FROM project_budget_versions WHERE project_id=?", UUID.class, target));
    }

    @Test
    void realConcurrencyKeepsOneDraftOneApprovalNoLostUpdateAndOneConversion() throws Exception {
        var owner = fixture(MembershipRole.OWNER);
        BusinessAccessContext actor = owner.actor();
        PricingPreviewRequest content = request(PROPOSED_PRICE, "1500.25", "10", line(MATERIAL, "2.5", "120.125"), line(FREIGHT, "1", "99.6875"));
        UUID estimateId = estimates.create(actor, new EstimateCreateRequest("EST-RACE", "Race", null, null)).estimate().id();

        // 1. Simultaneous DRAFT creation: exactly one revision.
        List<String> drafts = race(4, () -> estimates.createRevision(actor, estimateId, new EstimateRevisionRequest(content, 0L)).status().name());
        assertEquals(1, drafts.stream().filter("DRAFT"::equals).count(), drafts.toString());
        assertTrue(drafts.stream().filter(outcome -> !outcome.equals("DRAFT"))
                .allMatch(outcome -> outcome.equals("stale_estimate_version") || outcome.equals("draft_revision_already_exists")), drafts.toString());
        UUID draft = jdbc.queryForObject("SELECT id FROM estimate_versions WHERE estimate_id=?", UUID.class, estimateId);

        // 2. Stale concurrent edits: one winner, no lost update.
        PricingPreviewRequest edited = request(PROPOSED_PRICE, "1600", "10", line(MATERIAL, "2.5", "120.125"), line(FREIGHT, "1", "99.6875"));
        List<String> edits = race(2, () -> estimates.updateRevision(actor, estimateId, draft, new EstimateRevisionRequest(edited, 0L)).status().name());
        assertEquals(List.of("DRAFT", "stale_revision_version"), edits.stream().sorted().toList());
        assertEquals(0, new BigDecimal("1600").compareTo(jdbc.queryForObject("SELECT planned_batch_price FROM estimate_versions WHERE id=?", BigDecimal.class, draft)));

        // 3. Simultaneous approvals of the same DRAFT: one APPROVED, never two.
        List<String> approvals = race(3, () -> estimates.approve(actor, estimateId, draft, new EstimateTransitionRequest(1L)).status().name());
        assertEquals(1, approvals.stream().filter("APPROVED"::equals).count(), approvals.toString());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM estimate_versions WHERE estimate_id=? AND status='APPROVED'", Integer.class, estimateId));

        // 4. Duplicate conversion attempts into one project: one budget, one lineage row; the rest replay it.
        UUID target = project(owner, "PRJ-RACE");
        List<String> converted = race(4, () -> conversions.convert(actor, estimateId, draft, new EstimateConversionRequest(target, 0L))
                .body().replayed() ? "replayed" : "created");
        assertEquals(1, converted.stream().filter("created"::equals).count(), converted.toString());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM project_budget_versions WHERE project_id=?", Integer.class, target));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM estimate_project_conversions WHERE estimate_version_id=?", Integer.class, draft));

        // 5. Two different approved estimates racing into the same project: F05 keeps exactly one DRAFT budget.
        UUID contested = project(owner, "PRJ-CONTESTED");
        List<UUID[]> sources = new ArrayList<>();
        for (String code : List.of("EST-A", "EST-B")) {
            var detail = estimates.create(actor, new EstimateCreateRequest(code, code, null, content));
            UUID revision = detail.revisions().getFirst().id();
            estimates.approve(actor, detail.estimate().id(), revision, new EstimateTransitionRequest(0L));
            sources.add(new UUID[] {detail.estimate().id(), revision});
        }
        var turn = new java.util.concurrent.atomic.AtomicInteger();
        List<String> contestedOutcomes = race(2, () -> {
            UUID[] source = sources.get(turn.getAndIncrement());
            return conversions.convert(actor, source[0], source[1], new EstimateConversionRequest(contested, 0L)).body().replayed() ? "replayed" : "created";
        });
        assertEquals(1, contestedOutcomes.stream().filter("created"::equals).count(), contestedOutcomes.toString());
        assertTrue(contestedOutcomes.stream().filter(outcome -> !outcome.equals("created"))
                .allMatch(outcome -> outcome.equals("draft_budget_already_exists") || outcome.equals("stale_project_version")), contestedOutcomes.toString());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM project_budget_versions WHERE project_id=?", Integer.class, contested));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM estimate_project_conversions WHERE project_id=?", Integer.class, contested));
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
                    } catch (EstimateException ex) {
                        return ex.code();
                    } catch (ProjectException ex) {
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

    private Map<String, String> snapshot() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : ISOLATED_TABLES) result.put(table, jdbc.queryForObject(
                "SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text), '[]'::jsonb)::text FROM " + table + " t", String.class));
        return result;
    }

    /** Test-only statement probes detect even writes that would leave identical final rows. */
    private void installWriteProbe() throws Exception {
        try (Connection connection = POSTGRES.getPostgresDatabase().getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE IF NOT EXISTS f06b_it_financial_writes (table_name text NOT NULL, operation text NOT NULL)");
            sql.execute("""
                    CREATE OR REPLACE FUNCTION f06b_it_capture_write() RETURNS trigger LANGUAGE plpgsql
                    SECURITY DEFINER SET search_path = pg_catalog, public AS $$
                    BEGIN INSERT INTO public.f06b_it_financial_writes VALUES (TG_TABLE_NAME, TG_OP); RETURN NULL; END $$
                    """);
            for (String table : PROBED_TABLES) {
                sql.execute("DROP TRIGGER IF EXISTS f06b_it_write_probe ON " + table);
                sql.execute("CREATE TRIGGER f06b_it_write_probe AFTER INSERT OR UPDATE OR DELETE OR TRUNCATE ON " + table
                        + " FOR EACH STATEMENT EXECUTE FUNCTION f06b_it_capture_write()");
            }
        }
    }
}
