package com.app.sme_health_backend.reports;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL, JDBC sessions, CSRF and existing role/business resolution; no mocked financial stores. */
@SpringBootTest(properties = {"app.ai.enabled=false", "app.ai.live-activation-approved=false", "app.ai.api-key="})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class BusinessFinancialReportPostgreSqlIT extends com.app.sme_health_backend.testsupport.DisposablePostgres {
    @Autowired AppUserRepository users;
    @Autowired BusinessRepository businesses;
    @Autowired BusinessMembershipRepository memberships;
    @Autowired BusinessProfileRepository profiles;
    @Autowired MonthlyRecordRepository records;
    @Autowired ScoringService scoring;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    static final String FIXTURE_PASSWORD = "F12B-synthetic-password-2026!";
    static final String Q1 = "{\"fromMonth\":\"2026-01\",\"toMonth\":\"2026-03\",\"language\":\"en\"}";
    static final String URL = "/api/reports/business-financial-summary";
    static final String CSV = "/api/reports/business-financial-summary.csv";

    record Fixture(AppUser user, UUID businessId) { }

    Fixture fixture(String businessName) {
        AppUser user = new AppUser(); user.setEmail("f12b." + UUID.randomUUID() + "@example.com");
        user.setFullName("Synthetic F12B fixture"); user.setPasswordHash(passwordEncoder.encode(FIXTURE_PASSWORD));
        user.setAccountStatus(AccountStatus.ACTIVE); user.setMustChangePassword(false); user = users.saveAndFlush(user);
        return new Fixture(user, business(user, businessName, MembershipRole.OWNER));
    }

    UUID business(AppUser user, String name, MembershipRole role) {
        UUID id = UUID.randomUUID(); Business business = new Business(id); business.setBusinessName(name); businesses.saveAndFlush(business);
        memberships.saveAndFlush(new BusinessMembership(user.getId(), id, role, MembershipStatus.ACTIVE));
        BusinessProfile profile = new BusinessProfile(); profile.setUserId(id); profile.setBusinessType("services");
        profile.setLanguagePreference("en"); profiles.saveAndFlush(profile);
        return id;
    }

    MonthlyRecord record(UUID business, String month, String revenue, String cogs, String inflow, String outflow, String balance, String receivables) {
        MonthlyRecord record = new MonthlyRecord(); record.setUserId(business); record.setMonth(month);
        record.setRevenue(new BigDecimal(revenue)); record.setCogs(cogs == null ? null : new BigDecimal(cogs));
        record.setCashInflow(new BigDecimal(inflow)); record.setCashOutflow(new BigDecimal(outflow));
        record.setCashBalanceEom(new BigDecimal(balance)); record.setOperatingExpenses(new BigDecimal("100.10"));
        record.setReceivablesOutstanding(receivables == null ? null : new BigDecimal(receivables)); record.setUpdatedAt(LocalDateTime.now());
        return records.saveAndFlush(record);
    }

    void seed(UUID business) {
        record(business, "2026-01", "1000.10", null, "900.00", "700.30", "5000.00", "50.00");
        record(business, "2026-03", "2000.20", "400.00", "1500.00", "1600.05", "4500.00", null);
        for (String month : List.of("2026-01", "2026-03")) scoring.calculateAndSaveScore(business, month);
    }

    Cookie login(Fixture fixture) throws Exception {
        var csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        Cookie xsrf = csrf.getCookie("XSRF-TOKEN"), initial = csrf.getCookie("FINSIGHT_SESSION"); assertNotNull(xsrf);
        var request = post("/api/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", fixture.user.getEmail(), "password", FIXTURE_PASSWORD)));
        if (initial != null) request.cookie(initial);
        var session = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(session); select(session, fixture.businessId); return session;
    }
    void select(Cookie session, UUID business) throws Exception {
        mvc.perform(post("/api/businesses/active").cookie(session).with(csrf(session)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("businessId", business)))).andExpect(status().isOk());
    }
    RequestPostProcessor csrf(Cookie session) throws Exception {
        var request = get("/api/auth/csrf"); if (session != null) request.cookie(session);
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
        var xsrf = result.getCookie("XSRF-TOKEN"); assertNotNull(xsrf);
        var token = json.readValue(result.getContentAsString(), com.app.sme_health_backend.identity.dto.CsrfResponse.class);
        return servletRequest -> {
            var cookies = new ArrayList<>(Arrays.asList(servletRequest.getCookies() == null ? new Cookie[0] : servletRequest.getCookies()));
            cookies.removeIf(cookie -> cookie.getName().equals(xsrf.getName())); cookies.add(xsrf);
            servletRequest.setCookies(cookies.toArray(Cookie[]::new)); servletRequest.addHeader(token.headerName(), token.token()); return servletRequest;
        };
    }
    ResultActions report(Cookie session, String body) throws Exception {
        return mvc.perform(post(URL).cookie(session).with(csrf(session)).contentType("application/json").content(body));
    }
    ResultActions csv(Cookie session, String body) throws Exception {
        return mvc.perform(post(CSV).cookie(session).with(csrf(session)).contentType("application/json")
                .accept("text/csv", "application/json").content(body));
    }
    tools.jackson.databind.JsonNode tree(ResultActions actions) throws Exception {
        return json.readTree(actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test void ownerReceivesExactCanonicalReportFromRealRecordsWithFlowAndSnapshotSeparation() throws Exception {
        var f = fixture("Real Traders"); seed(f.businessId); var session = login(f);
        var response = report(session, Q1).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        var tree = tree(response);
        assertEquals("business-financial-summary-v1", tree.get("schemaVersion").stringValue());
        assertEquals("Real Traders", tree.get("business").get("name").stringValue());
        assertEquals("PKR", tree.get("currency").stringValue());
        assertEquals(2, tree.get("monthlyRows").size());
        assertEquals("2026-01", tree.get("monthlyRows").get(0).get("month").stringValue());
        var flows = tree.get("periodFlows");
        assertEquals("3000.30", flows.get("revenue").get("total").stringValue());
        assertEquals("2400.00", flows.get("cashInflow").get("total").stringValue());
        assertEquals("2300.35", flows.get("cashOutflow").get("total").stringValue());
        assertEquals("99.65", flows.get("netCashMovement").get("total").stringValue());
        assertEquals("200.20", flows.get("operatingExpenses").get("total").stringValue());
        assertEquals("400.00", flows.get("cogs").get("total").stringValue());
        assertEquals("PARTIAL", flows.get("cogs").get("status").stringValue());
        var balances = tree.get("latestBalances");
        assertEquals("2026-03", balances.get("asOfMonth").stringValue());
        assertEquals("4500.00", balances.get("cashBalanceEom").stringValue(), "balance is the latest snapshot, not 9500.00");
        assertTrue(balances.get("receivablesOutstanding").isNull(), "unknown receivables must not borrow the January balance or become zero");
        for (String field : List.of("revenue", "cogs", "operatingExpenses", "cashInflow", "cashOutflow", "netCashMovement"))
            assertTrue(flows.get(field).get("total").isString(), field + " must be an exact decimal string");
        var health = tree.get("latestHealth");
        var stored = jdbc.queryForMap("SELECT month, composite_score::text AS composite, methodology_version FROM score_results WHERE user_id = ? ORDER BY month DESC LIMIT 1", f.businessId);
        assertEquals(stored.get("month"), health.get("month").stringValue());
        assertEquals(new BigDecimal(stored.get("composite").toString()), new BigDecimal(health.get("compositeScore").stringValue()));
        assertEquals("health-score-v1", health.get("methodologyVersion").stringValue());
        assertEquals(List.of("2026-02"), json.convertValue(tree.get("completeness").get("missingMonths"), List.class));
        assertTrue(json.convertValue(tree.get("limitations"), List.class).contains("MONTHS_MISSING"));
        String raw = response.andReturn().getResponse().getContentAsString();
        assertFalse(raw.contains(f.businessId.toString()), "no internal tenant identifier in the report");
        assertFalse(raw.contains(f.user.getId().toString()));
    }

    @Test void healthIsTheLatestStoredScoreInThePeriodNotAnAverageAndPeriodSelectsIt() throws Exception {
        var f = fixture("Health Traders"); seed(f.businessId); var session = login(f);
        var scores = jdbc.queryForList("SELECT month, composite_score::text AS composite FROM score_results WHERE user_id = ? ORDER BY month", f.businessId);
        assertEquals(2, scores.size());
        var latest = tree(report(session, Q1)).get("latestHealth");
        assertEquals("2026-03", latest.get("month").stringValue());
        assertEquals(new BigDecimal(scores.get(1).get("composite").toString()), new BigDecimal(latest.get("compositeScore").stringValue()));
        var january = tree(report(session, "{\"fromMonth\":\"2026-01\",\"toMonth\":\"2026-02\"}")).get("latestHealth");
        assertEquals("2026-01", january.get("month").stringValue());
        assertEquals(new BigDecimal(scores.get(0).get("composite").toString()), new BigDecimal(january.get("compositeScore").stringValue()));
        var none = tree(report(session, "{\"fromMonth\":\"2025-01\",\"toMonth\":\"2025-06\"}"));
        assertTrue(none.get("latestHealth").isNull());
        assertTrue(none.get("periodFlows").get("revenue").get("total").isNull());
        assertTrue(none.get("latestBalances").get("cashBalanceEom").isNull());
        assertEquals(List.of("NO_RECORDS_IN_PERIOD", "NO_HEALTH_SCORE_IN_PERIOD"), json.convertValue(none.get("limitations"), List.class));
    }

    @Test void everyExistingFinancialReadRoleCanReadAndSuspendedOrRemovedMembershipsCannot() throws Exception {
        var f = fixture("Roles Traders"); seed(f.businessId); var session = login(f);
        var membership = memberships.findByUserIdAndBusinessId(f.user.getId(), f.businessId).orElseThrow();
        for (MembershipRole role : MembershipRole.values()) {
            membership.setRole(role); memberships.saveAndFlush(membership);
            report(session, Q1).andExpect(status().isOk());
            csv(session, Q1).andExpect(status().isOk());
        }
        membership.setStatus(MembershipStatus.SUSPENDED); memberships.saveAndFlush(membership);
        report(session, Q1).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("active_business_required"));
        csv(session, Q1).andExpect(status().isConflict());
        membership.setStatus(MembershipStatus.ACTIVE); memberships.saveAndFlush(membership); select(session, f.businessId);
        memberships.delete(membership); memberships.flush();
        report(session, Q1).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("active_business_required"));
    }

    @Test void unauthenticatedAndMissingOrWrongCsrfRequestsFailThroughExistingSecurity() throws Exception {
        mvc.perform(post(URL).with(csrf(null)).contentType("application/json").content(Q1)).andExpect(status().isUnauthorized());
        mvc.perform(post(CSV).with(csrf(null)).contentType("application/json").content(Q1)).andExpect(status().isUnauthorized());
        var f = fixture("Csrf Traders"); var session = login(f);
        mvc.perform(post(URL).cookie(session).contentType("application/json").content(Q1)).andExpect(status().isForbidden());
        mvc.perform(post(CSV).cookie(session).contentType("application/json").content(Q1)).andExpect(status().isForbidden());
        mvc.perform(post(URL).cookie(session).header("X-XSRF-TOKEN", "wrong").contentType("application/json").content(Q1)).andExpect(status().isForbidden());
        mvc.perform(get(URL).cookie(session)).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/auth/logout").cookie(session).with(csrf(session))).andExpect(status().isNoContent());
        report(session, Q1).andExpect(status().isUnauthorized());
    }

    @Test void tenantAuthorityCannotBeInjectedAndBusinessSwitchIsolatesTheReport() throws Exception {
        var first = fixture("First Traders"); seed(first.businessId);
        UUID second = business(first.user, "Second Traders", MembershipRole.VIEWER);
        record(second, "2026-02", "7.77", "1.00", "10.00", "5.00", "123.45", "9.99");
        UUID foreign = fixture("Foreign Traders").businessId; record(foreign, "2026-01", "999999.99", "1.00", "1.00", "1.00", "1.00", "1.00");
        var session = login(first);
        for (String authority : List.of("businessId", "userId", "actorUserId", "tenant", "sql", "metrics"))
            report(session, Q1.replaceFirst("\\{", "{\"" + authority + "\":\"" + second + "\",")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("report_invalid"));
        var viaHeaders = mvc.perform(post(URL).param("businessId", foreign.toString()).header("X-Business-Id", foreign.toString())
                .cookie(session).with(csrf(session)).contentType("application/json").content(Q1)).andExpect(status().isOk());
        assertEquals("First Traders", json.readTree(viaHeaders.andReturn().getResponse().getContentAsString()).get("business").get("name").stringValue());
        assertEquals("3000.30", tree(report(session, Q1)).get("periodFlows").get("revenue").get("total").stringValue());

        select(session, second);
        var switched = tree(report(session, Q1));
        assertEquals("Second Traders", switched.get("business").get("name").stringValue());
        assertEquals("7.77", switched.get("periodFlows").get("revenue").get("total").stringValue());
        assertEquals(1, switched.get("monthlyRows").size());
        assertEquals("123.45", switched.get("latestBalances").get("cashBalanceEom").stringValue());
        assertFalse(csv(session, Q1).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8).contains("3000.30"));
        select(session, first.businessId);
        assertEquals("3000.30", tree(report(session, Q1)).get("periodFlows").get("revenue").get("total").stringValue());
        assertFalse(json.writeValueAsString(tree(report(session, Q1))).contains("999999.99"));
    }

    @Test void csvIsRenderedFromTheSameDatasetWithSafeHeadersUtf8BomAndNeutralizedHostileText() throws Exception {
        var f = fixture("=HYPERLINK(\"http://evil\",\"x\")"); seed(f.businessId); var session = login(f);
        var json1 = tree(report(session, Q1));
        var result = csv(session, Q1).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"finsight-business-financial-summary_2026-01_2026-03.csv\""))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn();
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertEquals((byte) 0xEF, bytes[0]); assertEquals((byte) 0xBB, bytes[1]); assertEquals((byte) 0xBF, bytes[2]);
        var rows = BusinessFinancialReportCsvTests.parse(bytes);
        assertEquals("'=HYPERLINK(\"http://evil\",\"x\")", BusinessFinancialReportCsvTests.value(rows, "report", "", "businessName"));
        assertEquals(json1.get("periodFlows").get("revenue").get("total").stringValue(), BusinessFinancialReportCsvTests.value(rows, "periodFlow", "2026-01..2026-03", "revenue"));
        assertEquals(json1.get("periodFlows").get("netCashMovement").get("total").stringValue(), BusinessFinancialReportCsvTests.value(rows, "periodFlow", "2026-01..2026-03", "netCashMovement"));
        assertEquals(json1.get("latestBalances").get("cashBalanceEom").stringValue(), BusinessFinancialReportCsvTests.value(rows, "latestBalance", "2026-03", "cashBalanceEom"));
        assertEquals("", BusinessFinancialReportCsvTests.value(rows, "latestBalance", "2026-03", "receivablesOutstanding"));
        assertEquals(json1.get("latestHealth").get("compositeScore").stringValue(), BusinessFinancialReportCsvTests.value(rows, "latestHealth", "2026-03", "compositeScore"));
        for (var row : json1.get("monthlyRows"))
            assertEquals(row.get("cashInflow").stringValue(), BusinessFinancialReportCsvTests.value(rows, "monthly", row.get("month").stringValue(), "cashInflow"));
        assertFalse(new String(bytes, StandardCharsets.UTF_8).contains(f.businessId.toString()));
        // Deterministic ordering/content: only the generated-at row may differ between two requests.
        var again = BusinessFinancialReportCsvTests.parse(csv(session, Q1).andReturn().getResponse().getContentAsByteArray());
        assertEquals(strip(rows), strip(again));
    }

    static List<List<String>> strip(List<List<String>> rows) {
        return rows.stream().filter(row -> !row.get(2).equals("generatedAt")).toList();
    }

    @Test void urduCsvAndJsonLanguageWorkEndToEnd() throws Exception {
        var f = fixture("کراچی ٹریڈرز"); seed(f.businessId); var session = login(f);
        var body = "{\"fromMonth\":\"2026-01\",\"toMonth\":\"2026-03\",\"language\":\"ur\"}";
        assertEquals("ur", tree(report(session, body)).get("language").stringValue());
        var rows = BusinessFinancialReportCsvTests.parse(csv(session, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEquals("کراچی ٹریڈرز", BusinessFinancialReportCsvTests.value(rows, "report", "", "businessName"));
        assertTrue(rows.stream().anyMatch(row -> row.get(0).equals("periodFlow") && row.get(3).equals("آمدنی")));
    }

    @Test void invalidRangesMalformedJsonAndOversizeRequestsAreRejectedOnBothEndpoints() throws Exception {
        var session = login(fixture("Bounds Traders"));
        for (String path : List.of(URL, CSV)) {
            for (String body : List.of("{\"fromMonth\":\"2026-05\",\"toMonth\":\"2026-04\"}", "{\"fromMonth\":\"2026-13\",\"toMonth\":\"2026-14\"}",
                    "{\"fromMonth\":\"2024-01\",\"toMonth\":\"2026-01\"}", "{\"fromMonth\":\"2026-01\",\"toMonth\":\"2026-02\",\"language\":\"fr\"}",
                    "{\"fromMonth\":\"2026-01\"}", "{", "", "[]", "null", "{\"fromMonth\":\"2026-01\",\"fromMonth\":\"2026-01\",\"toMonth\":\"2026-02\"}")) {
                mvc.perform(post(path).cookie(session).with(csrf(session)).contentType("application/json").accept("text/csv", "application/json").content(body))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("report_invalid"));
            }
            mvc.perform(post(path).cookie(session).with(csrf(session)).contentType("application/json").accept("text/csv", "application/json")
                    .content(Q1.replace("en", "e".repeat(4000)))).andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.error").value("report_request_too_large"));
        }
        report(session, "{\"fromMonth\":\"2024-01\",\"toMonth\":\"2025-12\"}").andExpect(status().isOk());
    }

    @Test void reportAndExportDoNotMutateAnyFinancialOrConfigurationRow() throws Exception {
        var f = fixture("No Mutation Traders"); seed(f.businessId); var other = fixture("Other"); seed(other.businessId);
        var session = login(f); var before = financialSnapshot();
        for (int count = 0; count < 3; count++) {
            report(session, Q1).andExpect(status().isOk()); csv(session, Q1).andExpect(status().isOk());
            report(session, "{\"fromMonth\":\"2025-01\",\"toMonth\":\"2025-02\"}").andExpect(status().isOk());
        }
        report(session, "{\"fromMonth\":\"2026-03\",\"toMonth\":\"2026-01\"}").andExpect(status().isBadRequest());
        csv(session, "{").andExpect(status().isBadRequest());
        assertEquals(before, financialSnapshot(), "Every financial, score, advice, document, configuration and dashboard row must remain identical");
    }

    Map<String, String> financialSnapshot() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (String table : List.of("monthly_records", "score_results", "insights", "recommendations", "uploaded_documents", "whatsapp_deliveries",
                "business_profiles", "business_configuration", "dashboard_preferences", "document_financial_contributions"))
            snapshot.put(table, jdbc.queryForObject("SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text), '[]'::jsonb)::text FROM " + table + " t", String.class));
        return snapshot;
    }
}
