package com.app.sme_health_backend.estimates;

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
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL, JDBC sessions, CSRF and existing role/business resolution; no mocked financial stores. */
@SpringBootTest(properties = {"app.ai.enabled=false", "app.ai.live-activation-approved=false", "app.ai.api-key="})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class EstimatePreviewPostgreSqlIT extends com.app.sme_health_backend.testsupport.DisposablePostgres {
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
    static final String FIXTURE_PASSWORD = "F06-synthetic-password-2026!";
    static final String VALID = """
            {"lines":[{"costGroup":"MATERIAL","description":"Materials","quantity":"1","unit":"batch","unitCost":"800.00"}],
             "saleableQuantity":"10","pricingMode":"TARGET_MARGIN","pricingValue":"0.20"}
            """;
    record Fixture(AppUser user, UUID businessId) { }
    Fixture fixture() {
        AppUser user = new AppUser(); user.setEmail("f06." + UUID.randomUUID() + "@example.com");
        user.setFullName("Synthetic F06 fixture"); user.setPasswordHash(passwordEncoder.encode(FIXTURE_PASSWORD));
        user.setAccountStatus(AccountStatus.ACTIVE); user.setMustChangePassword(false); user = users.saveAndFlush(user);
        UUID business = UUID.randomUUID(); businesses.saveAndFlush(new Business(business));
        memberships.saveAndFlush(new BusinessMembership(user.getId(), business, MembershipRole.OWNER, MembershipStatus.ACTIVE));
        BusinessProfile profile = new BusinessProfile(); profile.setUserId(business); profile.setBusinessType("services");
        profile.setLanguagePreference("en"); profiles.saveAndFlush(profile);
        return new Fixture(user, business);
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
    ResultActions preview(Cookie session, String body) throws Exception {
        return mvc.perform(post("/api/estimates/preview").cookie(session).with(csrf(session)).contentType("application/json").content(body));
    }
    @Test void authorizedPreviewUsesDecimalStringsSessionBusinessAndNoStore() throws Exception {
        var f = fixture(); var session = login(f);
        var result = preview(session, VALID).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.businessId").value(f.businessId.toString())).andExpect(jsonPath("$.planningOnly").value(true))
                .andExpect(jsonPath("$.currency").value("PKR")).andExpect(jsonPath("$.currencyBasis").value("APPLICATION_FIXED_PKR"))
                .andExpect(jsonPath("$.totalPlannedCost").value("800.00")).andExpect(jsonPath("$.plannedBatchPrice").value("1000.00"))
                .andExpect(jsonPath("$.plannedContribution").value("200.00")).andExpect(jsonPath("$.plannedMargin").value("0.2"))
                .andExpect(jsonPath("$.plannedMarkup").value("0.25")).andExpect(jsonPath("$.plannedPerUnitPrice").value("100.00"))
                .andReturn().getResponse().getContentAsString();
        var tree = json.readTree(result);
        for (String field : List.of("totalPlannedCost", "plannedBatchPrice", "plannedContribution", "plannedMargin", "plannedMarkup", "plannedPerUnitPrice", "saleableQuantity", "pricingValue"))
            assertTrue(tree.get(field).isString(), field + " must be a JSON string");
        assertTrue(tree.get("calculation").get("accountingRoundingPolicy").isNull());
        preview(session, VALID.replace("TARGET_MARGIN", "TARGET_MARKUP")).andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedBatchPrice").value("960.00")).andExpect(jsonPath("$.plannedMargin").value("0.166666666666666666666667"));
    }
    @Test void everyExistingFinancialReadRoleIsAllowedButSuspendedAndRemovedMembershipsAreDenied() throws Exception {
        var f = fixture(); var session = login(f);
        var membership = memberships.findByUserIdAndBusinessId(f.user.getId(), f.businessId).orElseThrow();
        for (MembershipRole role : MembershipRole.values()) {
            membership.setRole(role); memberships.saveAndFlush(membership);
            preview(session, VALID).andExpect(status().isOk());
        }
        membership.setStatus(MembershipStatus.SUSPENDED); memberships.saveAndFlush(membership);
        preview(session, VALID).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("active_business_required"));
        membership.setStatus(MembershipStatus.ACTIVE); memberships.saveAndFlush(membership); select(session, f.businessId);
        memberships.delete(membership); memberships.flush();
        preview(session, VALID).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("active_business_required"));
    }
    @Test void unauthenticatedAndCsrfRequestsFailThroughExistingSecurity() throws Exception {
        mvc.perform(post("/api/estimates/preview").with(csrf(null)).contentType("application/json").content(VALID))
                .andExpect(status().isUnauthorized());
        var f = fixture(); var session = login(f);
        mvc.perform(post("/api/estimates/preview").cookie(session).contentType("application/json").content(VALID)).andExpect(status().isForbidden());
        mvc.perform(post("/api/estimates/preview").cookie(session).header("X-XSRF-TOKEN", "wrong").contentType("application/json").content(VALID))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(session).with(csrf(session))).andExpect(status().isNoContent());
        preview(session, VALID).andExpect(status().isUnauthorized());
    }
    @Test void tenantAuthorityCannotBeInjectedAndBusinessSwitchUsesServerSession() throws Exception {
        var first = fixture(); var second = fixture(); var session = login(first);
        for (String authority : List.of("businessId", "userId", "actorUserId", "currency", "projectId", "sourceRef")) {
            String body = VALID.strip().replaceFirst("\\{", "{\"" + authority + "\":\"" + second.businessId + "\",");
            preview(session, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("estimate_invalid"));
        }
        preview(session, VALID.replace("\"unitCost\":\"800.00\"", "\"unitCost\":\"800.00\",\"businessId\":\"" + second.businessId + "\""))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/estimates/preview").param("businessId", second.businessId.toString()).header("X-Business-Id", second.businessId.toString())
                .cookie(session).with(csrf(session)).contentType("application/json").content(VALID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(first.businessId.toString()));
        mvc.perform(post("/api/businesses/active").cookie(session).with(csrf(session)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("businessId", second.businessId)))).andExpect(status().isForbidden());
        memberships.saveAndFlush(new BusinessMembership(first.user.getId(), second.businessId, MembershipRole.VIEWER, MembershipStatus.ACTIVE));
        select(session, second.businessId);
        preview(session, VALID).andExpect(status().isOk()).andExpect(jsonPath("$.businessId").value(second.businessId.toString()));
    }
    @Test void malformedNumericCoercionDuplicatesTrailingContentAndUnknownFieldsAreRejected() throws Exception {
        var session = login(fixture());
        for (String body : List.of(
                VALID.replace("\"quantity\":\"1\"", "\"quantity\":1"),
                VALID.replace("\"unitCost\":\"800.00\"", "\"unitCost\":800.00"),
                VALID.replace("\"saleableQuantity\":\"10\"", "\"saleableQuantity\":10"),
                VALID.replace("\"pricingValue\":\"0.20\"", "\"pricingValue\":0.20"),
                VALID.replace("\"quantity\":\"1\"", "\"quantity\":\"1e500\""),
                VALID.replace("\"quantity\":\"1\"", "\"quantity\":\"-1\""),
                VALID.replace("\"unitCost\":\"800.00\"", "\"unitCost\":\"-1\""),
                VALID.replace("\"pricingValue\":\"0.20\"", "\"pricingValue\":\"1\""),
                VALID.replace("\"quantity\":\"1\"", "\"quantity\":null"),
                VALID.replace("\"quantity\":\"1\"", "\"quantity\":true"),
                VALID.replace("\"quantity\":\"1\"", "\"quantity\":\"1\",\"quantity\":\"2\""),
                VALID.replace("\"pricingValue\":\"0.20\"", "\"pricingValue\":\"0.20\",\"pricingValue\":\"0.30\""),
                VALID.replace("\"MATERIAL\"", "\"UNKNOWN\""), VALID + "{}", "null", "[]", "{", "")) {
            preview(session, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("estimate_invalid"));
        }
    }
    @Test void boundedLineCountDecimalSizeAndRawBodyAreEnforced() throws Exception {
        var session = login(fixture());
        var line = Map.of("costGroup", "MATERIAL", "description", "x", "quantity", "1", "unit", "item", "unitCost", "1");
        String excessive = json.writeValueAsString(Map.of("lines", Collections.nCopies(101, line), "pricingMode", "PROPOSED_PRICE", "pricingValue", "1"));
        preview(session, excessive).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.lines").value("line_count_invalid"));
        preview(session, VALID.replace("800.00", "1000000000000")).andExpect(status().isBadRequest());
        preview(session, VALID.replace("800.00", "0.1234567890123")).andExpect(status().isBadRequest());
        preview(session, VALID.replace("Materials", "x".repeat(65_536))).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("estimate_request_too_large")).andExpect(header().string("Cache-Control", "no-store"));
        String largestCount = json.writeValueAsString(Map.of("lines", Collections.nCopies(100, line), "pricingMode", "PROPOSED_PRICE", "pricingValue", "1"));
        preview(session, largestCount).andExpect(status().isOk()).andExpect(jsonPath("$.totalPlannedCost").value("100.00"));
    }
    @Test void unknownAndZeroDenominatorsSerializeAsExplicitNulls() throws Exception {
        var session = login(fixture());
        var body = VALID.replace("800.00", "0").replace("\"saleableQuantity\":\"10\"", "\"saleableQuantity\":null");
        var response = preview(session, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var tree = json.readTree(response);
        for (String field : List.of("plannedMargin", "plannedMarkup", "plannedPerUnitPrice", "saleableQuantity")) assertTrue(tree.get(field).isNull());
        assertEquals("ZERO_PRICE", tree.get("unavailableReasons").get("plannedMargin").stringValue());
        assertEquals("ZERO_COST", tree.get("unavailableReasons").get("plannedMarkup").stringValue());
        assertEquals("SALEABLE_QUANTITY_UNKNOWN", tree.get("unavailableReasons").get("plannedPerUnitPrice").stringValue());
        preview(session, VALID.replace("\"saleableQuantity\":\"10\"", "\"saleableQuantity\":\"0\""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unavailableReasons.plannedPerUnitPrice").value("ZERO_SALEABLE_QUANTITY"));
    }
    @Test void acceptedRejectedAndRepeatedPreviewsDoNotMutateMonthlyScoresOrOtherFinancialData() throws Exception {
        var first = fixture(); var second = fixture();
        for (Fixture fixture : List.of(first, second)) {
            MonthlyRecord record = new MonthlyRecord(); record.setUserId(fixture.businessId); record.setMonth("2026-09");
            record.setCashInflow(new BigDecimal("12345.67")); record.setCashOutflow(new BigDecimal("10000.12"));
            record.setCashBalanceEom(new BigDecimal("2345.55")); record.setRevenue(new BigDecimal("15678.91"));
            record.setOperatingExpenses(new BigDecimal("9500.34")); record.setCogs(null); record.setUpdatedAt(LocalDateTime.now());
            records.saveAndFlush(record); scoring.calculateAndSaveScore(fixture.businessId, record.getMonth());
        }
        var session = login(first); var before = financialSnapshot();
        for (int count = 0; count < 3; count++) {
            preview(session, VALID).andExpect(status().isOk());
            preview(session, VALID.replace("TARGET_MARGIN", "TARGET_MARKUP")).andExpect(status().isOk());
        }
        preview(session, VALID.replace("0.20", "1.20")).andExpect(status().isBadRequest());
        assertEquals(before, financialSnapshot(), "All actual financial rows, nullable values, score evidence and timestamps must remain identical");
    }
    Map<String, String> financialSnapshot() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (String table : List.of("monthly_records", "score_results", "insights", "recommendations", "uploaded_documents", "whatsapp_deliveries", "business_profiles", "business_configuration", "dashboard_preferences"))
            snapshot.put(table, jdbc.queryForObject("SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text), '[]'::jsonb)::text FROM " + table + " t", String.class));
        return snapshot;
    }
}
