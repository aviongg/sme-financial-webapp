package com.app.sme_health_backend.identity;

import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.documents.storage.DocumentStorageService;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.security.internal-service-secret=internal_ocr_dev_secret_2026"})
@AutoConfigureMockMvc
public class TenantIsolationPostgreSqlIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private BusinessMembershipRepository membershipRepository;

    @Autowired
    private BusinessProfileRepository profileRepository;

    @Autowired
    private MonthlyRecordRepository recordRepository;

    @Autowired
    private UploadedDocumentRepository documentRepository;

    @Autowired
    private DocumentStorageService storageService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final Set<UUID> createdUserIds = new HashSet<>();
    private final Set<UUID> createdBusinessIds = new HashSet<>();

    @AfterEach
    void tearDown() {
        for (UUID userId : createdUserIds) {
            try {
                jdbcTemplate.update("DELETE FROM uploaded_documents WHERE user_id = ?", userId);
                jdbcTemplate.update("DELETE FROM monthly_records WHERE user_id = ?", userId);
                jdbcTemplate.update("DELETE FROM business_memberships WHERE user_id = ?", userId);
            } catch (Exception ignored) {}
        }

        for (UUID businessId : createdBusinessIds) {
            try {
                jdbcTemplate.update("DELETE FROM uploaded_documents WHERE user_id = ?", businessId);
                jdbcTemplate.update("DELETE FROM monthly_records WHERE user_id = ?", businessId);
                jdbcTemplate.update("DELETE FROM score_results WHERE user_id = ?", businessId);
                jdbcTemplate.update("DELETE FROM insights WHERE user_id = ?", businessId);
                jdbcTemplate.update("DELETE FROM recommendations WHERE user_id = ?", businessId);
                jdbcTemplate.update("DELETE FROM whatsapp_deliveries WHERE user_id = ?", businessId);
                jdbcTemplate.update("DELETE FROM business_memberships WHERE business_id = ?", businessId);
                jdbcTemplate.update("DELETE FROM business_profiles WHERE user_id = ?", businessId);
            } catch (Exception ignored) {}
        }

        for (UUID businessId : createdBusinessIds) {
            try {
                jdbcTemplate.update("DELETE FROM businesses WHERE id = ?", businessId);
            } catch (Exception ignored) {}
        }

        for (UUID userId : createdUserIds) {
            try {
                jdbcTemplate.update("DELETE FROM business_profiles WHERE user_id = ?", userId);
                jdbcTemplate.update("DELETE FROM app_users WHERE id = ?", userId);
            } catch (Exception ignored) {}
        }

        createdUserIds.clear();
        createdBusinessIds.clear();
    }

    private AppUser createTestUser(String prefix, AccountStatus status) {
        String email = com.app.sme_health_backend.identity.validation.EmailValidator.normalizeAndValidate(
                prefix.toLowerCase(java.util.Locale.ROOT) + "." + UUID.randomUUID() + "@example.com"
        );
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("secure-password-2026!"));
        user.setFullName("Test User " + prefix);
        user.setAccountStatus(status);
        user.setMustChangePassword(false);
        AppUser saved = userRepository.saveAndFlush(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private record LoginSession(Cookie sessionCookie, Cookie xsrfCookie, CsrfToken csrfToken, AppUser user, String rawPassword) {}

    private LoginSession loginUser(AppUser user) throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        String rawPassword = "secure-password-2026!";
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie preLoginCookie = csrfResult.getResponse().getCookie("FINSIGHT_SESSION");
        Cookie xsrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        CsrfToken csrfToken = (CsrfToken) csrfResult.getRequest().getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) csrfResult.getRequest().getAttribute("_csrf");
        }

        String loginPayload = "{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + rawPassword + "\"}";
        MockHttpServletRequestBuilder loginReq = post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload);

        if (preLoginCookie != null) {
            loginReq.cookie(preLoginCookie);
        }
        if (xsrfCookie != null) {
            loginReq.cookie(xsrfCookie).header("X-XSRF-TOKEN", xsrfCookie.getValue());
        } else {
            loginReq.with(csrf());
        }

        MvcResult loginResult = mockMvc.perform(loginReq)
                .andExpect(status().isOk())
                .andReturn();

        Cookie postLoginCookie = loginResult.getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(postLoginCookie, "Post-login session cookie FINSIGHT_SESSION must exist");

        return new LoginSession(postLoginCookie, xsrfCookie, csrfToken, user, rawPassword);
    }

    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder, LoginSession session) {
        builder.cookie(session.sessionCookie());
        if (session.xsrfCookie() != null) {
            builder.cookie(session.xsrfCookie()).header("X-XSRF-TOKEN", session.xsrfCookie().getValue());
        }
        builder.with(csrf());
        return builder;
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder withAuth(
            org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder builder,
            LoginSession session
    ) {
        builder.cookie(session.sessionCookie());
        if (session.xsrfCookie() != null) {
            builder.cookie(session.xsrfCookie()).header("X-XSRF-TOKEN", session.xsrfCookie().getValue());
        }
        builder.with(csrf());
        return builder;
    }

    private UUID createBusinessForSession(LoginSession session, String businessType) throws Exception {
        String createBusinessPayload = """
                {
                    "businessType": "%s",
                    "languagePreference": "en",
                    "whatsappOptIn": false
                }
                """.formatted(businessType);

        MvcResult result = mockMvc.perform(withAuth(post("/api/businesses"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBusinessPayload))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        // Parse businessId from JSON
        int idx = responseBody.indexOf("\"businessId\":\"");
        String bIdStr = responseBody.substring(idx + 14, idx + 50);
        UUID bId = UUID.fromString(bIdStr);
        createdBusinessIds.add(bId);
        return bId;
    }

    @Test
    @DisplayName("Authenticated user without active business returns 409 Conflict with active_business_required")
    void testMissingActiveBusinessReturns409Conflict() throws Exception {
        AppUser user = createTestUser("noactive", AccountStatus.ACTIVE);
        LoginSession session = loginUser(user);

        // Calling profile without selecting or creating a business returns 409
        mockMvc.perform(withAuth(get("/api/profile"), session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("active_business_required"))
                .andExpect(jsonPath("$.message").value("Select or create a business before using this feature."));

        // Calling monthly records returns 409
        mockMvc.perform(withAuth(get("/api/records/monthly"), session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("active_business_required"));
    }

    @Test
    @DisplayName("Cross-tenant isolation across all financial domains; cross-tenant lookup yields 404")
    void testCrossTenantIsolationAcrossDomains() throws Exception {
        AppUser userA = createTestUser("tenantA", AccountStatus.ACTIVE);
        LoginSession sessionA = loginUser(userA);
        UUID businessA = createBusinessForSession(sessionA, "retail");

        AppUser userB = createTestUser("tenantB", AccountStatus.ACTIVE);
        LoginSession sessionB = loginUser(userB);
        UUID businessB = createBusinessForSession(sessionB, "services");

        // 1. User A posts record for Business A
        String recordAPayload = """
                {
                    "month": "2026-09",
                    "cashInflow": 500000,
                    "cashOutflow": 200000,
                    "revenue": 500000,
                    "cogs": 150000,
                    "operatingExpenses": 50000,
                    "cashBalanceEom": 300000,
                    "financingType": "none"
                }
                """;
        mockMvc.perform(withAuth(post("/api/records/monthly"), sessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordAPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessId").value(businessA.toString()))
                .andExpect(jsonPath("$.revenue").value(500000));

        // 2. User B posts record for Business B
        String recordBPayload = """
                {
                    "month": "2026-09",
                    "cashInflow": 999999,
                    "cashOutflow": 111111,
                    "revenue": 999999,
                    "cogs": 200000,
                    "operatingExpenses": 88888,
                    "cashBalanceEom": 800000,
                    "financingType": "none"
                }
                """;
        MvcResult recordBResult = mockMvc.perform(withAuth(post("/api/records/monthly"), sessionB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessId").value(businessB.toString()))
                .andReturn();

        // 3. User A queries monthly records -> sees only Business A
        mockMvc.perform(withAuth(get("/api/records/monthly"), sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].businessId").value(businessA.toString()))
                .andExpect(jsonPath("$[0].revenue").value(500000));

        // 4. User B queries monthly records -> sees only Business B
        mockMvc.perform(withAuth(get("/api/records/monthly"), sessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].businessId").value(businessB.toString()))
                .andExpect(jsonPath("$[0].revenue").value(999999));

        // 5. Cross-tenant privacy guarantee: User A querying Business B's record ID returns generic 404
        String recordBBody = recordBResult.getResponse().getContentAsString();
        int idx = recordBBody.indexOf("\"id\":\"");
        UUID recordBId = UUID.fromString(recordBBody.substring(idx + 6, idx + 42));

        mockMvc.perform(withAuth(get("/api/records/monthly/id/" + recordBId), sessionA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // 6. User A gets profile -> gets Business A
        mockMvc.perform(withAuth(get("/api/profile"), sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(businessA.toString()))
                .andExpect(jsonPath("$.businessType").value("retail"));

        // 7. User A gets cashflow -> gets Business A
        mockMvc.perform(withAuth(get("/api/cashflow"), sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].inflow").value(500000));

        // 8. User A gets dashboard -> gets Business A
        mockMvc.perform(withAuth(get("/api/dashboard"), sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(businessA.toString()))
                .andExpect(jsonPath("$.profile.businessType").value("retail"));

        // 9. Search scoped to Business A
        mockMvc.perform(withAuth(post("/api/search"), sessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"September\",\"type\":\"all\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value(500000));
    }

    @Test
    @DisplayName("Cross-tenant document access returns generic 404 on get, file, draft, confirm, retry, delete")
    void testCrossTenantDocumentIsolationAnd404() throws Exception {
        AppUser userA = createTestUser("docA", AccountStatus.ACTIVE);
        LoginSession sessionA = loginUser(userA);
        createBusinessForSession(sessionA, "retail");

        AppUser userB = createTestUser("docB", AccountStatus.ACTIVE);
        LoginSession sessionB = loginUser(userB);
        UUID businessB = createBusinessForSession(sessionB, "services");

        // User B uploads document
        byte[] testBytes = "%PDF-1.4\n%test pdf content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", testBytes);

        MvcResult uploadResult = mockMvc.perform(withAuth(multipart("/api/documents/upload"), sessionB)
                        .file(file)
                        .param("documentTypeHint", "invoice"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessId").value(businessB.toString()))
                .andReturn();

        String body = uploadResult.getResponse().getContentAsString();
        int idx = body.indexOf("\"id\":\"");
        UUID docBId = UUID.fromString(body.substring(idx + 6, idx + 42));

        // User A attempts to access User B's document
        mockMvc.perform(withAuth(get("/api/documents/" + docBId), sessionA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));

        mockMvc.perform(withAuth(get("/api/documents/" + docBId + "/file"), sessionA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));

        mockMvc.perform(withAuth(patch("/api/documents/" + docBId), sessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5000}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));

        mockMvc.perform(withAuth(post("/api/documents/" + docBId + "/retry"), sessionA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));

        mockMvc.perform(withAuth(delete("/api/documents/" + docBId), sessionA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    @DisplayName("Role matrix enforcement: SCORE_CALCULATE and RECORD_CREATE denied to VIEWER; SCORE_CALCULATE denied to MANAGER")
    void testRoleMatrixPermissionsEnforcement() throws Exception {
        AppUser owner = createTestUser("owner", AccountStatus.ACTIVE);
        LoginSession ownerSession = loginUser(owner);
        UUID businessId = createBusinessForSession(ownerSession, "retail");

        // Create user with VIEWER role in businessId
        AppUser viewer = createTestUser("viewer", AccountStatus.ACTIVE);
        BusinessMembership viewerMembership = new BusinessMembership(
                viewer.getId(), businessId,
                MembershipRole.VIEWER, MembershipStatus.ACTIVE
        );
        membershipRepository.saveAndFlush(viewerMembership);

        LoginSession viewerSession = loginUser(viewer);

        // Viewer selects businessId
        mockMvc.perform(withAuth(post("/api/businesses/active"), viewerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessId\":\"" + businessId + "\"}"))
                .andExpect(status().isOk());

        // VIEWER can read financial data (GET records) -> 200 OK
        mockMvc.perform(withAuth(get("/api/records/monthly"), viewerSession))
                .andExpect(status().isOk());

        // VIEWER cannot calculate scores -> 403 Forbidden
        mockMvc.perform(withAuth(post("/api/scores/calculate"), viewerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2026-09\"}"))
                .andExpect(status().isForbidden());

        // VIEWER cannot create records -> 403 Forbidden
        String recordPayload = """
                {
                    "month": "2026-09",
                    "cashInflow": 100000,
                    "cashOutflow": 50000,
                    "revenue": 100000,
                    "cogs": 30000,
                    "operatingExpenses": 20000,
                    "cashBalanceEom": 50000,
                    "financingType": "none"
                }
                """;
        mockMvc.perform(withAuth(post("/api/records/monthly"), viewerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordPayload))
                .andExpect(status().isForbidden());

        // Create user with MANAGER role in businessId
        AppUser manager = createTestUser("manager", AccountStatus.ACTIVE);
        BusinessMembership managerMembership = new BusinessMembership(
                manager.getId(), businessId,
                MembershipRole.MANAGER, MembershipStatus.ACTIVE
        );
        membershipRepository.saveAndFlush(managerMembership);

        LoginSession managerSession = loginUser(manager);
        mockMvc.perform(withAuth(post("/api/businesses/active"), managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessId\":\"" + businessId + "\"}"))
                .andExpect(status().isOk());

        // MANAGER cannot calculate scores -> 403 Forbidden
        mockMvc.perform(withAuth(post("/api/scores/calculate"), managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2026-09\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthorized business switching rejected with 403 Forbidden")
    void testUnauthorizedBusinessSwitchingDenied() throws Exception {
        AppUser userA = createTestUser("switchA", AccountStatus.ACTIVE);
        LoginSession sessionA = loginUser(userA);
        createBusinessForSession(sessionA, "retail");

        AppUser userB = createTestUser("switchB", AccountStatus.ACTIVE);
        LoginSession sessionB = loginUser(userB);
        UUID businessB = createBusinessForSession(sessionB, "services");

        // User A attempts to switch to User B's business
        mockMvc.perform(withAuth(post("/api/businesses/active"), sessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessId\":\"" + businessB + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Legacy {userId} URL paths return 404 Not Found")
    void testLegacyUrlsReturn404() throws Exception {
        AppUser user = createTestUser("legacy", AccountStatus.ACTIVE);
        LoginSession session = loginUser(user);
        createBusinessForSession(session, "retail");
        UUID dummy = UUID.randomUUID();

        mockMvc.perform(withAuth(get("/api/profile/" + dummy), session))
                .andExpect(status().isNotFound());

        mockMvc.perform(withAuth(get("/api/records/monthly/" + dummy), session))
                .andExpect(status().isNotFound());

        mockMvc.perform(withAuth(get("/api/cashflow/" + dummy), session))
                .andExpect(status().isNotFound());

        mockMvc.perform(withAuth(get("/api/scores/" + dummy + "/latest"), session))
                .andExpect(status().isNotFound());

        mockMvc.perform(withAuth(get("/api/dashboard/" + dummy), session))
                .andExpect(status().isNotFound());

        mockMvc.perform(withAuth(get("/api/insights/" + dummy), session))
                .andExpect(status().isNotFound());

        mockMvc.perform(withAuth(get("/api/recommendations/" + dummy), session))
                .andExpect(status().isNotFound());

        mockMvc.perform(withAuth(get("/api/search/" + dummy), session))
                .andExpect(status().isNotFound());

        mockMvc.perform(withAuth(post("/api/zakat/" + dummy + "/2026-09/preview"), session)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(withAuth(get("/api/whatsapp/deliveries/user/" + dummy), session))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Stateless POST /api/zakat/preview works without active business context")
    void testStatelessZakatPreviewWithoutActiveBusiness() throws Exception {
        AppUser user = createTestUser("zakatstateless", AccountStatus.ACTIVE);
        LoginSession session = loginUser(user);

        String previewPayload = """
                {
                  "assessment": {
                    "assessmentDate": "2026-09-30",
                    "currency": "PKR",
                    "nisabMetal": "SILVER",
                    "metalWeightGrams": 612.36,
                    "metalPricePerGram": 100,
                    "priceSource": "Test quotation",
                    "priceTimestamp": "2026-09-30T12:00:00+05:00",
                    "haulStatus": "CONFIRMED"
                  },
                  "assets": {
                    "cashAndBankBalances": 100000,
                    "inventory": [],
                    "receivables": [],
                    "unsupportedCategories": []
                  },
                  "liabilities": {
                    "accountsPayable": 0,
                    "currentPayables": [],
                    "principalDueWithin12LunarMonths": []
                  },
                  "financing": {
                    "financingType": "none",
                    "loanOutstanding": 0,
                    "interestExpense": 0
                  }
                }
                """;

        mockMvc.perform(withAuth(post("/api/zakat/preview"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculationStatus").value("CALCULATED"))
                .andExpect(jsonPath("$.zakatDue").value(2500));
    }
}
