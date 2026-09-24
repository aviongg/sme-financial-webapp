package com.app.sme_health_backend.security;

import com.app.sme_health_backend.crypto.SensitiveDataCipher;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.mfa.dto.MfaInitiateResponse;
import com.app.sme_health_backend.mfa.entity.UserMfa;
import com.app.sme_health_backend.mfa.entity.UserMfaRecoveryCode;
import com.app.sme_health_backend.mfa.repository.UserMfaRecoveryCodeRepository;
import com.app.sme_health_backend.mfa.repository.UserMfaRepository;
import com.app.sme_health_backend.mfa.service.MfaService;
import com.app.sme_health_backend.mfa.service.TotpEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.AEADBadTagException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class MfaAndRecoverySecurityIT {

    private static final String APP_USER = "finsight_app";
    private static final String APP_PASSWORD = "FinSight_App_Runtime_2026_!*7vQ";
    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/sme_health";

    @Autowired
    private MfaService mfaService;

    @Autowired
    private TotpEngine totpEngine;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private UserMfaRepository mfaRepository;

    @Autowired
    private UserMfaRecoveryCodeRepository recoveryCodeRepository;

    @Autowired
    private SensitiveDataCipher cipher;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private AppUser createTestUser(String emailPrefix) {
        AppUser user = new AppUser();
        user.setEmail(emailPrefix + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("user-password-123"));
        user.setFullName("MFA Test User");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        user.setAuthVersion(0L);
        return userRepository.saveAndFlush(user);
    }

    private Connection createConnection() throws Exception {
        Properties props = new Properties();
        props.setProperty("user", APP_USER);
        props.setProperty("password", APP_PASSWORD);
        props.setProperty("sslmode", "prefer");
        return DriverManager.getConnection(JDBC_URL, props);
    }

    @Test
    @DisplayName("Pending TOTP secret is stored encrypted in raw PostgreSQL and not plaintext")
    void testTotpSecretEncryptedInDatabase() throws Exception {
        AppUser user = createTestUser("mfa-enc");
        MfaInitiateResponse response = mfaService.initiateEnrollment(user.getId());
        String plaintextSecret = response.secret();

        // Query raw database directly via JDBC
        try (Connection conn = createConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT totp_secret FROM user_mfa WHERE user_id = ?")) {
                ps.setObject(1, user.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "user_mfa row must exist");
                    String storedCiphertext = rs.getString(1);
                    assertNotNull(storedCiphertext);
                    assertNotEquals(plaintextSecret, storedCiphertext, "Database must store encrypted envelope, not plaintext");
                    assertTrue(storedCiphertext.startsWith("enc:v1:"), "Must use S5 AES envelope prefix");
                }
            }
        }
    }

    @Test
    @DisplayName("User-bound AAD roundtrip and cross-user ciphertext transplantation failure")
    void testUserBoundAadAndTransplantationFailure() throws Exception {
        AppUser userA = createTestUser("mfa-aad-a");
        AppUser userB = createTestUser("mfa-aad-b");

        // Initiate enrollment for user A
        mfaService.initiateEnrollment(userA.getId());
        UserMfa mfaA = mfaRepository.findByUserId(userA.getId()).orElseThrow();
        String ciphertextA = mfaA.getTotpSecret();

        // Initiate enrollment for user B
        mfaService.initiateEnrollment(userB.getId());
        UserMfa mfaB = mfaRepository.findByUserId(userB.getId()).orElseThrow();

        // Transplant user A's ciphertext into user B's row and enable
        mfaB.setStatus("ENABLED");
        mfaB.setTotpSecret(ciphertextA);
        mfaRepository.saveAndFlush(mfaB);

        // Attempting to verify user B with transplanted ciphertext must fail because user ID is bound in AAD
        String candidateCode = "123456";
        Exception ex = assertThrows(Exception.class, () ->
                mfaService.verifyTotp(userB.getId(), candidateCode));

        // Decryption fails due to AAD mismatch
        boolean isAadFailure = false;
        Throwable t = ex;
        while (t != null) {
            if (t instanceof AEADBadTagException || (t.getMessage() != null && t.getMessage().contains("AEADBadTagException"))
                    || (t.getMessage() != null && t.getMessage().contains("Tag mismatch"))
                    || (t.getMessage() != null && t.getMessage().contains("Authentication failed"))
                    || (t.getMessage() != null && t.getMessage().contains("mismatched AAD"))) {
                isAadFailure = true;
                break;
            }
            t = t.getCause();
        }
        assertTrue(isAadFailure, "Transplanted ciphertext must fail AAD validation: " + ex.getMessage());
    }

    @Test
    @DisplayName("TOTP replay prevention: same accepted timestep cannot be reused")
    void testTotpReplayPrevention() {
        AppUser user = createTestUser("mfa-replay");
        String secret = mfaService.initiateEnrollment(user.getId()).secret();
        String code = totpEngine.generateCode(secret);

        // Confirm enrollment
        List<String> recoveryCodes = mfaService.confirmEnrollment(user.getId(), "user-password-123", code);
        assertNotNull(recoveryCodes);
        assertEquals(10, recoveryCodes.size());

        // First verification with same code succeeds
        boolean firstVerify = mfaService.verifyTotp(user.getId(), code);
        // Note: confirmEnrollment already claimed the timestep, so immediate replay of same code should be rejected!
        assertFalse(firstVerify, "Same timestep used in enrollment cannot be immediately replayed");
    }

    @Test
    @DisplayName("Concurrent TOTP replay prevention across threads")
    void testConcurrentTotpReplay() throws Exception {
        AppUser user = createTestUser("mfa-concurrent");
        String secret = mfaService.initiateEnrollment(user.getId()).secret();

        // Fast-forward or use current code for confirmation
        long currentTimestep = System.currentTimeMillis() / 1000L / 30L;
        String enrollCode = totpEngine.generateCode(secret, currentTimestep - 1);
        mfaService.confirmEnrollment(user.getId(), "user-password-123", enrollCode);

        // Current code to challenge concurrently
        String testCode = totpEngine.generateCode(secret, currentTimestep);

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    if (mfaService.verifyTotp(user.getId(), testCode)) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {}
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        // Exactly 1 thread must succeed
        assertEquals(1, successCount.get(), "Exactly one concurrent TOTP verification must succeed for the same timestep");
    }

    @Test
    @DisplayName("Recovery codes: 10 generated, Argon2id hashed, single-use, concurrent consumption defense")
    void testRecoveryCodesLifecycle() throws Exception {
        AppUser user = createTestUser("mfa-recovery");
        String secret = mfaService.initiateEnrollment(user.getId()).secret();
        String code = totpEngine.generateCode(secret);

        List<String> recoveryCodes = mfaService.confirmEnrollment(user.getId(), "user-password-123", code);
        assertEquals(10, recoveryCodes.size());

        // Verify stored in DB as Argon2id hashes
        List<UserMfaRecoveryCode> storedCodes = recoveryCodeRepository.findByUserId(user.getId());
        assertEquals(10, storedCodes.size());
        for (UserMfaRecoveryCode rc : storedCodes) {
            assertTrue(rc.getCodeHash().startsWith("$argon2id$"), "Recovery code must be Argon2id hashed");
            assertNull(rc.getUsedAt());
        }

        // Test one-time consumption
        String testCode = recoveryCodes.get(0);
        boolean firstUse = mfaService.verifyRecoveryCode(user.getId(), testCode);
        assertTrue(firstUse, "First recovery code attempt must succeed");

        // Second use must fail
        boolean secondUse = mfaService.verifyRecoveryCode(user.getId(), testCode);
        assertFalse(secondUse, "Reused recovery code must be rejected");

        // Concurrent recovery attempt with another code
        String concurrentCode = recoveryCodes.get(1);
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    if (mfaService.verifyRecoveryCode(user.getId(), concurrentCode)) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {}
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(1, successCount.get(), "Exactly one concurrent recovery code consumption must succeed");
    }

    @Test
    @DisplayName("MFA disable removes secrets, recovery codes, and increments auth_version")
    void testMfaDisableLifecycle() {
        AppUser user = createTestUser("mfa-disable");
        String secret = mfaService.initiateEnrollment(user.getId()).secret();
        String code = totpEngine.generateCode(secret);
        mfaService.confirmEnrollment(user.getId(), "user-password-123", code);

        assertTrue(mfaService.isMfaEnabled(user.getId()));

        // Disable MFA
        mfaService.disableMfa(user.getId());

        assertFalse(mfaService.isMfaEnabled(user.getId()));
        assertTrue(mfaRepository.findByUserId(user.getId()).isEmpty(), "user_mfa row must be deleted");
        assertTrue(recoveryCodeRepository.findByUserId(user.getId()).isEmpty(), "recovery codes must be deleted");

        AppUser updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(1L, updated.getAuthVersion(), "auth_version must be incremented upon MFA disable");
    }
}
