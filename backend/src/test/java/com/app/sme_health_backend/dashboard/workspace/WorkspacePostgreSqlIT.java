package com.app.sme_health_backend.dashboard.workspace;

import com.app.sme_health_backend.configuration.dto.ConfigurationRequest;
import com.app.sme_health_backend.configuration.model.OperatingProfile;
import com.app.sme_health_backend.configuration.service.BusinessConfigurationService;
import com.app.sme_health_backend.dashboard.service.DashboardService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.entity.*;
import com.app.sme_health_backend.identity.model.*;
import com.app.sme_health_backend.identity.repository.*;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.scoring.service.ScoringService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static com.app.sme_health_backend.dashboard.workspace.WorkspaceTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual JDBC sessions, CSRF, authorization and PostgreSQL; no paid/external provider. */
@SpringBootTest(properties = {"app.ai.enabled=true", "app.ai.provider=mock", "app.ai.demo-enabled=true"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class WorkspacePostgreSqlIT extends com.app.sme_health_backend.testsupport.DisposablePostgres {
    @Autowired AppUserRepository users;
    @Autowired BusinessRepository businesses;
    @Autowired BusinessMembershipRepository memberships;
    @Autowired BusinessProfileRepository profiles;
    @Autowired MonthlyRecordRepository records;
    @Autowired ScoringService scoring;
    @Autowired DashboardService dashboards;
    @Autowired DashboardWorkspaceService workspace;
    @Autowired WorkspacePreferenceRepository preferences;
    @Autowired BusinessConfigurationService configuration;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    static final String FIXTURE_PASSWORD = "F13-synthetic-password-2026!";
    record Fixture(AppUser user, UUID businessId) {
        BusinessAccessContext actor() { return new BusinessAccessContext(user.getId(), businessId, MembershipRole.OWNER); }
    }
    Fixture fixture() {
        AppUser user = new AppUser(); user.setEmail("f13." + UUID.randomUUID() + "@example.com");
        user.setFullName("Synthetic workspace fixture"); user.setPasswordHash(passwordEncoder.encode(FIXTURE_PASSWORD));
        user.setAccountStatus(AccountStatus.ACTIVE); user.setMustChangePassword(false); user = users.saveAndFlush(user);
        UUID business = UUID.randomUUID(); businesses.saveAndFlush(new Business(business));
        memberships.saveAndFlush(new BusinessMembership(user.getId(), business, MembershipRole.OWNER, MembershipStatus.ACTIVE));
        BusinessProfile profile = new BusinessProfile(); profile.setUserId(business); profile.setBusinessType("services");
        profile.setLanguagePreference("en"); profiles.saveAndFlush(profile);
        return new Fixture(user, business);
    }
    Cookie login(Fixture f) throws Exception {
        var csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        Cookie xsrf = csrf.getCookie("XSRF-TOKEN"), initial = csrf.getCookie("FINSIGHT_SESSION"); assertNotNull(xsrf);
        var request = post("/api/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", f.user.getEmail(), "password", FIXTURE_PASSWORD)));
        if (initial != null) request.cookie(initial);
        var session = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(session); select(session, f.businessId); return session;
    }
    void select(Cookie session, UUID business) throws Exception {
        mvc.perform(post("/api/businesses/active").cookie(session).with(csrf(session)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("businessId", business)))).andExpect(status().isOk());
    }
    org.springframework.test.web.servlet.request.RequestPostProcessor csrf(Cookie session) throws Exception {
        var result = mvc.perform(get("/api/auth/csrf").cookie(session)).andExpect(status().isOk()).andReturn().getResponse();
        var xsrf = result.getCookie("XSRF-TOKEN"); assertNotNull(xsrf);
        var token = json.readValue(result.getContentAsString(), com.app.sme_health_backend.identity.dto.CsrfResponse.class);
        return request -> {
            var cookies = new ArrayList<>(Arrays.asList(request.getCookies() == null ? new Cookie[0] : request.getCookies()));
            cookies.removeIf(cookie -> cookie.getName().equals(xsrf.getName())); cookies.add(xsrf);
            request.setCookies(cookies.toArray(Cookie[]::new)); request.addHeader(token.headerName(), token.token()); return request;
        };
    }
    SaveRequest saveRequest(Response response, String hide) {
        return new SaveRequest(response.preferenceVersion(), response.registryVersion(), response.configurationVersion(),
                response.preferences().stream().map(p -> p.key().equals(hide) ? new Preference(p.key(), true, p.order(), p.size()) : p).toList());
    }
    @Test void defaultReadAndFourPresetPreviewsAreNonPersistingAndUseF02() {
        var f = fixture();
        for (var profile : OperatingProfile.values()) {
            var preview = workspace.preview(f.actor(), profile.presetKey());
            assertEquals(profile.presetKey(), preview.presetKey()); assertEquals("services", preview.recommendedPresetKey());
            assertEquals("health-score", preview.widgets().getFirst().key());
            assertEquals("f13-presets-v1", preview.presetVersion()); assertFalse(preview.personalized());
        }
        assertEquals(0, preferences.find(f.businessId, f.user.getId()).version());
        assertEquals(0, configuration.get(f.actor()).version());
        assertNull(workspace.get(f.actor()).dashboard().getScore());
        assertTrue(workspace.get(f.actor()).records().isEmpty());
    }
    @Test void saveSurvivesLoginResetIsMonotonicAndAuditUsesActualActorAndBusiness() throws Exception {
        var f = fixture(); var session = login(f); var initial = workspace.get(f.actor());
        var saved = mvc.perform(put("/api/workspace/preferences").cookie(session).with(csrf(session)).contentType("application/json")
                .content(json.writeValueAsString(saveRequest(initial, "cash-flow"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preferenceVersion").value(1)).andReturn().getResponse();
        assertFalse(json.readTree(saved.getContentAsString()).get("widgets").toString().contains("cash-flow"));
        mvc.perform(post("/api/auth/logout").cookie(session).with(csrf(session))).andExpect(status().isNoContent());
        var again = login(f);
        mvc.perform(get("/api/workspace").cookie(again)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.preferenceVersion").value(1)).andExpect(jsonPath("$.personalized").value(true));
        mvc.perform(post("/api/workspace/reset").cookie(again).with(csrf(again)).contentType("application/json")
                .content(json.writeValueAsString(new ResetRequest(1L, "f13-v1", 0L))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preferenceVersion").value(2)).andExpect(jsonPath("$.personalized").value(false));
        assertEquals("stale_version", assertThrows(WorkspaceException.class, () -> workspace.save(f.actor(), saveRequest(initial, "cash-flow"))).code());
        var audit = jdbc.queryForMap("SELECT actor_user_id,business_id FROM security_audit_events WHERE business_id=? AND event_type='WORKSPACE_PREFERENCES_RESET'", f.businessId);
        assertEquals(f.user.getId(), audit.get("actor_user_id")); assertEquals(f.businessId, audit.get("business_id"));
        assertEquals("[]", jdbc.queryForObject("SELECT overrides_json::text FROM dashboard_preferences WHERE business_id=? AND user_id=?", String.class, f.businessId, f.user.getId()));
    }
    @Test void sessionsCsrfClosedRequestsAndExistingDocumentPermissionAreEnforced() throws Exception {
        var f = fixture(); var membership = memberships.findByUserIdAndBusinessId(f.user.getId(), f.businessId).orElseThrow();
        membership.setRole(MembershipRole.VIEWER); memberships.saveAndFlush(membership); var session = login(f);
        mvc.perform(get("/api/workspace")).andExpect(status().isUnauthorized());
        String body = json.writeValueAsString(new SaveRequest(0L, "f13-v1", 0L, List.of(new Preference("document-workflow", false, 0, "wide"))));
        mvc.perform(put("/api/workspace/preferences").cookie(session).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/workspace/preferences").cookie(session).with(csrf(session)).contentType("application/json").content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("widget_forbidden"));
        mvc.perform(put("/api/workspace/preferences").cookie(session).with(csrf(session)).contentType("application/json")
                .content(body.substring(0, body.length() - 1) + ",\"businessId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("layout_invalid"));
        mvc.perform(put("/api/workspace/preferences").cookie(session).with(csrf(session)).contentType("application/json")
                .content(body.replace("document-workflow", "unknown-key"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("unknown_widget"));
        membership.setStatus(MembershipStatus.SUSPENDED); memberships.saveAndFlush(membership);
        mvc.perform(get("/api/workspace").cookie(session)).andExpect(status().isConflict());
        assertEquals(0, preferences.find(f.businessId, f.user.getId()).version());
    }
    @Test void twoUsersAndTwoBusinessesHaveIndependentPreferencesAndSessionScope() throws Exception {
        var first = fixture(); var second = fixture();
        memberships.saveAndFlush(new BusinessMembership(second.user.getId(), first.businessId, MembershipRole.VIEWER, MembershipStatus.ACTIVE));
        memberships.saveAndFlush(new BusinessMembership(first.user.getId(), second.businessId, MembershipRole.VIEWER, MembershipStatus.ACTIVE));
        workspace.save(first.actor(), saveRequest(workspace.get(first.actor()), "cash-flow"));
        var viewer = new BusinessAccessContext(second.user.getId(), first.businessId, MembershipRole.VIEWER);
        assertEquals(0, workspace.get(viewer).preferenceVersion()); assertEquals(0, workspace.get(second.actor()).preferenceVersion());
        var session = login(first); select(session, second.businessId);
        mvc.perform(get("/api/workspace").param("businessId", first.businessId.toString()).cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.dashboard.businessId").value(second.businessId.toString())).andExpect(jsonPath("$.preferenceVersion").value(0));
        select(session, first.businessId);
        mvc.perform(get("/api/workspace").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.preferenceVersion").value(1));
    }
    @Test void malformedLayoutsNestedInjectedFieldsAndRegistryConflictsDoNotWrite() throws Exception {
        var f = fixture(); var session = login(f);
        String valid = "{\"version\":0,\"registryVersion\":\"f13-v1\",\"configurationVersion\":0,\"widgets\":[{\"key\":\"cash-flow\",\"hidden\":false,\"order\":0,\"size\":\"wide\"}]}";
        for (String invalid : List.of(valid.replace("\"wide\"", "\"unsupported\""),
                valid.replace("\"order\":0", "\"order\":-1"), valid.replace("\"hidden\":false", "\"hidden\":null"),
                valid.replace("\"size\":\"wide\"", "\"size\":\"wide\",\"query\":\"arbitrary\""),
                valid.replace("cash-flow", "health-score").replace("\"hidden\":false", "\"hidden\":true"))) {
            mvc.perform(put("/api/workspace/preferences").cookie(session).with(csrf(session)).contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("layout_invalid"));
        }
        mvc.perform(put("/api/workspace/preferences").cookie(session).with(csrf(session)).contentType("application/json").content(valid.replace("f13-v1", "old-registry")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("stale_registry"));
        assertEquals(0, preferences.find(f.businessId, f.user.getId()).version());
    }
    @Test void moduleDisableReenableAndProfileChangesPreserveOverridesAndRejectStaleConfiguration() {
        var f = fixture(); var before = workspace.get(f.actor());
        assertTrue(before.aiStatus().available());
        workspace.save(f.actor(), saveRequest(before, "ai-explanation"));
        configuration.update(f.actor(), new ConfigurationRequest(OperatingProfile.PROJECT_ORDER, Map.of(), List.of("core_finance"), 0L));
        var disabled = workspace.get(f.actor());
        assertEquals("project-order", disabled.presetKey()); assertFalse(disabled.aiStatus().available());
        assertTrue(disabled.preferences().stream().noneMatch(p -> p.key().equals("ai-explanation")));
        assertEquals("stale_configuration", assertThrows(WorkspaceException.class, () -> workspace.save(f.actor(), saveRequest(before, "cash-flow"))).code());
        workspace.save(f.actor(), saveRequest(disabled, "cash-flow"));
        configuration.update(f.actor(), new ConfigurationRequest(OperatingProfile.PROJECT_ORDER, Map.of(), List.of("core_finance", "ai"), 1L));
        var restored = workspace.get(f.actor());
        assertTrue(restored.preferences().stream().filter(p -> p.key().equals("ai-explanation")).findFirst().orElseThrow().hidden());
        var reset = workspace.reset(f.actor(), new ResetRequest(restored.preferenceVersion(), restored.registryVersion(), restored.configurationVersion()));
        assertEquals("project-order", reset.presetKey()); assertFalse(reset.personalized());
        assertTrue(reset.widgets().stream().anyMatch(p -> p.key().equals("ai-explanation")));
        long configVersion = reset.configurationVersion();
        for (var profile : List.of(OperatingProfile.SERVICES, OperatingProfile.PROJECT_ORDER)) {
            configuration.update(f.actor(), new ConfigurationRequest(profile, Map.of(), List.of("core_finance", "documents"), configVersion++));
            var aiOff = workspace.get(f.actor());
            assertFalse(aiOff.aiStatus().available());
            String unhideKey = aiOff.preferences().stream().filter(Preference::hidden).findFirst().orElseThrow().key();
            List<Preference> submitted = new ArrayList<>();
            Preference moved = null;
            for (var p : aiOff.preferences()) {
                if (p.key().equals("cash-flow")) {
                    moved = new Preference(p.key(), true, aiOff.preferences().size() - 1, p.size());
                } else if (p.key().equals(unhideKey)) {
                    submitted.add(new Preference(p.key(), false, submitted.size(), p.size()));
                } else {
                    submitted.add(new Preference(p.key(), p.hidden(), submitted.size(), p.size()));
                }
            }
            assertNotNull(moved);
            submitted.add(new Preference(moved.key(), moved.hidden(), submitted.size(), moved.size()));
            var savedLayout = workspace.save(f.actor(), new SaveRequest(aiOff.preferenceVersion(), aiOff.registryVersion(), aiOff.configurationVersion(), submitted));
            assertEquals(submitted, savedLayout.preferences());
            assertEquals(submitted, workspace.get(f.actor()).preferences());
        }
    }
    @Test void concurrentFirstSavesHaveExactlyOneWinnerAndNeverDuplicateRows() throws Exception {
        var f = fixture(); var request = saveRequest(workspace.get(f.actor()), "cash-flow");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> task = () -> { start.await(); try { workspace.save(f.actor(), request); return "saved"; }
                catch (WorkspaceException e) { return e.code(); } };
            var one = executor.submit(task); var two = executor.submit(task); start.countDown();
            var outcomes = List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("saved"::equals).count()); assertEquals(1, outcomes.stream().filter("stale_version"::equals).count());
        }
        assertEquals(1, preferences.find(f.businessId, f.user.getId()).version());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM dashboard_preferences WHERE business_id=? AND user_id=?", Integer.class, f.businessId, f.user.getId()));
    }
    @Test void batchFinancialValuesMatchCanonicalScoreCashAndNullableHistoricalSnapshots() {
        var f = fixture();
        for (String month : List.of("2025-12", "2026-06", "2026-08", "2026-09")) {
            var record = new MonthlyRecord(); record.setUserId(f.businessId); record.setMonth(month);
            record.setCashInflow(new BigDecimal("12345.67")); record.setCashOutflow(new BigDecimal("10000.12"));
            record.setCashBalanceEom(new BigDecimal("2345.55")); record.setRevenue(new BigDecimal("15678.91"));
            record.setOperatingExpenses(new BigDecimal("9500.34")); record.setCogs(null); record.setUpdatedAt(LocalDateTime.now());
            records.saveAndFlush(record); scoring.calculateAndSaveScore(f.businessId, month);
        }
        var response = workspace.get(f.actor()); var canonical = dashboards.getDashboard(f.businessId);
        assertEquals(canonical.getScore().getCompositeScore(), response.dashboard().getScore().getCompositeScore());
        assertEquals(canonical.getScore().getComputedAt(), response.dashboard().getScore().getComputedAt());
        assertEquals(4, response.records().size()); assertNull(response.records().getFirst().getCogs());
        assertEquals("SNAPSHOT", response.sourceMode()); assertEquals(new BigDecimal("4691.10"), response.cashSummaryByPeriod().get("3").net());
        assertEquals(new BigDecimal("49382.68"), response.cashSummaryByPeriod().get("all").totalInflow());
        assertEquals(new BigDecimal("2345.55"), response.cashHistory().getLast().getNet());
        assertEquals(new BigDecimal("2345.55"), response.cashSummaryByPeriod().get("all").endingCash());
    }
}
