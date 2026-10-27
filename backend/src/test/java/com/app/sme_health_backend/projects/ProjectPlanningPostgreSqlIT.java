package com.app.sme_health_backend.projects;

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
import com.app.sme_health_backend.projects.dto.ProjectBudgetApprovalRequest;
import com.app.sme_health_backend.projects.dto.ProjectBudgetLineRequest;
import com.app.sme_health_backend.projects.dto.ProjectBudgetRequest;
import com.app.sme_health_backend.projects.dto.ProjectCreateRequest;
import com.app.sme_health_backend.projects.model.ProjectCostCategory;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end PostgreSQL + MockMvc + Session + CSRF integration tests for F05 Phase A
 * Project / Job / Order Planning & Budgeting Foundation.
 */
@SpringBootTest(properties = {"app.ai.enabled=true", "app.ai.provider=mock", "app.ai.demo-enabled=true"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class ProjectPlanningPostgreSqlIT extends DisposablePostgres {

    @Autowired AppUserRepository users;
    @Autowired BusinessRepository businesses;
    @Autowired BusinessMembershipRepository memberships;
    @Autowired BusinessProfileRepository profiles;
    @Autowired MonthlyRecordRepository records;
    @Autowired ScoringService scoring;
    @Autowired ProjectPlanningService planningService;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwordEncoder;

    static final String FIXTURE_PASSWORD = "F05-synthetic-password-2026!";

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
        user.setEmail("f05." + role.name().toLowerCase() + "." + UUID.randomUUID() + "@example.com");
        user.setFullName("Synthetic F05 " + role.name());
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
        select(session, f.businessId);
        return session;
    }

    void select(Cookie session, UUID businessId) throws Exception {
        mvc.perform(post("/api/businesses/active")
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("businessId", businessId))))
                .andExpect(status().isOk());
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

    @Test
    void fullProjectBudgetRevisionApprovalSupersessionSummaryAndNonGoalIsolationWorkEndToEnd() throws Exception {
        var owner = fixture(MembershipRole.OWNER);
        var session = login(owner);

        // Seed a MonthlyRecord and ScoreResult to prove F05 never modifies them
        var baselineRecord = new MonthlyRecord();
        baselineRecord.setUserId(owner.businessId);
        baselineRecord.setMonth("2026-10");
        baselineRecord.setRevenue(new BigDecimal("500000.00"));
        baselineRecord.setOperatingExpenses(new BigDecimal("300000.00"));
        baselineRecord.setCashInflow(new BigDecimal("480000.00"));
        baselineRecord.setCashOutflow(new BigDecimal("290000.00"));
        baselineRecord.setCashBalanceEom(new BigDecimal("190000.00"));
        baselineRecord.setUpdatedAt(LocalDateTime.now());
        records.saveAndFlush(baselineRecord);
        var baselineScore = scoring.calculateAndSaveScore(owner.businessId, "2026-10");

        // 1. Create a PROJECT with lowercase code -> normalized to uppercase
        String createBody = """
                {
                  "code": " prj-2026-001 ",
                  "name": "Solar Inverter Fitout",
                  "kind": "PROJECT",
                  "description": "15kW hybrid commercial installation",
                  "startDate": "2026-11-01",
                  "targetEndDate": "2026-12-15"
                }
                """;
        var createdRes = mvc.perform(post("/api/projects")
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("PRJ-2026-001"))
                .andExpect(jsonPath("$.kind").value("PROJECT"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.baseCurrency").value("PKR"))
                .andExpect(jsonPath("$.currencyBasis").value("APPLICATION_FIXED_PKR"))
                .andExpect(jsonPath("$.planningOnly").value(true))
                .andExpect(jsonPath("$.canDeleteDraftProject").value(true))
                .andReturn().getResponse();
        JsonNode createdProject = json.readTree(createdRes.getContentAsString());
        UUID projectId = UUID.fromString(createdProject.get("id").asText());

        // Duplicate code in same business returns 409 duplicate_project_code
        mvc.perform(post("/api/projects")
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("duplicate_project_code"));

        // 2. Update metadata (version 0 -> 1)
        String updateBody = """
                {
                  "code": "PRJ-2026-001",
                  "name": "Solar Inverter Fitout — Phase 1",
                  "kind": "JOB",
                  "description": "Updated scope",
                  "startDate": "2026-11-01",
                  "targetEndDate": "2026-12-20",
                  "expectedVersion": 0
                }
                """;
        mvc.perform(put("/api/projects/" + projectId)
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("JOB"))
                .andExpect(jsonPath("$.version").value(1));

        // Stale expectedVersion returns 409 stale_project_version
        mvc.perform(put("/api/projects/" + projectId)
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("stale_project_version"));

        // 3. Create Revision 1 DRAFT budget (project version 1 -> 2), then discard it (project version 2 -> 3)
        String rev1DraftBody = """
                {
                  "plannedRevenue": "250000",
                  "expectedProjectVersion": 1,
                  "lines": [
                    {
                      "category": "MATERIAL",
                      "description": "Hybrid Inverter",
                      "quantity": "1",
                      "unit": "unit",
                      "unitCost": "120000"
                    }
                  ]
                }
                """;
        var discardedDraftRes = mvc.perform(post("/api/projects/" + projectId + "/budgets")
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content(rev1DraftBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse();
        UUID discardedDraftId = UUID.fromString(json.readTree(discardedDraftRes.getContentAsString()).get("id").asText());

        mvc.perform(delete("/api/projects/" + projectId + "/budgets/" + discardedDraftId)
                        .param("expectedBudgetVersion", "0")
                        .param("expectedProjectVersion", "2")
                        .cookie(session)
                        .with(csrf(session)))
                .andExpect(status().isNoContent());

        // 4. Recreate Revision 1 DRAFT budget (project version 3 -> 4), update it, then approve it (project version 4 -> 5)
        String rev1RecreateBody = """
                {
                  "plannedRevenue": "250000",
                  "expectedProjectVersion": 3,
                  "lines": [
                    {
                      "category": "MATERIAL",
                      "description": "Hybrid Inverter",
                      "quantity": "1",
                      "unit": "unit",
                      "unitCost": "110000"
                    },
                    {
                      "category": "LABOUR",
                      "description": "Installation crew",
                      "quantity": "40",
                      "unit": "hour",
                      "unitCost": "1000"
                    }
                  ]
                }
                """;
        var rev1Res = mvc.perform(post("/api/projects/" + projectId + "/budgets")
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content(rev1RecreateBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.plannedCost").value("150000.00"))
                .andExpect(jsonPath("$.plannedMargin").value("100000.00"))
                .andExpect(jsonPath("$.plannedMarginPct").value("0.4"))
                .andReturn().getResponse();
        UUID rev1Id = UUID.fromString(json.readTree(rev1Res.getContentAsString()).get("id").asText());

        // Edit Revision 1 DRAFT (budget version 0 -> 1, project version 4 -> 5)
        String rev1UpdateBody = """
                {
                  "plannedRevenue": "250000",
                  "expectedBudgetVersion": 0,
                  "lines": [
                    {
                      "category": "MATERIAL",
                      "description": "Hybrid Inverter + Mounts",
                      "quantity": "1",
                      "unit": "lot",
                      "unitCost": "135000"
                    },
                    {
                      "category": "LABOUR",
                      "description": "Installation crew",
                      "quantity": "40",
                      "unit": "hour",
                      "unitCost": "1000"
                    }
                  ]
                }
                """;
        mvc.perform(put("/api/projects/" + projectId + "/budgets/" + rev1Id)
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content(rev1UpdateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.plannedCost").value("175000.00"))
                .andExpect(jsonPath("$.plannedMargin").value("75000.00"))
                .andExpect(jsonPath("$.plannedMarginPct").value("0.3"));

        // Approve Revision 1 (budget version 1 -> 2, project version 5 -> 6)
        mvc.perform(post("/api/projects/" + projectId + "/budgets/" + rev1Id + "/approve")
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedProjectVersion": 5,
                                  "expectedBudgetVersion": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.approvedBy").value(owner.user.getId().toString()));

        // Approved budget is now immutable: PUT and DELETE return 409 budget_immutable
        mvc.perform(put("/api/projects/" + projectId + "/budgets/" + rev1Id)
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content(rev1UpdateBody.replace("\"expectedBudgetVersion\": 0", "\"expectedBudgetVersion\": 2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("budget_immutable"));

        mvc.perform(delete("/api/projects/" + projectId + "/budgets/" + rev1Id)
                        .param("expectedBudgetVersion", "2")
                        .param("expectedProjectVersion", "6")
                        .cookie(session)
                        .with(csrf(session)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("budget_immutable"));

        // Deleting a project that has approved budget history is forbidden even while project status is still DRAFT
        mvc.perform(delete("/api/projects/" + projectId)
                        .param("expectedVersion", "6")
                        .cookie(session)
                        .with(csrf(session)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("project_delete_forbidden"));

        // 5. Verify Planning Summary endpoint
        mvc.perform(get("/api/projects/" + projectId + "/planning-summary").cookie(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.planningOnly").value(true))
                .andExpect(jsonPath("$.baseCurrency").value("PKR"))
                .andExpect(jsonPath("$.currencyBasis").value("APPLICATION_FIXED_PKR"))
                .andExpect(jsonPath("$.actualFinancialsStatus").value("NOT_IMPLEMENTED_PHASE_A"))
                .andExpect(jsonPath("$.activeApprovedBudget.revision").value(1))
                .andExpect(jsonPath("$.activeApprovedBudget.plannedRevenue").value("250000.00"))
                .andExpect(jsonPath("$.activeApprovedBudget.plannedCost").value("175000.00"))
                .andExpect(jsonPath("$.activeApprovedBudget.plannedMargin").value("75000.00"))
                .andExpect(jsonPath("$.activeApprovedBudget.plannedMarginPct").value("0.3"))
                .andExpect(jsonPath("$.activeApprovedBudget.categorySubtotals.MATERIAL").value("135000.00"))
                .andExpect(jsonPath("$.activeApprovedBudget.categorySubtotals.LABOUR").value("40000.00"))
                .andExpect(jsonPath("$.latestDraftBudget").isEmpty())
                .andExpect(jsonPath("$.revisionCount").value(1))
                .andExpect(jsonPath("$.supersededCount").value(0));

        // 6. Transition project DRAFT -> ACTIVE (project version 6 -> 7)
        mvc.perform(post("/api/projects/" + projectId + "/transition")
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetStatus": "ACTIVE",
                                  "reason": "Customer signed order",
                                  "expectedVersion": 6
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(7));

        // 7. Create Revision 2 DRAFT (project version 7 -> 8)
        String rev2DraftBody = """
                {
                  "plannedRevenue": "260000",
                  "expectedProjectVersion": 7,
                  "lines": [
                    {
                      "category": "MATERIAL",
                      "description": "Hybrid Inverter + Mounts + Surge Kit",
                      "quantity": "1",
                      "unit": "lot",
                      "unitCost": "156000"
                    },
                    {
                      "category": "LABOUR",
                      "description": "Installation crew",
                      "quantity": "52",
                      "unit": "hour",
                      "unitCost": "1000"
                    }
                  ]
                }
                """;
        var rev2Res = mvc.perform(post("/api/projects/" + projectId + "/budgets")
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content(rev2DraftBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse();
        UUID rev2Id = UUID.fromString(json.readTree(rev2Res.getContentAsString()).get("id").asText());

        // Attempting a second open DRAFT is rejected with 409 draft_budget_already_exists
        mvc.perform(post("/api/projects/" + projectId + "/budgets")
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content(rev2DraftBody.replace("\"expectedProjectVersion\": 7", "\"expectedProjectVersion\": 8")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("draft_budget_already_exists"));

        // Completing or archiving while an open DRAFT exists is rejected with 409 open_draft_budget_exists
        mvc.perform(post("/api/projects/" + projectId + "/transition")
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetStatus": "COMPLETED",
                                  "expectedVersion": 8
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("open_draft_budget_exists"));

        // Approve Revision 2 (project version 8 -> 9, rev2 version 0 -> 1, rev1 APPROVED -> SUPERSEDED)
        mvc.perform(post("/api/projects/" + projectId + "/budgets/" + rev2Id + "/approve")
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedProjectVersion": 8,
                                  "expectedBudgetVersion": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.revision").value(2));

        // Verify GET /api/projects/{id} and GET /api/projects/{id}/budgets show Revision 2 as APPROVED and Revision 1 as SUPERSEDED
        mvc.perform(get("/api/projects/" + projectId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(9))
                .andExpect(jsonPath("$.activeApprovedBudget.id").value(rev2Id.toString()))
                .andExpect(jsonPath("$.activeApprovedBudget.revision").value(2))
                .andExpect(jsonPath("$.activeApprovedBudget.plannedRevenue").value("260000.00"))
                .andExpect(jsonPath("$.activeApprovedBudget.plannedCost").value("208000.00"))
                .andExpect(jsonPath("$.activeApprovedBudget.plannedMargin").value("52000.00"))
                .andExpect(jsonPath("$.activeApprovedBudget.plannedMarginPct").value("0.2"))
                .andExpect(jsonPath("$.latestDraftBudget").isEmpty())
                .andExpect(jsonPath("$.revisionCount").value(2))
                .andExpect(jsonPath("$.canDeleteDraftProject").value(false));

        mvc.perform(get("/api/projects/" + projectId + "/budgets").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revision").value(2))
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[1].revision").value(1))
                .andExpect(jsonPath("$[1].status").value("SUPERSEDED"))
                .andExpect(jsonPath("$[1].supersededByBudgetId").value(rev2Id.toString()));

        // Transition ACTIVE -> COMPLETED -> ARCHIVED (project versions 9 -> 10 -> 11)
        mvc.perform(post("/api/projects/" + projectId + "/transition")
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content("{\"targetStatus\":\"COMPLETED\",\"expectedVersion\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.version").value(10));

        mvc.perform(post("/api/projects/" + projectId + "/transition")
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content("{\"targetStatus\":\"ARCHIVED\",\"expectedVersion\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.version").value(11));

        // Archived project rejects new budget revisions with 409 invalid_project_state
        mvc.perform(post("/api/projects/" + projectId + "/budgets")
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content(rev2DraftBody.replace("\"expectedProjectVersion\": 7", "\"expectedProjectVersion\": 11")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("invalid_project_state"));

        // Verify MonthlyRecord and ScoreResult were NOT touched by any F05 planning activity
        var recordAfter = records.findByUserIdAndMonth(owner.businessId, "2026-10").orElseThrow();
        assertEquals(0, new BigDecimal("500000.00").compareTo(recordAfter.getRevenue()));
        assertEquals(0, new BigDecimal("300000.00").compareTo(recordAfter.getOperatingExpenses()));
        var scoreAfter = scoring.getLatestScore(owner.businessId).orElseThrow();
        assertEquals(baselineScore.getCompositeScore(), scoreAfter.getCompositeScore());

        // Verify audit trail captured actorUserId and businessId
        Integer auditCount = jdbc.queryForObject(
                "SELECT count(*) FROM security_audit_events WHERE business_id=? AND actor_user_id=? AND event_type LIKE 'PROJECT_%'",
                Integer.class,
                owner.businessId,
                owner.user.getId()
        );
        assertNotNull(auditCount);
        assertTrue(auditCount >= 8, "Expected at least 8 PROJECT_* audit events, got " + auditCount);
    }

    @Test
    void draftProjectDeletionAndZeroRevenueBudgetBehaveDeterministically() throws Exception {
        var owner = fixture(MembershipRole.OWNER);
        var session = login(owner);

        // Create a pure DRAFT ORDER project with a zero-revenue internal budget
        var createdRes = mvc.perform(post("/api/projects")
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "ORD-INT-01",
                                  "name": "Internal Tooling Order",
                                  "kind": "ORDER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        UUID projectId = UUID.fromString(json.readTree(createdRes.getContentAsString()).get("id").asText());

        mvc.perform(post("/api/projects/" + projectId + "/budgets")
                        .cookie(session)
                        .with(csrf(session))
                        .contentType("application/json")
                        .content("""
                                {
                                  "plannedRevenue": "0",
                                  "expectedProjectVersion": 0,
                                  "lines": [
                                    {
                                      "category": "OVERHEAD",
                                      "description": "Internal lab benches",
                                      "quantity": "2",
                                      "unit": "bench",
                                      "unitCost": "15000"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plannedRevenue").value("0.00"))
                .andExpect(jsonPath("$.plannedCost").value("30000.00"))
                .andExpect(jsonPath("$.plannedMargin").value("-30000.00"))
                .andExpect(jsonPath("$.plannedMarginPct").isEmpty())
                .andExpect(jsonPath("$.unavailableReasons.plannedMarginPct").value("ZERO_PLANNED_REVENUE"));

        // Pure DRAFT project (with unapproved draft budget) can be deleted cleanly
        mvc.perform(delete("/api/projects/" + projectId)
                        .param("expectedVersion", "1")
                        .cookie(session)
                        .with(csrf(session)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/projects/" + projectId).cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("project_not_found"));
    }

    @Test
    void roleAuthorizationTenantIsolationAndClosedRequestValidationAreEnforced() throws Exception {
        var ownerA = fixture(MembershipRole.OWNER);
        var accountantA = member(ownerA.businessId, MembershipRole.ACCOUNTANT);
        var managerA = member(ownerA.businessId, MembershipRole.MANAGER);
        var viewerA = member(ownerA.businessId, MembershipRole.VIEWER);
        var ownerB = fixture(MembershipRole.OWNER);

        var sessionAccountantA = login(accountantA);
        var sessionManagerA = login(managerA);
        var sessionViewerA = login(viewerA);
        var sessionOwnerB = login(ownerB);

        // Unauthenticated requests are rejected with 401
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());

        // ACCOUNTANT can create a project and draft budget
        var createdRes = mvc.perform(post("/api/projects")
                        .cookie(sessionAccountantA)
                        .with(csrf(sessionAccountantA))
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "JOB-100",
                                  "name": "Accountant Managed Job",
                                  "kind": "JOB"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        UUID projectId = UUID.fromString(json.readTree(createdRes.getContentAsString()).get("id").asText());

        // Same code in Business B is allowed
        mvc.perform(post("/api/projects")
                        .cookie(sessionOwnerB)
                        .with(csrf(sessionOwnerB))
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "JOB-100",
                                  "name": "Business B Independent Job",
                                  "kind": "JOB"
                                }
                                """))
                .andExpect(status().isCreated());

        // Cross-business access from Business B to Business A's projectId returns 404
        mvc.perform(get("/api/projects/" + projectId).cookie(sessionOwnerB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("project_not_found"));

        // MANAGER and VIEWER can read list, detail, budgets, and planning-summary (200 OK)
        for (Cookie readOnlySession : List.of(sessionManagerA, sessionViewerA)) {
            mvc.perform(get("/api/projects").cookie(readOnlySession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].id").value(projectId.toString()));
            mvc.perform(get("/api/projects/" + projectId).cookie(readOnlySession))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/projects/" + projectId + "/budgets").cookie(readOnlySession))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/projects/" + projectId + "/planning-summary").cookie(readOnlySession))
                    .andExpect(status().isOk());

            // MANAGER and VIEWER are denied (403) on all write/transition/delete/approve operations
            mvc.perform(post("/api/projects")
                            .cookie(readOnlySession)
                            .with(csrf(readOnlySession))
                            .contentType("application/json")
                            .content("{\"code\":\"JOB-200\",\"name\":\"Denied\",\"kind\":\"JOB\"}"))
                    .andExpect(status().isForbidden());

            mvc.perform(put("/api/projects/" + projectId)
                            .cookie(readOnlySession)
                            .with(csrf(readOnlySession))
                            .contentType("application/json")
                            .content("{\"code\":\"JOB-100\",\"name\":\"Denied\",\"kind\":\"JOB\",\"expectedVersion\":0}"))
                    .andExpect(status().isForbidden());

            mvc.perform(post("/api/projects/" + projectId + "/transition")
                            .cookie(readOnlySession)
                            .with(csrf(readOnlySession))
                            .contentType("application/json")
                            .content("{\"targetStatus\":\"ACTIVE\",\"expectedVersion\":0}"))
                    .andExpect(status().isForbidden());

            mvc.perform(post("/api/projects/" + projectId + "/budgets")
                            .cookie(readOnlySession)
                            .with(csrf(readOnlySession))
                            .contentType("application/json")
                            .content("{\"plannedRevenue\":\"100\",\"expectedProjectVersion\":0,\"lines\":[{\"category\":\"LABOUR\",\"description\":\"A\",\"quantity\":\"1\",\"unit\":\"hr\",\"unitCost\":\"50\"}]}"))
                    .andExpect(status().isForbidden());

            mvc.perform(delete("/api/projects/" + projectId)
                            .param("expectedVersion", "0")
                            .cookie(readOnlySession)
                            .with(csrf(readOnlySession)))
                    .andExpect(status().isForbidden());
        }

        // Closed request validation: client-supplied businessId, currency, or actuals are rejected with 400 project_invalid
        for (String badJson : List.of(
                "{\"code\":\"JOB-999\",\"name\":\"Bad\",\"kind\":\"JOB\",\"businessId\":\"" + UUID.randomUUID() + "\"}",
                "{\"code\":\"JOB-999\",\"name\":\"Bad\",\"kind\":\"JOB\",\"currency\":\"USD\"}",
                "{\"code\":\"JOB-999\",\"name\":\"Bad\",\"kind\":\"JOB\",\"actualRevenue\":\"500\"}"
        )) {
            mvc.perform(post("/api/projects")
                            .cookie(sessionAccountantA)
                            .with(csrf(sessionAccountantA))
                            .contentType("application/json")
                            .content(badJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("project_invalid"));
        }
    }

    @Test
    void concurrentDraftCreationAndConcurrentApprovalRacesEnforceSingleDraftAndSingleApprovedBudget() throws Exception {
        var owner = fixture(MembershipRole.OWNER);
        var created = planningService.createProject(
                owner.actor(),
                new ProjectCreateRequest("PRJ-RACE-1", "Concurrency Race Project", ProjectKind.PROJECT, null, null, null)
        );
        UUID projectId = created.id();

        var budgetRequest = new ProjectBudgetRequest(
                0L,
                "100000",
                List.of(new ProjectBudgetLineRequest(ProjectCostCategory.LABOUR, "Engineer", "10", "day", "6000"))
        );

        // Race 1: Two concurrent createDraftBudget calls with expectedProjectVersion = 0
        var latch1 = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<String> task = () -> {
                latch1.await();
                try {
                    planningService.createDraftBudget(owner.actor(), projectId, budgetRequest);
                    return "created";
                } catch (ProjectException ex) {
                    return ex.code();
                }
            };
            var f1 = pool.submit(task);
            var f2 = pool.submit(task);
            latch1.countDown();
            var outcomes = List.of(f1.get(30, TimeUnit.SECONDS), f2.get(30, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("created"::equals).count(), "Exactly one draft creation must win: " + outcomes);
            assertEquals(1, outcomes.stream().filter(c -> c.equals("stale_project_version") || c.equals("draft_budget_already_exists")).count(),
                    "Loser must receive deterministic conflict code: " + outcomes);
        }

        Integer draftCount = jdbc.queryForObject(
                "SELECT count(*) FROM project_budget_versions WHERE business_id=? AND project_id=? AND status='DRAFT'",
                Integer.class,
                owner.businessId,
                projectId
        );
        assertEquals(1, draftCount);

        var openDraft = planningService.getProject(owner.actor(), projectId).latestDraftBudget();
        assertNotNull(openDraft);

        // Race 2: Two concurrent approveBudget calls on the same open DRAFT
        var approveReq = new ProjectBudgetApprovalRequest(1L, 0L);
        var latch2 = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<String> task = () -> {
                latch2.await();
                try {
                    planningService.approveBudget(owner.actor(), projectId, openDraft.id(), approveReq);
                    return "approved";
                } catch (ProjectException ex) {
                    return ex.code();
                }
            };
            var f1 = pool.submit(task);
            var f2 = pool.submit(task);
            latch2.countDown();
            var outcomes = List.of(f1.get(30, TimeUnit.SECONDS), f2.get(30, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("approved"::equals).count(), "Exactly one approval must win: " + outcomes);
            assertEquals(1, outcomes.stream().filter(c -> c.equals("stale_project_version")
                    || c.equals("stale_budget_version")
                    || c.equals("invalid_budget_transition")).count(),
                    "Loser must receive deterministic conflict code: " + outcomes);
        }

        Integer approvedCount = jdbc.queryForObject(
                "SELECT count(*) FROM project_budget_versions WHERE business_id=? AND project_id=? AND status='APPROVED'",
                Integer.class,
                owner.businessId,
                projectId
        );
        assertEquals(1, approvedCount);
    }
}
