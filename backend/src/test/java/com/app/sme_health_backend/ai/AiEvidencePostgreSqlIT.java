package com.app.sme_health_backend.ai;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.entity.*;
import com.app.sme_health_backend.identity.model.*;
import com.app.sme_health_backend.identity.repository.*;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.recommendation.entity.RecommendationStatus;
import com.app.sme_health_backend.recommendation.repository.RecommendationRepository;
import com.app.sme_health_backend.recommendation.service.RecommendationService;
import com.app.sme_health_backend.insight.service.InsightService;
import com.app.sme_health_backend.scoring.service.ScoringService;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real disposable PostgreSQL; only synthetic records, deterministic provider, no network activation. */
@SpringBootTest(properties = {"app.ai.enabled=true", "app.ai.provider=mock", "app.ai.demo-enabled=true", "app.ai.requests-per-minute=60"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class AiEvidencePostgreSqlIT extends com.app.sme_health_backend.testsupport.DisposablePostgres {
    @Autowired AppUserRepository users;
    @Autowired BusinessRepository businesses;
    @Autowired BusinessMembershipRepository memberships;
    @Autowired BusinessProfileRepository profiles;
    @Autowired MonthlyRecordRepository records;
    @Autowired RecommendationRepository recommendations;
    @Autowired ScoringService scoring;
    @Autowired InsightService insights;
    @Autowired RecommendationService recommendationService;
    @Autowired MvpFinancialEvidenceAdapter adapter;
    @Autowired AiExplanationService explanations;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    org.springframework.test.web.servlet.request.RequestPostProcessor realCsrf(Cookie session) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").cookie(session)).andExpect(status().isOk()).andReturn().getResponse();
        Cookie xsrf = response.getCookie("XSRF-TOKEN"); assertNotNull(xsrf);
        var token = new tools.jackson.databind.json.JsonMapper().readValue(response.getContentAsString(),
                com.app.sme_health_backend.identity.dto.CsrfResponse.class);
        return request -> {
            var cookies = new java.util.ArrayList<Cookie>(java.util.Arrays.asList(request.getCookies() == null ? new Cookie[0] : request.getCookies()));
            cookies.removeIf(cookie -> cookie.getName().equals(xsrf.getName())); cookies.add(xsrf);
            request.setCookies(cookies.toArray(Cookie[]::new)); request.addHeader(token.headerName(), token.token());
            return request;
        };
    }

    record Fixture(BusinessAccessContext actor, MonthlyRecord record) { }
    Fixture fixture() {
        AppUser user = new AppUser(); user.setEmail("offline.ai." + UUID.randomUUID() + "@example.com");
        user.setFullName("Synthetic AI fixture"); user.setPasswordHash("fixture-password-not-for-login"); user.setAccountStatus(AccountStatus.ACTIVE);
        user = users.saveAndFlush(user);
        UUID businessId = UUID.randomUUID(); businesses.saveAndFlush(new Business(businessId));
        memberships.saveAndFlush(new BusinessMembership(user.getId(), businessId, MembershipRole.OWNER, MembershipStatus.ACTIVE));
        BusinessProfile profile = new BusinessProfile(); profile.setUserId(businessId); profile.setBusinessType("trade");
        profile.setLanguagePreference("en"); profile.setPaymentBehavior("immediate"); profile.setNtnRegistered(true); profile.setBusinessRegistered(true);
        profiles.saveAndFlush(profile);
        MonthlyRecord record = new MonthlyRecord(); record.setUserId(businessId); record.setMonth("2026-09");
        record.setRevenue(new BigDecimal("100000.00")); record.setOperatingExpenses(new BigDecimal("35000.00")); record.setCogs(new BigDecimal("20000.00"));
        record.setCashInflow(new BigDecimal("95000.00")); record.setCashOutflow(new BigDecimal("85000.00"));
        record.setCashBalanceEom(new BigDecimal("10000.00")); record.setFinancingType("none"); record.setUpdatedAt(LocalDateTime.now());
        record = records.saveAndFlush(record); scoring.calculateAndSaveScore(businessId, "2026-09");
        insights.getInsights(businessId); recommendationService.getRecommendations(businessId);
        return new Fixture(new BusinessAccessContext(user.getId(), businessId, MembershipRole.OWNER), record);
    }
    @Test void persistedEvidenceMatchesCanonicalScoreAndDoesNotMutateAdviceLifecycle() {
        var f = fixture(); UUID businessId = f.actor.businessId();
        var rec = recommendations.findByUserIdAndMonthOrderByCreatedAtDesc(businessId, "2026-09").getFirst();
        rec.setStatus(RecommendationStatus.DONE); recommendations.saveAndFlush(rec);
        long before = recommendations.count();
        var response = explanations.explain(f.actor, new AiTypes.Request("recommendation", "en", null, rec.getId(), "2026-09", null));
        assertEquals("simulated", response.provenance().mode()); assertEquals(before, recommendations.count());
        assertEquals(RecommendationStatus.DONE, recommendations.findByIdAndUserId(rec.getId(), businessId).orElseThrow().getStatus());
        String canonical = scoring.getScore(businessId, "2026-09").orElseThrow().getCompositeScore().toPlainString();
        assertEquals(canonical, response.evidence().stream().filter(e -> e.id().equals("score.total")).findFirst().orElseThrow().value());
        var event = jdbc.queryForMap("SELECT actor_user_id,business_id,metadata FROM security_audit_events WHERE business_id=? AND event_type='AI_EXPLANATION_REQUESTED' ORDER BY occurred_at DESC LIMIT 1", businessId);
        assertEquals(f.actor.userId(), event.get("actor_user_id")); assertEquals(businessId, event.get("business_id"));
        assertFalse(event.get("metadata").toString().contains(canonical));
    }
    @Test void rejectsAnotherTenantsRecommendationEvenWithValidUuid() {
        var first = fixture(); var other = fixture();
        var foreign = recommendations.findByUserIdAndMonthOrderByCreatedAtDesc(first.actor.businessId(), "2026-09").getFirst();
        assertThrows(ResourceNotFoundException.class, () -> adapter.build(other.actor,
                new AiTypes.Request("recommendation", "en", null, foreign.getId(), "2026-09", null), "recommendation"));
    }
    @Test void persistedRecordChangesInvalidateCachedExplanations() {
        var f = fixture(); var request = new AiTypes.Request("ask", "en", "Explain monthly records", null, "2026-09", null);
        assertFalse(explanations.explain(f.actor, request).provenance().cached());
        assertTrue(explanations.explain(f.actor, request).provenance().cached());
        var record = records.findById(f.record.getId()).orElseThrow(); record.setRevenue(new BigDecimal("100123.45")); record.setUpdatedAt(LocalDateTime.now()); records.saveAndFlush(record);
        var refreshed = explanations.explain(f.actor, request); assertFalse(refreshed.provenance().cached());
        assertEquals("100123.45", refreshed.evidence().stream().filter(e -> e.id().equals("record.2026-09.revenue")).findFirst().orElseThrow().value());
    }
    @Test void realSecurityRequiresSessionMembershipAndCsrfAndSupportsViewerRead() throws Exception {
        var f = fixture();
        var member = memberships.findByUserIdAndBusinessId(f.actor.userId(), f.actor.businessId()).orElseThrow();
        member.setRole(MembershipRole.VIEWER); memberships.saveAndFlush(member);
        var account = users.findById(f.actor.userId()).orElseThrow();
        String password = "AI-fixture-password-2026!";
        account.setPasswordHash(passwordEncoder.encode(password)); account.setMustChangePassword(false); users.saveAndFlush(account);
        var csrfResponse = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        Cookie initialSession = csrfResponse.getCookie("FINSIGHT_SESSION"), xsrf = csrfResponse.getCookie("XSRF-TOKEN");
        assertNotNull(xsrf);
        var login = post("/api/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()).contentType("application/json")
                .content("{\"email\":\"" + account.getEmail() + "\",\"password\":\"" + password + "\"}");
        if (initialSession != null) login.cookie(initialSession);
        Cookie session = mvc.perform(login).andExpect(status().isOk()).andReturn().getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(session, "Login must establish a real JDBC-backed session cookie");
        mvc.perform(post("/api/businesses/active").cookie(session).with(realCsrf(session)).contentType("application/json")
                .content("{\"businessId\":\"" + f.actor.businessId() + "\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/ai/status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ai/status").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("simulated"));
        String body = "{\"type\":\"dashboard\",\"language\":\"en\"}";
        mvc.perform(post("/api/ai/explain").cookie(session).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/explain").cookie(session).with(realCsrf(session)).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.provenance.mode").value("simulated"));
        member.setStatus(MembershipStatus.SUSPENDED); memberships.saveAndFlush(member);
        mvc.perform(get("/api/ai/status").cookie(session)).andExpect(status().isConflict());
    }
}
