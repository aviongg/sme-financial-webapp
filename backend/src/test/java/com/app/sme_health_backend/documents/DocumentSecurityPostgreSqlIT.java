package com.app.sme_health_backend.documents;

import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.processing.DocumentStartupRecovery;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.documents.storage.DocumentStorageService;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "OCR_SERVICE_SECRET=internal_ocr_dev_secret_2026",
        "INTERNAL_SERVICE_SECRET=internal_ocr_dev_secret_2026"
})
@AutoConfigureMockMvc
public class DocumentSecurityPostgreSqlIT {

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
    private MonthlyRecordRepository monthlyRecordRepository;

    @Autowired
    private DocumentStorageService storageService;

    @Autowired
    private DocumentStartupRecovery startupRecovery;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final Set<UUID> createdUserIds = new HashSet<>();
    private final Set<UUID> createdBusinessIds = new HashSet<>();
    private final Set<UUID> createdDocIds = new HashSet<>();
    private final Set<String> createdStoragePaths = new HashSet<>();

    @AfterEach
    void tearDown() {
        for (String path : createdStoragePaths) {
            try {
                storageService.delete(path);
            } catch (Exception ignored) { }
        }
        for (UUID docId : createdDocIds) {
            try {
                documentRepository.deleteById(docId);
            } catch (Exception ignored) { }
        }
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
        createdDocIds.clear();
        createdStoragePaths.clear();
    }

    private record LoginSession(Cookie sessionCookie, Cookie xsrfCookie, CsrfToken csrfToken, AppUser user, String rawPassword) {}

    private AppUser createTestUser(String prefix) {
        String email = "s4." + prefix.toLowerCase() + "." + UUID.randomUUID() + "@example.com";
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("secure-password-2026!"));
        user.setFullName("S4 User " + prefix);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        AppUser saved = userRepository.saveAndFlush(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private LoginSession loginUser(AppUser user) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie preLoginCookie = csrfResult.getResponse().getCookie("FINSIGHT_SESSION");
        Cookie xsrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        CsrfToken csrfToken = (CsrfToken) csrfResult.getRequest().getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) csrfResult.getRequest().getAttribute("_csrf");
        }

        MockHttpServletRequestBuilder loginReq = post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"secure-password-2026!\"}");

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
        assertNotNull(postLoginCookie);

        return new LoginSession(postLoginCookie, xsrfCookie, csrfToken, user, "secure-password-2026!");
    }

    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder, LoginSession session) {
        builder.cookie(session.sessionCookie());
        if (session.xsrfCookie() != null) {
            builder.cookie(session.xsrfCookie()).header("X-XSRF-TOKEN", session.xsrfCookie().getValue());
        }
        builder.with(csrf());
        return builder;
    }

    private MockMultipartHttpServletRequestBuilder withAuth(
            MockMultipartHttpServletRequestBuilder builder,
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
        int idx = responseBody.indexOf("\"businessId\":\"");
        String bIdStr = responseBody.substring(idx + 14, idx + 50);
        UUID bId = UUID.fromString(bIdStr);
        createdBusinessIds.add(bId);

        return bId;
    }

    @Test
    @DisplayName("S4: Upload persists tenant metadata, relative opaque key, and blocks cross-tenant access")
    void testUploadAndTenantIsolation() throws Exception {
        AppUser userA = createTestUser("Alice");
        LoginSession sessionA = loginUser(userA);
        UUID businessA = createBusinessForSession(sessionA, "retail");

        AppUser userB = createTestUser("Bob");
        LoginSession sessionB = loginUser(userB);
        UUID businessB = createBusinessForSession(sessionB, "services");

        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.png",
                "image/png",
                pngBytes
        );

        // 1. Upload persists tenant-owned metadata
        MvcResult uploadResult = mockMvc.perform(withAuth(multipart("/api/documents/upload"), sessionA)
                        .file(file)
                        .param("documentTypeHint", "receipt"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.processingStatus").value("pending"))
                .andReturn();

        JsonNode uploadJson = new JsonMapper().readTree(uploadResult.getResponse().getContentAsString());
        UUID docId = UUID.fromString(uploadJson.get("id").stringValue());
        createdDocIds.add(docId);

        UploadedDocument doc = documentRepository.findById(docId).orElseThrow();
        assertEquals(businessA, doc.getUserId(), "Document must be owned by tenant Business A");
        createdStoragePaths.add(doc.getStoragePath());

        // 2. Storage entry corresponds to correct document/business and uses server-generated relative key
        assertTrue(storageService.exists(doc.getStoragePath()));
        assertTrue(doc.getStoragePath().startsWith(businessA.toString()),
                "Storage path must derive from server-controlled businessId prefix");
        assertFalse(doc.getStoragePath().contains(".."), "Storage path must not contain traversal tokens");
        assertFalse(doc.getStoragePath().startsWith("/"), "Storage path must be stored as a relative key");
        assertFalse(doc.getStoragePath().contains(":"), "Storage path must not expose drive letters");
        assertArrayEquals(pngBytes, storageService.loadBytes(doc.getStoragePath()));

        // Verify API response never exposes physical filesystem paths
        assertFalse(uploadResult.getResponse().getContentAsString().contains("C:"));
        assertFalse(uploadResult.getResponse().getContentAsString().contains("\\"));
        assertFalse(uploadResult.getResponse().getContentAsString().contains("/var/finsight"));

        // 3. Browser delivery for active tenant includes safe headers
        mockMvc.perform(withAuth(get("/api/documents/" + docId + "/file"), sessionA))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(pngBytes))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));

        // 4. Cross-tenant access: Tenant B returns generic 404 for metadata and file
        mockMvc.perform(withAuth(get("/api/documents/" + docId), sessionB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));

        mockMvc.perform(withAuth(get("/api/documents/" + docId + "/file"), sessionB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));

        // 5. Old OCR callback bypass eliminated: X-Internal-Service-Key without session cannot access file
        mockMvc.perform(get("/api/documents/" + docId + "/file")
                        .header("X-Internal-Service-Key", "internal_ocr_dev_secret_2026"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("S4: Document confirmation, retry, and deletion retain full lifecycle semantics")
    void testDocumentLifecycleAndCompensation() throws Exception {
        AppUser user = createTestUser("Charlie");
        LoginSession session = loginUser(user);
        UUID businessId = createBusinessForSession(session, "retail");

        MonthlyRecord baseRecord = new MonthlyRecord();
        baseRecord.setUserId(businessId);
        baseRecord.setMonth("2026-09");
        baseRecord.setRevenue(new BigDecimal("100000.00"));
        baseRecord.setOperatingExpenses(new BigDecimal("40000.00"));
        baseRecord.setCogs(new BigDecimal("20000.00"));
        baseRecord.setCashInflow(new BigDecimal("100000.00"));
        baseRecord.setCashOutflow(new BigDecimal("60000.00"));
        baseRecord.setCashBalanceEom(new BigDecimal("40000.00"));
        baseRecord.setFinancingType("none");
        baseRecord.setUpdatedAt(LocalDateTime.now());
        monthlyRecordRepository.save(baseRecord);

        byte[] pdfBytes = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invoice.pdf",
                "application/pdf",
                pdfBytes
        );

        MvcResult uploadResult = mockMvc.perform(withAuth(multipart("/api/documents/upload"), session)
                        .file(file)
                        .param("documentTypeHint", "invoice"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode uploadJson = new JsonMapper().readTree(uploadResult.getResponse().getContentAsString());
        UUID docId = UUID.fromString(uploadJson.get("id").stringValue());
        createdDocIds.add(docId);

        UploadedDocument doc = documentRepository.findById(docId).orElseThrow();
        createdStoragePaths.add(doc.getStoragePath());

        // 1. Simulate extracted draft status with JSONB extracted data
        doc.setProcessingStatus(DocumentStatus.extracted);
        doc.setExtractedData("{\"date\":\"2026-09-15\",\"amount\":5000.00,\"vendor_or_party\":\"Acme Supplies\",\"category\":\"purchase\",\"confidence\":\"high\",\"document_type_detected\":\"invoice\"}");
        documentRepository.save(doc);

        mockMvc.perform(withAuth(post("/api/documents/" + docId + "/confirm"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "targetMonth": "2026-09",
                                    "confirmedAmount": 5000.00,
                                    "targetClassification": "cogs",
                                    "cashFlowImpact": "cash_outflow",
                                    "initialCashBalanceEom": 10000.00,
                                    "confirmedDate": "2026-09-15",
                                    "confirmedParty": "Acme Supplies"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").value("confirmed"));

        UploadedDocument confirmedDoc = documentRepository.findById(docId).orElseThrow();
        assertEquals(DocumentStatus.confirmed, confirmedDoc.getProcessingStatus());
        assertNotNull(confirmedDoc.getConfirmedAt());

        // 3. Deletion test on a non-confirmed draft: compensating storage cleanup
        MockMultipartFile draftFile = new MockMultipartFile("file", "draft.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        MvcResult draftUpload = mockMvc.perform(withAuth(multipart("/api/documents/upload"), session)
                        .file(draftFile))
                .andExpect(status().isCreated())
                .andReturn();

        UUID draftId = UUID.fromString(new JsonMapper().readTree(draftUpload.getResponse().getContentAsString()).get("id").stringValue());
        UploadedDocument draftDoc = documentRepository.findById(draftId).orElseThrow();
        String draftStoragePath = draftDoc.getStoragePath();
        createdStoragePaths.add(draftStoragePath);
        assertTrue(storageService.exists(draftStoragePath));

        mockMvc.perform(withAuth(delete("/api/documents/" + draftId), session))
                .andExpect(status().isNoContent());

        assertFalse(documentRepository.existsById(draftId), "Draft metadata must be deleted from DB");
        assertFalse(storageService.exists(draftStoragePath), "Draft storage file must be deleted from storage");

        // 4. Retry workflow remains functional
        UploadedDocument failedDoc = new UploadedDocument();
        UUID failedId = UUID.randomUUID();
        failedDoc.setId(failedId);
        failedDoc.setUserId(businessId);
        failedDoc.setFileUrl("http://localhost:8080/api/documents/" + failedId + "/file");
        failedDoc.setStoragePath(doc.getStoragePath());
        failedDoc.setOriginalFilename("retry.pdf");
        failedDoc.setContentType("application/pdf");
        failedDoc.setProcessingStatus(DocumentStatus.failed);
        failedDoc.setFailureReason("unavailable");
        failedDoc.setUploadTimestamp(LocalDateTime.now());
        documentRepository.save(failedDoc);
        createdDocIds.add(failedId);

        mockMvc.perform(withAuth(post("/api/documents/" + failedId + "/retry"), session))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processingStatus").value("pending"));

        // 5. Stale processing recovery
        failedDoc.setProcessingStatus(DocumentStatus.processing);
        failedDoc.setProcessingStartedAt(LocalDateTime.now().minusHours(2));
        documentRepository.save(failedDoc);

        startupRecovery.recoverStaleProcessingDocuments();

        UploadedDocument recovered = documentRepository.findById(failedId).orElseThrow();
        assertEquals(DocumentStatus.failed, recovered.getProcessingStatus());
        assertEquals("processing_interrupted", recovered.getFailureReason());
    }
}
