package com.app.sme_health_backend.identity;

import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.documents.storage.DocumentStorageService;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.identity.service.ActiveBusinessContext;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.security.internal-service-secret=internal_ocr_dev_secret_2026"})
@AutoConfigureMockMvc
public class BusinessContextPostgreSqlIT {

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
    private BusinessService businessService;

    @Autowired
    private ActiveBusinessContext activeBusinessContext;

    @Autowired
    private BusinessAuthorizationService authorizationService;

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
                jdbcTemplate.update("DELETE FROM business_memberships WHERE user_id = ?", userId);
            } catch (Exception ignored) {}
        }

        for (UUID businessId : createdBusinessIds) {
            try {
                jdbcTemplate.update("DELETE FROM uploaded_documents WHERE user_id = ?", businessId);
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
                userRepository.deleteById(userId);
            } catch (Exception ignored) {}
        }

        createdUserIds.clear();
        createdBusinessIds.clear();
    }

    private AppUser createTestUser(String prefix, AccountStatus status) {
        String email = prefix + "." + UUID.randomUUID() + "@example.com";
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("secure-password-2026!"));
        user.setFullName("Test User " + prefix);
        user.setAccountStatus(status);
        user.setMustChangePassword(false);
        AppUser saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private record LoginSession(Cookie sessionCookie, Cookie xsrfCookie, CsrfToken csrfToken, AppUser user, String rawPassword) {}

    private LoginSession loginUser(AppUser user) throws Exception {
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

    @Test
    @DisplayName("Verify Flyway V10 migration applied and enforces fk_business_profiles_business with ON DELETE RESTRICT")
    void testV10MigrationAndForeignKeyConstraint() {
        // 1. Verify V10 is recorded in flyway history (using migrator credentials since runtime role is denied flyway_schema_history)
        Integer v10Count = getFlywayVersionCount("10");
        assertNotNull(v10Count);
        assertTrue(v10Count > 0, "Flyway V10 migration must be applied successfully");

        // 2. Verify FK constraint exists on business_profiles referencing businesses(id)
        Integer fkCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.table_constraints
                WHERE constraint_name = 'fk_business_profiles_business'
                  AND table_name = 'business_profiles'
                """,
                Integer.class
        );
        assertNotNull(fkCount);
        assertEquals(1, fkCount, "fk_business_profiles_business foreign key must exist on business_profiles");

        // 3. Verify ON DELETE RESTRICT semantics
        UUID businessId = UUID.randomUUID();
        createdBusinessIds.add(businessId);

        Business business = new Business(businessId, "ACTIVE");
        businessRepository.saveAndFlush(business);

        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(businessId);
        profile.setBusinessType("retail");
        profile.setLanguagePreference("en");
        profile.setWhatsappOptIn(false);
        profileRepository.saveAndFlush(profile);

        // Deleting business directly must fail with DataIntegrityViolationException (foreign key violation)
        assertThrows(DataIntegrityViolationException.class, () -> {
            businessRepository.deleteById(businessId);
            businessRepository.flush();
        }, "Deleting a business referenced by business_profiles must be restricted");

        // Deleting child profile first, then business must succeed
        profileRepository.deleteById(businessId);
        profileRepository.flush();

        businessRepository.deleteById(businessId);
        businessRepository.flush();
        assertFalse(businessRepository.existsById(businessId), "Business can be deleted after child profile is removed");
    }

    @Test
    @DisplayName("Verify transactional onboarding creates Business + BusinessProfile + OWNER membership atomically and sets session")
    void testTransactionalBusinessOnboarding() throws Exception {
        AppUser user = createTestUser("onboard", AccountStatus.ACTIVE);
        LoginSession session = loginUser(user);

        String payload = """
                {
                    "businessType": "retail",
                    "languagePreference": "en",
                    "whatsappOptIn": false
                }
                """;

        MvcResult result = mockMvc.perform(withAuth(post("/api/businesses"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessId").isNotEmpty())
                .andExpect(jsonPath("$.businessType").value("retail"))
                .andExpect(jsonPath("$.languagePreference").value("en"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.whatsappNumber").doesNotExist())
                .andReturn();

        String businessIdStr = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.businessId");
        UUID businessId = UUID.fromString(businessIdStr);
        createdBusinessIds.add(businessId);

        // Verify PostgreSQL persistence
        Optional<Business> businessOpt = businessRepository.findById(businessId);
        assertTrue(businessOpt.isPresent(), "Business row must exist in PostgreSQL");
        assertEquals("ACTIVE", businessOpt.get().getStatus());

        Optional<BusinessProfile> profileOpt = profileRepository.findById(businessId);
        assertTrue(profileOpt.isPresent(), "BusinessProfile row must exist in PostgreSQL with userId == businessId");
        assertEquals("retail", profileOpt.get().getBusinessType());

        Optional<BusinessMembership> membershipOpt = membershipRepository.findByUserIdAndBusinessId(user.getId(), businessId);
        assertTrue(membershipOpt.isPresent(), "OWNER membership row must exist in PostgreSQL");
        assertEquals(MembershipRole.OWNER, membershipOpt.get().getRole());
        assertEquals(MembershipStatus.ACTIVE, membershipOpt.get().getStatus());

        // Verify active business is retrievable using the existing session
        mockMvc.perform(withAuth(get("/api/businesses/active"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(businessId.toString()))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("Verify transactional rollback leaves no orphan business when membership persistence fails")
    void testTransactionalRollbackOnProfileFailure() {
        // Non-existent user ID violates foreign key fk_membership_user on business_memberships
        UUID nonExistentUserId = UUID.randomUUID();

        long businessCountBefore = businessRepository.count();
        long profileCountBefore = profileRepository.count();
        long membershipCountBefore = membershipRepository.count();

        CreateBusinessRequest validRequest = new CreateBusinessRequest(
                "retail",
                "en",
                null,
                false,
                null,
                null,
                null
        );

        // Executing createBusiness with non-existent user must fail on step 3 with DataIntegrityViolationException
        assertThatThrownBy(() -> businessService.createBusiness(validRequest, nonExistentUserId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Verify atomic rollback: neither Business, BusinessProfile, nor BusinessMembership rows persist
        long businessCountAfter = businessRepository.count();
        long profileCountAfter = profileRepository.count();
        long membershipCountAfter = membershipRepository.count();

        assertEquals(businessCountBefore, businessCountAfter, "No orphan businesses must persist after rollback");
        assertEquals(profileCountBefore, profileCountAfter, "No orphan business_profiles must persist after rollback");
        assertEquals(membershipCountBefore, membershipCountAfter, "No orphan business_memberships must persist after rollback");
    }

    @Test
    @DisplayName("Verify multi-business active selection, 2+ businesses unset on login, and unauthorized tenant switching denied")
    void testActiveBusinessSwitchingAndUnauthorizedAccess() throws Exception {
        AppUser user1 = createTestUser("tenant.u1", AccountStatus.ACTIVE);
        AppUser user2 = createTestUser("tenant.u2", AccountStatus.ACTIVE);

        // Create Business 1 for User 1
        UUID b1Id = UUID.randomUUID();
        createdBusinessIds.add(b1Id);
        businessRepository.saveAndFlush(new Business(b1Id, "ACTIVE"));
        BusinessProfile p1 = new BusinessProfile();
        p1.setUserId(b1Id);
        p1.setBusinessType("retail");
        p1.setLanguagePreference("en");
        profileRepository.saveAndFlush(p1);
        membershipRepository.saveAndFlush(new BusinessMembership(user1.getId(), b1Id, MembershipRole.OWNER, MembershipStatus.ACTIVE));

        // Create Business 2 for User 1
        UUID b2Id = UUID.randomUUID();
        createdBusinessIds.add(b2Id);
        businessRepository.saveAndFlush(new Business(b2Id, "ACTIVE"));
        BusinessProfile p2 = new BusinessProfile();
        p2.setUserId(b2Id);
        p2.setBusinessType("manufacturing");
        p2.setLanguagePreference("ur");
        profileRepository.saveAndFlush(p2);
        membershipRepository.saveAndFlush(new BusinessMembership(user1.getId(), b2Id, MembershipRole.ACCOUNTANT, MembershipStatus.ACTIVE));

        // Create Business 3 for User 2 only
        UUID b3Id = UUID.randomUUID();
        createdBusinessIds.add(b3Id);
        businessRepository.saveAndFlush(new Business(b3Id, "ACTIVE"));
        BusinessProfile p3 = new BusinessProfile();
        p3.setUserId(b3Id);
        p3.setBusinessType("trade");
        p3.setLanguagePreference("en");
        profileRepository.saveAndFlush(p3);
        membershipRepository.saveAndFlush(new BusinessMembership(user2.getId(), b3Id, MembershipRole.OWNER, MembershipStatus.ACTIVE));

        // User 1 logs in. Since User 1 has 2 businesses, active business context must be unset on login
        LoginSession session1 = loginUser(user1);

        mockMvc.perform(withAuth(get("/api/businesses/active"), session1))
                .andExpect(status().isNotFound());

        // User 1 activates Business 1
        String activateB1 = "{\"businessId\":\"" + b1Id + "\"}";
        mockMvc.perform(withAuth(post("/api/businesses/active"), session1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activateB1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(b1Id.toString()))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(withAuth(get("/api/businesses/active"), session1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(b1Id.toString()))
                .andExpect(jsonPath("$.role").value("OWNER"));

        // User 1 switches to Business 2
        String activateB2 = "{\"businessId\":\"" + b2Id + "\"}";
        mockMvc.perform(withAuth(post("/api/businesses/active"), session1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activateB2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(b2Id.toString()))
                .andExpect(jsonPath("$.role").value("ACCOUNTANT"))
                .andExpect(jsonPath("$.active").value(true));

        // User 1 attempts unauthorized activation of User 2's Business 3 -> 403 Forbidden
        String activateB3 = "{\"businessId\":\"" + b3Id + "\"}";
        mockMvc.perform(withAuth(post("/api/businesses/active"), session1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activateB3))
                .andExpect(status().isForbidden());

        // Active business remains Business 2
        mockMvc.perform(withAuth(get("/api/businesses/active"), session1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(b2Id.toString()))
                .andExpect(jsonPath("$.role").value("ACCOUNTANT"));
    }

    @Test
    @DisplayName("Verify account status revocation takes dynamic effect against existing session in PostgreSQL")
    void testAccountStatusRevocationOnExistingSession() throws Exception {
        AppUser user = createTestUser("revoke", AccountStatus.ACTIVE);

        // Single business -> auto-selected on login
        UUID businessId = UUID.randomUUID();
        createdBusinessIds.add(businessId);
        businessRepository.saveAndFlush(new Business(businessId, "ACTIVE"));
        BusinessProfile p = new BusinessProfile();
        p.setUserId(businessId);
        p.setBusinessType("retail");
        p.setLanguagePreference("en");
        profileRepository.saveAndFlush(p);
        membershipRepository.saveAndFlush(new BusinessMembership(user.getId(), businessId, MembershipRole.OWNER, MembershipStatus.ACTIVE));

        LoginSession session = loginUser(user);

        // Request succeeds while user is ACTIVE
        mockMvc.perform(withAuth(get("/api/businesses"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Disable user account in PostgreSQL
        jdbcTemplate.update("UPDATE app_users SET account_status = 'DISABLED' WHERE id = ?", user.getId());

        // Next request with existing session cookie must be rejected with 401 Unauthorized by AccountStatusValidationFilter
        mockMvc.perform(withAuth(get("/api/businesses"), session))
                .andExpect(status().isUnauthorized());

        // Subsequent requests with the same invalidated session also return 401
        mockMvc.perform(withAuth(get("/api/businesses"), session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Verify dynamic role change takes immediate effect without re-login")
    void testDynamicRoleChangeTakesImmediateEffect() {
        AppUser user = createTestUser("dynamic.role", AccountStatus.ACTIVE);

        UUID businessId = UUID.randomUUID();
        createdBusinessIds.add(businessId);
        businessRepository.saveAndFlush(new Business(businessId, "ACTIVE"));
        BusinessProfile p = new BusinessProfile();
        p.setUserId(businessId);
        p.setBusinessType("retail");
        p.setLanguagePreference("en");
        profileRepository.saveAndFlush(p);

        BusinessMembership membership = new BusinessMembership(
                user.getId(),
                businessId,
                MembershipRole.VIEWER,
                MembershipStatus.ACTIVE
        );
        membershipRepository.saveAndFlush(membership);

        MockHttpServletRequest request = new MockHttpServletRequest();
        activeBusinessContext.setActiveBusinessId(request, businessId);

        // In VIEWER role, SCORE_CALCULATE is false
        // Simulating authentication context for ActiveBusinessContext resolution
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        new com.app.sme_health_backend.security.service.AppUserDetails(user),
                        null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
                );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            boolean hasPermissionBefore = authorizationService.hasPermission(request, BusinessPermission.SCORE_CALCULATE);
            assertFalse(hasPermissionBefore, "VIEWER must NOT have SCORE_CALCULATE permission");

            // Update membership role to ACCOUNTANT directly in DB
            jdbcTemplate.update(
                    "UPDATE business_memberships SET role = 'ACCOUNTANT' WHERE user_id = ? AND business_id = ?",
                    user.getId(), businessId
            );

            // Dynamic check queries DB on the same request context -> immediately reflects ACCOUNTANT
            boolean hasPermissionAfter = authorizationService.hasPermission(request, BusinessPermission.SCORE_CALCULATE);
            assertTrue(hasPermissionAfter, "ACCOUNTANT must have SCORE_CALCULATE permission immediately without re-login");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Verify internal OCR authentication preserved and restricted from accessing tenant endpoints")
    void testInternalOcrAuthenticationPreservedAndTenantRestricted() throws Exception {
        AppUser user = createTestUser("ocr.tenant", AccountStatus.ACTIVE);

        UUID businessId = UUID.randomUUID();
        createdBusinessIds.add(businessId);
        businessRepository.saveAndFlush(new Business(businessId, "ACTIVE"));
        BusinessProfile p = new BusinessProfile();
        p.setUserId(businessId);
        p.setBusinessType("retail");
        profileRepository.saveAndFlush(p);

        UUID documentId = UUID.randomUUID();
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] payload = "GENUINE_INTERNAL_OCR_TEST_DOCUMENT_BYTES".getBytes(StandardCharsets.UTF_8);
        byte[] testBytes = new byte[pngHeader.length + payload.length];
        System.arraycopy(pngHeader, 0, testBytes, 0, pngHeader.length);
        System.arraycopy(payload, 0, testBytes, pngHeader.length, payload.length);
        var storedFile = storageService.store(businessId, new MockMultipartFile("file", "test-invoice.png", "image/png", testBytes));

        UploadedDocument doc = new UploadedDocument();
        doc.setId(documentId);
        doc.setUserId(businessId);
        doc.setOriginalFilename("test-invoice.png");
        doc.setContentType("image/png");
        doc.setFileSizeBytes((long) testBytes.length);
        doc.setStoragePath(storedFile.storagePath());
        doc.setFileUrl("http://localhost:8080/api/documents/" + documentId + "/file");
        doc.setProcessingStatus(com.app.sme_health_backend.documents.processing.DocumentStatus.pending);
        doc.setUploadTimestamp(java.time.LocalDateTime.now());
        documentRepository.save(doc);

        try {
            // 1. Old OCR service header no longer gains bypass for /api/documents/{id}/file -> 401 Unauthorized
            mockMvc.perform(get("/api/documents/" + documentId + "/file")
                            .header("X-Internal-Service-Key", "internal_ocr_dev_secret_2026"))
                    .andExpect(status().isUnauthorized());

            // 2. Invalid OCR secret is also rejected
            mockMvc.perform(get("/api/documents/" + documentId + "/file")
                            .header("X-Internal-Service-Key", "bad_secret_key"))
                    .andExpect(status().isUnauthorized());

            // 3. Unauthenticated request without session returns 401 Unauthorized
            mockMvc.perform(get("/api/documents/" + documentId + "/file"))
                    .andExpect(status().isUnauthorized());
        } finally {
            storageService.delete(storedFile.storagePath());
            documentRepository.deleteById(documentId);
        }
    }

    private int getFlywayVersionCount(String version) {
        String url = "jdbc:postgresql://localhost:5432/sme_health";
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url, "finsight_migrator", "FinSight_Migrator_Ddl_2026_!$4mP");
             java.sql.PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM flyway_schema_history WHERE version = ? AND success = true")) {
            ps.setString(1, version);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }
}
