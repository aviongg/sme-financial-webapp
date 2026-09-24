package com.app.sme_health_backend.crypto;

import com.app.sme_health_backend.crypto.migration.CryptoMigrationReport;
import com.app.sme_health_backend.crypto.migration.CryptoMigrationService;
import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDelivery;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus;
import com.app.sme_health_backend.whatsapp.repository.WhatsAppDeliveryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "app.security.internal-service-secret=internal_ocr_dev_secret_2026",
        "finsight.crypto.allow-legacy-plaintext=true"
})
public class EncryptedEntityPostgreSqlIT {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private BusinessProfileRepository businessProfileRepository;

    @Autowired
    private WhatsAppDeliveryRepository whatsAppDeliveryRepository;

    @Autowired
    private UploadedDocumentRepository uploadedDocumentRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CryptoProperties cryptoProperties;

    @Autowired
    private LocalKeyringProvider keyringProvider;

    @Autowired
    private CryptoMigrationService cryptoMigrationService;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdDocIds = new ArrayList<>();
    private final List<UUID> createdDeliveryIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        cryptoProperties.setAllowLegacyPlaintext(true);

        for (UUID docId : createdDocIds) {
            uploadedDocumentRepository.deleteById(docId);
        }
        for (UUID deliveryId : createdDeliveryIds) {
            whatsAppDeliveryRepository.deleteById(deliveryId);
        }
        for (UUID userId : createdUserIds) {
            businessProfileRepository.deleteById(userId);
            businessRepository.deleteById(userId);
            appUserRepository.deleteById(userId);
        }
        createdDocIds.clear();
        createdDeliveryIds.clear();
        createdUserIds.clear();
    }

    @Test
    @DisplayName("Verify AppUser.fullName raw DB ciphertext vs JPA plaintext")
    void testAppUserFullNameEncryption() {
        String plaintextName = "Fatima Jinnah";
        AppUser user = new AppUser();
        user.setEmail("crypto-user-" + UUID.randomUUID() + "@test.com");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setFullName(plaintextName);
        user.setAccountStatus(AccountStatus.ACTIVE);

        AppUser saved = appUserRepository.saveAndFlush(user);
        createdUserIds.add(saved.getId());

        // 1. Direct SQL inspection
        String rawDbValue = jdbcTemplate.queryForObject(
                "SELECT full_name FROM app_users WHERE id = ?",
                String.class,
                saved.getId()
        );

        assertNotNull(rawDbValue);
        assertTrue(rawDbValue.startsWith("enc:v1:k1:"), "Raw column must store encrypted envelope: " + rawDbValue);
        assertFalse(rawDbValue.contains(plaintextName), "Raw database column must NEVER contain plaintext name");

        // 2. JPA Repository read
        AppUser loaded = appUserRepository.findById(saved.getId()).orElseThrow();
        assertEquals(plaintextName, loaded.getFullName(), "JPA layer must transparently decrypt to original plaintext");
    }

    @Test
    @DisplayName("Verify BusinessProfile.whatsappNumber raw DB ciphertext vs JPA plaintext")
    void testBusinessProfileWhatsAppNumberEncryption() {
        UUID userId = createBaseUserOnly("profile-test-" + UUID.randomUUID());
        String plaintextPhone = "+923001234567";

        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);
        profile.setBusinessType("retail");
        profile.setLanguagePreference("en");
        profile.setWhatsappNumber(plaintextPhone);
        profile.setWhatsappOptIn(true);
        profile.setWhatsappOptedInAt(LocalDateTime.now());

        businessRepository.saveAndFlush(new Business(userId, "ACTIVE"));
        businessProfileRepository.saveAndFlush(profile);

        // 1. Raw SQL inspection
        String rawPhone = jdbcTemplate.queryForObject(
                "SELECT whatsapp_number FROM business_profiles WHERE user_id = ?",
                String.class,
                userId
        );

        assertNotNull(rawPhone);
        assertTrue(rawPhone.startsWith("enc:v1:k1:"), "Raw whatsapp_number must store encrypted envelope: " + rawPhone);
        assertFalse(rawPhone.contains(plaintextPhone), "Raw DB must not contain plaintext phone number");

        // 2. JPA read
        BusinessProfile loaded = businessProfileRepository.findById(userId).orElseThrow();
        assertEquals(plaintextPhone, loaded.getWhatsappNumber());

        // 3. Test findByWhatsappOptInTrueAndWhatsappNumberIsNotNull query
        List<BusinessProfile> optIns = businessProfileRepository.findByWhatsappOptInTrueAndWhatsappNumberIsNotNull();
        assertTrue(optIns.stream().anyMatch(p -> p.getUserId().equals(userId)));
    }

    @Test
    @DisplayName("Verify WhatsAppDelivery.destinationNumber raw DB ciphertext vs JPA plaintext")
    void testWhatsAppDeliveryDestinationNumberEncryption() {
        UUID userId = createBaseUserAndProfile("delivery-test-" + UUID.randomUUID());
        String plaintextDestination = "+923009876543";

        WhatsAppDelivery delivery = new WhatsAppDelivery();
        delivery.setUserId(userId);
        delivery.setDeliveryCycle("2026-W39");
        delivery.setTargetMonth("2026-08");
        delivery.setSourceFingerprint("fp-" + UUID.randomUUID());
        delivery.setDestinationNumber(plaintextDestination);
        delivery.setLanguage("en");
        delivery.setTemplateName("financial_health_weekly_summary_v1");
        delivery.setProviderName("mock");
        delivery.setDeliveryStatus(WhatsAppDeliveryStatus.PENDING);

        WhatsAppDelivery saved = whatsAppDeliveryRepository.saveAndFlush(delivery);
        createdDeliveryIds.add(saved.getId());

        // 1. Raw SQL inspection
        String rawDestination = jdbcTemplate.queryForObject(
                "SELECT destination_number FROM whatsapp_deliveries WHERE id = ?",
                String.class,
                saved.getId()
        );

        assertNotNull(rawDestination);
        assertTrue(rawDestination.startsWith("enc:v1:k1:"), "Raw destination_number must be encrypted");
        assertFalse(rawDestination.contains(plaintextDestination), "Raw DB must not contain plaintext destination");

        // 2. JPA read
        WhatsAppDelivery loaded = whatsAppDeliveryRepository.findById(saved.getId()).orElseThrow();
        assertEquals(plaintextDestination, loaded.getDestinationNumber());
    }

    @Test
    @DisplayName("Verify UploadedDocument.originalFilename raw DB ciphertext vs JPA plaintext")
    void testUploadedDocumentOriginalFilenameEncryption() {
        UUID userId = createBaseUserAndProfile("doc-test-" + UUID.randomUUID());
        String plaintextFilename = "audited_financial_report_2026.pdf";

        UploadedDocument doc = new UploadedDocument();
        doc.setId(UUID.randomUUID());
        doc.setUserId(userId);
        doc.setFileUrl("http://localhost:8080/files/test.pdf");
        doc.setOriginalFilename(plaintextFilename);
        doc.setContentType("application/pdf");
        doc.setFileSizeBytes(102400L);
        doc.setProcessingStatus(DocumentStatus.pending);

        UploadedDocument saved = uploadedDocumentRepository.saveAndFlush(doc);
        createdDocIds.add(saved.getId());

        // 1. Raw SQL inspection
        String rawFilename = jdbcTemplate.queryForObject(
                "SELECT original_filename FROM uploaded_documents WHERE id = ?",
                String.class,
                saved.getId()
        );

        assertNotNull(rawFilename);
        assertTrue(rawFilename.startsWith("enc:v1:k1:"), "Raw original_filename must be encrypted");
        assertFalse(rawFilename.contains(plaintextFilename), "Raw DB must not contain plaintext filename");

        // 2. JPA read
        UploadedDocument loaded = uploadedDocumentRepository.findById(saved.getId()).orElseThrow();
        assertEquals(plaintextFilename, loaded.getOriginalFilename());
    }

    @Test
    @DisplayName("Legacy plaintext is readable when allowLegacyPlaintext=true, and rejected when false")
    void testLegacyPlaintextAndStrictMode() {
        UUID userId = UUID.randomUUID();
        String legacyPlaintextName = "Tariq Legacy Plaintext";

        // Insert legacy plaintext directly via raw JDBC
        jdbcTemplate.update(
                "INSERT INTO app_users (id, email, password_hash, full_name, account_status, must_change_password, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, 'ACTIVE', false, NOW(), NOW())",
                userId, "legacy-" + userId + "@test.com", "hash", legacyPlaintextName
        );
        createdUserIds.add(userId);

        // 1. In default migration mode (allowLegacyPlaintext=true), JPA loads legacy plaintext
        cryptoProperties.setAllowLegacyPlaintext(true);
        AppUser loaded = appUserRepository.findById(userId).orElseThrow();
        assertEquals(legacyPlaintextName, loaded.getFullName());

        // 2. In strict mode (allowLegacyPlaintext=false), JPA load fails closed
        cryptoProperties.setAllowLegacyPlaintext(false);
        assertThrows(Exception.class, () -> appUserRepository.findById(userId));
    }

    @Test
    @DisplayName("Startup hook with autoRunMigration=false performs zero migration")
    void testStartupHookDoesNotMigrateWhenDisabled() {
        UUID userId = UUID.randomUUID();
        String rawPlaintext = "Unmigrated User on Startup";

        // Insert raw legacy plaintext row directly via JDBC
        jdbcTemplate.update(
                "INSERT INTO app_users (id, email, password_hash, full_name, account_status, must_change_password, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, 'ACTIVE', false, NOW(), NOW())",
                userId, "unmigrated-" + userId + "@test.com", "hash", rawPlaintext
        );
        createdUserIds.add(userId);

        // Ensure autoRunMigration is false (default)
        cryptoProperties.setAutoRunMigration(false);

        // Simulate onStartup hook
        cryptoMigrationService.onStartup();

        // Row must remain unmigrated plaintext
        String dbValue = jdbcTemplate.queryForObject(
                "SELECT full_name FROM app_users WHERE id = ?",
                String.class,
                userId
        );
        assertEquals(rawPlaintext, dbValue, "Row must not be mutated when autoRunMigration=false");
    }

    @Test
    @DisplayName("Controlled backfill migrates legacy plaintext rows, repeat run is idempotent (no double encryption)")
    void testBackfillMigrationAndIdempotency() {
        UUID userId = UUID.randomUUID();
        String rawPlaintext = "Migration Candidate User";

        // Insert raw legacy plaintext row
        jdbcTemplate.update(
                "INSERT INTO app_users (id, email, password_hash, full_name, account_status, must_change_password, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, 'ACTIVE', false, NOW(), NOW())",
                userId, "migrate-" + userId + "@test.com", "hash", rawPlaintext
        );
        createdUserIds.add(userId);

        // 1. Run migration
        CryptoMigrationReport firstRun = cryptoMigrationService.migrateAll();
        assertTrue(firstRun.getMigratedRows() >= 1, "First migration run must migrate legacy row(s)");

        // Verify row is now encrypted in DB
        String encryptedAfterFirstRun = jdbcTemplate.queryForObject(
                "SELECT full_name FROM app_users WHERE id = ?",
                String.class,
                userId
        );
        assertTrue(encryptedAfterFirstRun.startsWith("enc:v1:k1:"));

        // 2. Run migration a second time (idempotency check)
        CryptoMigrationReport secondRun = cryptoMigrationService.migrateAll();
        assertEquals(0, secondRun.getLegacyPlaintextRows(), "Second run must detect 0 legacy plaintext rows");
        assertEquals(0, secondRun.getMigratedRows(), "Second run must not migrate any already-encrypted rows");

        // Verify ciphertext was not double-encrypted
        String encryptedAfterSecondRun = jdbcTemplate.queryForObject(
                "SELECT full_name FROM app_users WHERE id = ?",
                String.class,
                userId
        );
        assertEquals(encryptedAfterFirstRun, encryptedAfterSecondRun, "Ciphertext must be unchanged by second migration run");

        // Verify decrypted value is still the original plaintext
        AppUser loaded = appUserRepository.findById(userId).orElseThrow();
        assertEquals(rawPlaintext, loaded.getFullName());
    }

    @Test
    @DisplayName("Key rotation: migrate existing ciphertext from k1 to k2")
    void testKeyRotationMigration() {
        // Register key k2 in keyring
        byte[] keyK2Bytes = "12345678901234567890123456789099".getBytes(StandardCharsets.UTF_8);
        keyringProvider.registerKey("k2", keyK2Bytes);

        String originalName = "Rotation Candidate User";
        AppUser user = new AppUser();
        user.setEmail("rotation-" + UUID.randomUUID() + "@test.com");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setFullName(originalName);
        user.setAccountStatus(AccountStatus.ACTIVE);

        AppUser saved = appUserRepository.saveAndFlush(user);
        createdUserIds.add(saved.getId());

        // Verify saved with active key k1
        String rawDbK1 = jdbcTemplate.queryForObject(
                "SELECT full_name FROM app_users WHERE id = ?",
                String.class,
                saved.getId()
        );
        assertTrue(rawDbK1.startsWith("enc:v1:k1:"));

        // Rotate ciphertext in database to k2
        CryptoMigrationReport rotationReport = cryptoMigrationService.rotateKey("k2");
        assertTrue(rotationReport.getMigratedRows() >= 1);

        // Verify raw DB now uses key k2
        String rawDbK2 = jdbcTemplate.queryForObject(
                "SELECT full_name FROM app_users WHERE id = ?",
                String.class,
                saved.getId()
        );
        assertTrue(rawDbK2.startsWith("enc:v1:k2:"), "Row must now be encrypted with key k2");

        // Verify JPA reads original plaintext using k2
        AppUser reloaded = appUserRepository.findById(saved.getId()).orElseThrow();
        assertEquals(originalName, reloaded.getFullName());

        // Restore active key k1 across database
        cryptoMigrationService.rotateKey("k1");
    }

    private UUID createBaseUserOnly(String prefix) {
        AppUser user = new AppUser();
        user.setEmail(prefix + "@test.com");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setFullName("User " + prefix);
        user.setAccountStatus(AccountStatus.ACTIVE);

        AppUser saved = appUserRepository.saveAndFlush(user);
        createdUserIds.add(saved.getId());
        return saved.getId();
    }

    private UUID createBaseUserAndProfile(String prefix) {
        UUID userId = createBaseUserOnly(prefix);
        businessRepository.saveAndFlush(new Business(userId, "ACTIVE"));
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);
        profile.setBusinessType("retail");
        profile.setLanguagePreference("en");
        profile.setWhatsappOptIn(false);
        businessProfileRepository.saveAndFlush(profile);
        return userId;
    }
}
