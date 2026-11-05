package com.app.sme_health_backend.configuration;

import com.app.sme_health_backend.configuration.dto.ConfigurationRequest;
import com.app.sme_health_backend.configuration.exception.ConfigurationException;
import com.app.sme_health_backend.configuration.model.OperatingProfile;
import com.app.sme_health_backend.configuration.service.*;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.entity.*;
import com.app.sme_health_backend.identity.model.*;
import com.app.sme_health_backend.identity.repository.*;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.whatsapp.service.WhatsAppDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Full Spring security + disposable real PostgreSQL; synthetic businesses only. */
@SpringBootTest(properties = "app.whatsapp.provider=mock")
@AutoConfigureMockMvc
class BusinessConfigurationPostgreSqlIT extends com.app.sme_health_backend.testsupport.DisposablePostgres {
    @Autowired AppUserRepository users;
    @Autowired BusinessRepository businesses;
    @Autowired BusinessMembershipRepository memberships;
    @Autowired BusinessProfileRepository profiles;
    @Autowired UploadedDocumentRepository documents;
    @Autowired BusinessConfigurationService configuration;
    @Autowired ModuleAccessPolicy modules;
    @Autowired WhatsAppDeliveryService whatsapp;
    @Autowired com.app.sme_health_backend.scoring.repository.ScoreResultRepository scores;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.app.sme_health_backend.whatsapp.client.WhatsAppClient whatsappClient;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    static final String PASSWORD = "F02-fixture-password-2026!";
    record Fixture(AppUser user, UUID businessId, Cookie session) {
        BusinessAccessContext actor() { return new BusinessAccessContext(user.getId(), businessId, MembershipRole.OWNER); }
    }
    Fixture fixture(String category) throws Exception {
        AppUser user = new AppUser(); user.setEmail("f02." + UUID.randomUUID() + "@example.com");
        user.setFullName("Synthetic configuration fixture"); user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setAccountStatus(AccountStatus.ACTIVE); user.setMustChangePassword(false); user = users.saveAndFlush(user);
        UUID business = UUID.randomUUID(); businesses.saveAndFlush(new Business(business));
        memberships.saveAndFlush(new BusinessMembership(user.getId(), business, MembershipRole.OWNER, MembershipStatus.ACTIVE));
        BusinessProfile profile = new BusinessProfile(); profile.setUserId(business); profile.setBusinessType(category);
        profile.setLanguagePreference("en"); profiles.saveAndFlush(profile);
        return new Fixture(user, business, loginAndSelect(user, business));
    }
    MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, Fixture f) {
        return request.cookie(f.session);
    }
    Cookie loginAndSelect(AppUser user, UUID business) throws Exception {
        var csrfResponse = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        Cookie initialSession = csrfResponse.getCookie("FINSIGHT_SESSION"), xsrf = csrfResponse.getCookie("XSRF-TOKEN");
        assertNotNull(xsrf);
        var login = post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}")
                .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue());
        if (initialSession != null) login.cookie(initialSession);
        Cookie session = mvc.perform(login).andExpect(status().isOk()).andReturn().getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(session, "Login must establish a real JDBC-backed session cookie");
        selectBusiness(session, business);
        return session;
    }
    void selectBusiness(Cookie session, UUID business) throws Exception {
        mvc.perform(post("/api/businesses/active").cookie(session).with(realCsrf(session)).contentType("application/json")
                .content("{\"businessId\":\"" + business + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.businessId").value(business.toString()));
    }
    // Spring's csrf() postprocessor replaces the filter's repository. Real cookies avoid
    // leaking that test repository into later requests/tests in this shared Spring context.
    org.springframework.test.web.servlet.request.RequestPostProcessor realCsrf(Cookie session) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").cookie(session)).andExpect(status().isOk()).andReturn().getResponse();
        Cookie xsrf = response.getCookie("XSRF-TOKEN");
        assertNotNull(xsrf);
        var token = new tools.jackson.databind.json.JsonMapper().readValue(response.getContentAsString(),
                com.app.sme_health_backend.identity.dto.CsrfResponse.class);
        return request -> {
            var cookies = new ArrayList<Cookie>(Arrays.asList(request.getCookies() == null ? new Cookie[0] : request.getCookies()));
            cookies.removeIf(cookie -> cookie.getName().equals(xsrf.getName())); cookies.add(xsrf);
            request.setCookies(cookies.toArray(Cookie[]::new)); request.addHeader(token.headerName(), token.token());
            return request;
        };
    }
    ConfigurationRequest coreOnly(long version) { return new ConfigurationRequest(OperatingProfile.PROJECT_ORDER,
            Map.of("tracksProjects", true, "holdsInventory", false), List.of("core_finance"), version); }
    String body(long version) { return "{\"operatingProfile\":\"PROJECT_ORDER\",\"operatingAnswers\":{\"tracksProjects\":true},"
            + "\"requestedModules\":[\"core_finance\"],\"version\":" + version + "}"; }

    @Test void newBusinessesUseCompatibleDefaultsAndSaveSeparatelyFromScoreCategory() throws Exception {
        var f = fixture("trade");
        mvc.perform(authenticated(get("/api/business-configuration"), f)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.operatingProfile").value("RETAIL_TRADE"))
                .andExpect(jsonPath("$.operatingAnswers.holdsInventory").value(true))
                .andExpect(jsonPath("$.operatingAnswers.tracksProjects").value(false))
                .andExpect(jsonPath("$.operatingAnswers.sellsOnCredit").value(false))
                .andExpect(jsonPath("$.configured").value(false)).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.requestedModules.length()").value(4));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM business_configuration WHERE business_id=?", Integer.class, f.businessId));
        var preview = configuration.preview(f.actor(), coreOnly(0));
        assertFalse(preview.configured()); assertEquals(0, configuration.get(f.actor()).version());
        mvc.perform(authenticated(put("/api/business-configuration"), f).with(realCsrf(f.session)).contentType("application/json").content(body(0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.configured").value(true)).andExpect(jsonPath("$.presetKey").value("project-order"));
        assertEquals(preview.modules(), configuration.get(f.actor()).modules());
        assertEquals("trade", profiles.findById(f.businessId).orElseThrow().getBusinessType());
        var audit = jdbc.queryForMap("SELECT actor_user_id,business_id FROM security_audit_events WHERE business_id=? AND event_type='BUSINESS_CONFIGURATION_UPDATED'", f.businessId);
        assertEquals(f.user.getId(), audit.get("actor_user_id")); assertEquals(f.businessId, audit.get("business_id"));
        Cookie fresh = loginAndSelect(f.user, f.businessId);
        mvc.perform(get("/api/business-configuration").cookie(fresh))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
    }
    @Test void enforcesCsrfExistingRolesMembershipAndServerSelectedTenant() throws Exception {
        var first = fixture("services"); var second = fixture("manufacturing");
        mvc.perform(get("/api/business-configuration")).andExpect(status().isUnauthorized());
        mvc.perform(authenticated(put("/api/business-configuration"), first).contentType("application/json").content(body(0)))
                .andExpect(status().isForbidden());
        var member = memberships.findByUserIdAndBusinessId(first.user.getId(), first.businessId).orElseThrow();
        for (MembershipRole role : List.of(MembershipRole.ACCOUNTANT, MembershipRole.MANAGER, MembershipRole.VIEWER)) {
            member.setRole(role); memberships.saveAndFlush(member);
            mvc.perform(authenticated(get("/api/business-configuration"), first)).andExpect(status().isOk()).andExpect(jsonPath("$.canManage").value(false));
            mvc.perform(authenticated(put("/api/business-configuration"), first).with(realCsrf(first.session)).contentType("application/json").content(body(0))).andExpect(status().isForbidden());
        }
        member.setRole(MembershipRole.OWNER); memberships.saveAndFlush(member);
        mvc.perform(authenticated(put("/api/business-configuration?businessId=" + second.businessId), first).with(realCsrf(first.session))
                .contentType("application/json").content(body(0))).andExpect(status().isOk());
        assertEquals(1, configuration.get(first.actor()).version()); assertEquals(0, configuration.get(second.actor()).version());
        mvc.perform(authenticated(post("/api/businesses/active"), first).with(realCsrf(first.session)).contentType("application/json")
                .content("{\"businessId\":\"" + second.businessId + "\"}")).andExpect(status().isForbidden());
        mvc.perform(authenticated(get("/api/business-configuration"), first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        assertEquals(0, configuration.get(second.actor()).version());
        member.setStatus(MembershipStatus.SUSPENDED); memberships.saveAndFlush(member);
        mvc.perform(authenticated(get("/api/business-configuration"), first)).andExpect(status().isConflict());
    }
    @Test void activeBusinessSwitchRetrievesIndependentSavedPreferences() throws Exception {
        var first = fixture("services"); var second = fixture("manufacturing");
        configuration.update(first.actor(), coreOnly(0));
        memberships.saveAndFlush(new BusinessMembership(first.user.getId(), second.businessId, MembershipRole.OWNER, MembershipStatus.ACTIVE));
        selectBusiness(first.session, second.businessId);
        mvc.perform(authenticated(get("/api/business-configuration"), first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.operatingProfile").value("MANUFACTURING")).andExpect(jsonPath("$.version").value(0));
        selectBusiness(first.session, first.businessId);
        mvc.perform(authenticated(get("/api/business-configuration"), first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.operatingProfile").value("PROJECT_ORDER")).andExpect(jsonPath("$.version").value(1));
    }
    @Test void concurrentFirstSavesHaveOneWinnerAndStaleSaveNeverChangesState() throws Exception {
        var f = fixture("retail"); CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<String> save = () -> { ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                try { configuration.update(f.actor(), coreOnly(0)); return "saved"; }
                catch (ConfigurationException error) { return error.code(); } };
            Future<String> a = executor.submit(save), b = executor.submit(save);
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            assertEquals(Set.of("saved", "stale_version"), Set.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)));
        }
        assertEquals(1, configuration.get(f.actor()).version());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM security_audit_events WHERE business_id=? AND event_type='BUSINESS_CONFIGURATION_UPDATED'", Integer.class, f.businessId));
        mvc.perform(authenticated(put("/api/business-configuration"), f).with(realCsrf(f.session)).contentType("application/json").content(body(0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("stale_version"));
    }
    @Test void disablingDocumentsAndWhatsappBlocksWritesButRetainsHistoryAndPreferences() throws Exception {
        var f = fixture("services");
        UploadedDocument document = new UploadedDocument(); document.setId(UUID.randomUUID()); document.setUserId(f.businessId); document.setFileUrl("synthetic-retained-file");
        document.setProcessingStatus(DocumentStatus.needs_review); document = documents.saveAndFlush(document);
        var profile = profiles.findById(f.businessId).orElseThrow(); profile.setWhatsappNumber("+923001234567"); profile.setWhatsappOptIn(true); profiles.saveAndFlush(profile);
        var score = new com.app.sme_health_backend.scoring.entity.ScoreResult();
        score.setUserId(f.businessId); score.setMonth("2026-09"); score.setCompositeScore(new java.math.BigDecimal("82.00"));
        score.setBand("STRONG"); score.setWeakestComponent("cashflow");
        score.setComponentScores(new com.app.sme_health_backend.scoring.dto.ComponentScoresDto(
                new java.math.BigDecimal("80"), new java.math.BigDecimal("85"), new java.math.BigDecimal("75"),
                new java.math.BigDecimal("85"), new java.math.BigDecimal("85")));
        score.setDataCompleteness(new java.math.BigDecimal("1.00")); score.setComputedAt(java.time.LocalDateTime.now());
        scores.saveAndFlush(score);
        // Positive control: this same business is eligible, and the explicit mock provider is called.
        var previousDelivery = whatsapp.deliverWeeklySummary(f.businessId, LocalDate.of(2026, 10, 5)).orElseThrow();
        assertEquals(com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus.SENT, previousDelivery.getDeliveryStatus());
        org.mockito.Mockito.verify(whatsappClient, org.mockito.Mockito.times(1)).sendSummary(org.mockito.ArgumentMatchers.any());
        configuration.update(f.actor(), coreOnly(0));
        assertFalse(modules.isEnabled(f.businessId, "documents")); assertFalse(modules.isEnabled(f.businessId, "whatsapp"));
        mvc.perform(authenticated(get("/api/documents/" + document.getId()), f)).andExpect(status().isOk());
        mvc.perform(authenticated(post("/api/documents/query"), f).with(realCsrf(f.session)).contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(document.getId().toString()));
        for (var action : List.of(post("/api/documents/" + document.getId() + "/retry"),
                patch("/api/documents/" + document.getId()), delete("/api/documents/" + document.getId()),
                post("/api/documents/" + document.getId() + "/confirm"), patch("/api/profile/whatsapp"))) {
            mvc.perform(authenticated(action, f).with(realCsrf(f.session)).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("module_disabled"));
        }
        mvc.perform(multipart("/api/documents/upload").file(new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[]{1}))
                .cookie(f.session).with(realCsrf(f.session)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("module_disabled"));
        assertTrue(whatsapp.deliverWeeklySummary(f.businessId, LocalDate.of(2026, 10, 12)).isEmpty());
        org.mockito.Mockito.verify(whatsappClient, org.mockito.Mockito.times(1)).sendSummary(org.mockito.ArgumentMatchers.any());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM whatsapp_deliveries WHERE user_id=?", Integer.class, f.businessId));
        mvc.perform(authenticated(get("/api/whatsapp/deliveries"), f)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(previousDelivery.getId().toString()));
        assertTrue(documents.existsById(document.getId())); assertTrue(profiles.findById(f.businessId).orElseThrow().isWhatsappOptIn());
        configuration.update(f.actor(), new ConfigurationRequest(OperatingProfile.SERVICES, Map.of(), ModuleRegistry.LEGACY_DEFAULTS, 1L));
        assertTrue(modules.isEnabled(f.businessId, "documents")); assertTrue(modules.isEnabled(f.businessId, "whatsapp"));
        assertTrue(documents.existsById(document.getId()));
        assertTrue(profiles.findById(f.businessId).orElseThrow().isWhatsappOptIn());
        assertEquals(com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus.SENT,
                whatsapp.deliverWeeklySummary(f.businessId, LocalDate.of(2026, 10, 12)).orElseThrow().getDeliveryStatus());
        org.mockito.Mockito.verify(whatsappClient, org.mockito.Mockito.times(2)).sendSummary(org.mockito.ArgumentMatchers.any());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM whatsapp_deliveries WHERE user_id=?", Integer.class, f.businessId));
    }
    @Test void operatingAnswersPersistAndPreviewPlanningHintsNeverActivatesUnavailableModules() throws Exception {
        var f = fixture("services");
        var request = new ConfigurationRequest(OperatingProfile.SERVICES,
                Map.of("holdsInventory", true, "tracksProjects", true, "sellsOnCredit", true), List.of("core_finance"), 0L);
        var preview = configuration.preview(f.actor(), request);
        var saved = configuration.update(f.actor(), request);
        assertEquals(preview.recommendedModules(), saved.recommendedModules());
        assertEquals(request.operatingAnswers(), configuration.get(f.actor()).operatingAnswers());
        for (String key : List.of("inventory", "projects", "receivables")) {
            assertTrue(saved.recommendedModules().contains(key));
            var future = saved.modules().stream().filter(module -> module.key().equals(key)).findFirst().orElseThrow();
            assertFalse(future.implemented()); assertFalse(future.enabled()); assertFalse(future.available());
            assertEquals("NOT_IMPLEMENTED", future.reason()); assertFalse(modules.isEnabled(f.businessId, key));
            assertThrows(ConfigurationException.class, () -> configuration.update(f.actor(),
                    new ConfigurationRequest(OperatingProfile.SERVICES, request.operatingAnswers(), List.of("core_finance", key), 1L)));
        }
        assertEquals(1, configuration.get(f.actor()).version());
        assertEquals(List.of("core_finance"), configuration.get(f.actor()).requestedModules());
    }
    @Test void rejectsUnimplementedModulesAndMalformedConfigurationWithoutWriting() throws Exception {
        var f = fixture("services");
        mvc.perform(authenticated(put("/api/business-configuration"), f).with(realCsrf(f.session)).contentType("application/json")
                .content(body(0).replace("[\"core_finance\"]", "[\"core_finance\",\"inventory\"]")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_configuration"));
        mvc.perform(authenticated(put("/api/business-configuration"), f).with(realCsrf(f.session)).contentType("application/json")
                .content(body(0).replace("PROJECT_ORDER", "UNKNOWN"))).andExpect(status().isBadRequest());
        assertEquals(0, configuration.get(f.actor()).version());
    }
}
