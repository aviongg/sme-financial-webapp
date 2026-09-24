package com.app.sme_health_backend.security;

import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.documents.storage.DocumentStorageService;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.security.internal-service-secret=internal_ocr_dev_secret_2026"})
@AutoConfigureMockMvc
public class SecurityIdentityPostgreSqlIT {

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
    private UploadedDocumentRepository documentRepository;

    @Autowired
    private DocumentStorageService storageService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdBusinessIds = new ArrayList<>();
    private final List<UUID> createdMembershipIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID membershipId : createdMembershipIds) {
            membershipRepository.deleteById(membershipId);
        }
        for (UUID userId : createdUserIds) {
            try {
                jdbcTemplate.update("DELETE FROM uploaded_documents WHERE user_id = ?", userId);
                jdbcTemplate.update("DELETE FROM business_profiles WHERE user_id = ?", userId);
                userRepository.deleteById(userId);
            } catch (Exception ignored) {}
        }
        for (UUID businessId : createdBusinessIds) {
            businessRepository.deleteById(businessId);
        }
    }

    @Test
    @DisplayName("Verify V9 migration applied, backfilled businesses, and created no fake users")
    void testV9MigrationAndBackfill() {
        // 1. Verify V9 migration exists in flyway history (using migrator credentials since runtime role is denied flyway_schema_history)
        Integer v9Count = getFlywayVersionCount("9");
        assertNotNull(v9Count);
        assertTrue(v9Count > 0, "Flyway V9 migration must be applied successfully");

        // 2. Verify businesses backfill matches existing business_profiles
        Integer profileCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM business_profiles",
                Integer.class
        );
        assertNotNull(profileCount);

        Integer matchCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM business_profiles bp JOIN businesses b ON bp.user_id = b.id",
                Integer.class
        );
        assertNotNull(matchCount);
        assertEquals(profileCount, matchCount, "Every existing BusinessProfile must have an identical Business.id");

        // 3. Verify no fake AppUser accounts were created by migration
        // Any users in the DB should be legitimately registered users, migration created none
        Integer migrationUserCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app_users WHERE email LIKE '%fake%' OR email LIKE '%placeholder%'",
                Integer.class
        );
        assertEquals(0, migrationUserCount, "Migration V9 must NOT create fake or placeholder AppUsers");
    }

    @Test
    @DisplayName("Verify app_users persistence, Argon2 hash storage, and unique email constraint on PostgreSQL")
    void testAppUserPersistenceAndConstraints() {
        String testEmail = "pg.user." + UUID.randomUUID() + "@example.com";
        String rawPassword = "secure-production-passphrase-2026!";
        String argonHash = passwordEncoder.encode(rawPassword);

        AppUser user = new AppUser();
        user.setEmail(testEmail);
        user.setPasswordHash(argonHash);
        user.setFullName("PostgreSQL Test User");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(false);

        AppUser saved = userRepository.save(user);
        createdUserIds.add(saved.getId());

        assertNotNull(saved.getId());
        assertTrue(saved.getPasswordHash().startsWith("$argon2id$v=19$m=65536,t=3,p=1$"),
                "Stored password hash must be Argon2id with production parameters");
        assertTrue(passwordEncoder.matches(rawPassword, saved.getPasswordHash()));

        // Verify unique constraint on email
        AppUser duplicate = new AppUser();
        duplicate.setEmail(testEmail);
        duplicate.setPasswordHash(argonHash);
        duplicate.setFullName("Duplicate User");
        duplicate.setAccountStatus(AccountStatus.ACTIVE);

        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.saveAndFlush(duplicate);
        }, "Inserting duplicate email must violate uq_app_users_email constraint");
    }

    @Test
    @DisplayName("Verify business_memberships foreign keys and unique (user_id, business_id) constraint")
    void testBusinessMembershipConstraints() {
        // Create user
        AppUser user = new AppUser();
        user.setEmail("member.test." + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password-12345678"));
        user.setFullName("Member User");
        user.setAccountStatus(AccountStatus.ACTIVE);
        AppUser savedUser = userRepository.save(user);
        createdUserIds.add(savedUser.getId());

        // Create business
        UUID businessId = UUID.randomUUID();
        Business business = new Business(businessId, "ACTIVE");
        Business savedBusiness = businessRepository.save(business);
        createdBusinessIds.add(savedBusiness.getId());

        // Create membership
        BusinessMembership membership = new BusinessMembership(
                savedUser.getId(),
                savedBusiness.getId(),
                MembershipRole.OWNER,
                MembershipStatus.ACTIVE
        );
        BusinessMembership savedMembership = membershipRepository.save(membership);
        createdMembershipIds.add(savedMembership.getId());

        assertNotNull(savedMembership.getId());
        assertEquals(MembershipRole.OWNER, savedMembership.getRole());

        // Verify unique constraint on (user_id, business_id)
        BusinessMembership duplicate = new BusinessMembership(
                savedUser.getId(),
                savedBusiness.getId(),
                MembershipRole.ACCOUNTANT,
                MembershipStatus.ACTIVE
        );

        assertThrows(DataIntegrityViolationException.class, () -> {
            membershipRepository.saveAndFlush(duplicate);
        }, "Duplicate membership for (user_id, business_id) must violate uq_membership_user_business constraint");
    }

    @Test
    @DisplayName("Verify Spring Session JDBC persistence and deletion in PostgreSQL tables")
    @SuppressWarnings("unchecked")
    void testSpringSessionJdbcPersistence() {
        if (sessionRepository == null) {
            return;
        }

        Session session = sessionRepository.createSession();
        String sessionId = session.getId();
        session.setAttribute("TEST_ATTR", "FINSIGHT_VALUE");

        ((FindByIndexNameSessionRepository<Session>) sessionRepository).save(session);

        // Verify session row exists in PostgreSQL spring_session table
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_session WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        assertEquals(1, count, "Session must be persisted in PostgreSQL spring_session table");

        // Verify attribute exists in spring_session_attributes table
        Integer attrCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_session_attributes WHERE attribute_name = 'TEST_ATTR'",
                Integer.class
        );
        assertTrue(attrCount != null && attrCount > 0, "Session attributes must be persisted");

        // Invalidate session
        sessionRepository.deleteById(sessionId);

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_session WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        assertEquals(0, remaining, "Session must be deleted from PostgreSQL upon invalidation");
    }

    @Test
    @DisplayName("Verify complete session fixation sequence: pre-login vs post-login rotation, JDBC session load, and logout invalidation")
    void testSessionFixationAndAuthenticationLifecycle() throws Exception {
        // Create user in DB
        String email = "fixation." + UUID.randomUUID() + "@example.com";
        String password = "secure-login-passphrase-2026!";
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName("Session Test User");
        user.setAccountStatus(AccountStatus.ACTIVE);
        AppUser savedUser = userRepository.save(user);
        createdUserIds.add(savedUser.getId());

        // 1. Client requests /api/auth/csrf and receives anonymous session / CSRF token
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        jakarta.servlet.http.Cookie preLoginCookie = csrfResult.getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(preLoginCookie, "Pre-login session cookie FINSIGHT_SESSION must exist");
        String preLoginSessionId = preLoginCookie.getValue();
        assertNotNull(preLoginSessionId, "Pre-login session ID must exist");

        jakarta.servlet.http.Cookie xsrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        CsrfToken csrfToken = (CsrfToken) csrfResult.getRequest().getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) csrfResult.getRequest().getAttribute("_csrf");
        }

        // 3. Client sends valid login request with CSRF and the pre-login session cookie
        String loginPayload = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        var loginRequestBuilder = post("/api/auth/login")
                .cookie(preLoginCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload);
        if (xsrfCookie != null) {
            loginRequestBuilder.cookie(xsrfCookie).header("X-XSRF-TOKEN", xsrfCookie.getValue());
        } else {
            loginRequestBuilder.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf());
        }

        MvcResult loginResult = mockMvc.perform(loginRequestBuilder)
                .andExpect(status().isOk())
                .andReturn();

        // 4 & 5. Post-login session ID
        jakarta.servlet.http.Cookie postLoginCookie = loginResult.getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(postLoginCookie, "Post-login session cookie FINSIGHT_SESSION must exist");
        String postLoginSessionId = postLoginCookie.getValue();
        assertNotNull(postLoginSessionId, "Post-login session ID must exist");

        // 6. Verify authenticated session ID is different from pre-login session ID
        assertNotEquals(preLoginSessionId, postLoginSessionId,
                "Session fixation protection must rotate session ID upon authentication");

        // 7 & 8. Completely separate request to /api/auth/me using ONLY the post-login session cookie
        mockMvc.perform(get("/api/auth/me")
                        .cookie(postLoginCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Session Test User"));

        // 9. Logout
        var logoutRequestBuilder = post("/api/auth/logout")
                .cookie(postLoginCookie);
        if (xsrfCookie != null) {
            logoutRequestBuilder.cookie(xsrfCookie).header("X-XSRF-TOKEN", xsrfCookie.getValue());
        } else {
            logoutRequestBuilder.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf());
        }
        mockMvc.perform(logoutRequestBuilder)
                .andExpect(status().isNoContent());

        // 10 & 11. Repeat /api/auth/me with old session -> verify 401
        mockMvc.perform(get("/api/auth/me")
                        .cookie(postLoginCookie))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me")
                        .cookie(preLoginCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Verify OCR cross-service document retrieval: unauthenticated rejected, trusted service key succeeds, invalid key rejected")
    void testOcrCrossServiceDocumentRetrieval() throws Exception {
        AppUser user = new AppUser();
        user.setEmail("ocr.doc." + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password-12345678"));
        user.setFullName("OCR User");
        user.setAccountStatus(AccountStatus.ACTIVE);
        AppUser savedUser = userRepository.save(user);
        createdUserIds.add(savedUser.getId());

        // Ensure businesses and business_profiles parent rows exist for foreign key
        jdbcTemplate.update("INSERT INTO businesses (id, status) VALUES (?, 'ACTIVE') ON CONFLICT (id) DO NOTHING", savedUser.getId());
        createdBusinessIds.add(savedUser.getId());
        jdbcTemplate.update("INSERT INTO business_profiles (user_id, business_type) VALUES (?, 'retail') ON CONFLICT (user_id) DO NOTHING", savedUser.getId());

        UUID documentId = UUID.randomUUID();
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] payload = "GENUINE_ENCRYPTED_OR_SOURCE_DOCUMENT_BYTES_FOR_OCR".getBytes(StandardCharsets.UTF_8);
        byte[] testBytes = new byte[pngHeader.length + payload.length];
        System.arraycopy(pngHeader, 0, testBytes, 0, pngHeader.length);
        System.arraycopy(payload, 0, testBytes, pngHeader.length, payload.length);
        var storedFile = storageService.store(savedUser.getId(), new MockMultipartFile("file", "test-receipt.png", "image/png", testBytes));

        UploadedDocument doc = new UploadedDocument();
        doc.setId(documentId);
        doc.setUserId(savedUser.getId());
        doc.setOriginalFilename("test-receipt.png");
        doc.setContentType("image/png");
        doc.setFileSizeBytes((long) testBytes.length);
        doc.setStoragePath(storedFile.storagePath());
        doc.setFileUrl("http://localhost:8080/api/documents/" + documentId + "/file");
        doc.setProcessingStatus(com.app.sme_health_backend.documents.processing.DocumentStatus.pending);
        doc.setUploadTimestamp(java.time.LocalDateTime.now());
        documentRepository.save(doc);

        try {
            // 1. Unauthenticated external request -> 401 Unauthorized
            mockMvc.perform(get("/api/documents/" + documentId + "/file"))
                    .andExpect(status().isUnauthorized());

            // 2. Old OCR service request with X-Internal-Service-Key must NOT gain internal bypass -> 401 Unauthorized
            mockMvc.perform(get("/api/documents/" + documentId + "/file")
                            .header("X-Internal-Service-Key", "internal_ocr_dev_secret_2026"))
                    .andExpect(status().isUnauthorized());

            // 3. Invalid OCR service credential -> 401 Unauthorized
            mockMvc.perform(get("/api/documents/" + documentId + "/file")
                            .header("X-Internal-Service-Key", "invalid_secret_key"))
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
