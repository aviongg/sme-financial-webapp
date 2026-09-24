package com.app.sme_health_backend.crypto;

import com.app.sme_health_backend.crypto.migration.CryptoMigrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CryptoPropertiesAndStartupTest {

    private static final String KEY_K1 = Base64.getEncoder().encodeToString(
            "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8)
    );

    @Test
    @DisplayName("Verify default configuration values: strict mode and auto-run disabled")
    void testDefaultValues() {
        CryptoProperties props = new CryptoProperties();

        // 1. Legacy plaintext must default to false (strict mode)
        assertFalse(props.isAllowLegacyPlaintext(),
                "Default allowLegacyPlaintext must be false (strict mode) for secure deployments");

        // 2. Auto-run migration must default to false
        assertFalse(props.isAutoRunMigration(),
                "Default autoRunMigration must be false to prevent unprompted startup mutations");

        // 3. Active key ID defaults to k1
        assertEquals("k1", props.getActiveKeyId());
    }

    @Test
    @DisplayName("Verify legacy plaintext variable permutations: absent/false -> strict, true -> migration mode")
    void testLegacyPlaintextPermutations() {
        CryptoProperties props = new CryptoProperties();
        props.setKeys(Map.of("k1", KEY_K1));
        LocalKeyringProvider keyring = new LocalKeyringProvider(props);
        keyring.initialize();
        AesGcmSensitiveDataCipher cipher = new AesGcmSensitiveDataCipher(keyring, props);

        String legacyValue = "Unencrypted Legacy Name";
        String aad = "FinSight|AppUser|fullName";

        // Permutation A: variable absent (default instantiated state: false) -> strict mode
        assertFalse(props.isAllowLegacyPlaintext());
        DecryptionException exAbsent = assertThrows(DecryptionException.class, () -> cipher.decrypt(legacyValue, aad));
        assertTrue(exAbsent.getMessage().contains("strict mode"));

        // Permutation B: variable explicitly set to false -> strict mode
        props.setAllowLegacyPlaintext(false);
        assertFalse(props.isAllowLegacyPlaintext());
        DecryptionException exFalse = assertThrows(DecryptionException.class, () -> cipher.decrypt(legacyValue, aad));
        assertTrue(exFalse.getMessage().contains("strict mode"));

        // Permutation C: variable explicitly set to true -> migration compatibility mode
        props.setAllowLegacyPlaintext(true);
        assertTrue(props.isAllowLegacyPlaintext());
        assertEquals(legacyValue, cipher.decrypt(legacyValue, aad));
    }

    @Test
    @DisplayName("Verify onStartup hook with autoRunMigration=false performs ZERO database operations")
    void testStartupHookDoesNotMigrateWhenDisabled() {
        JdbcTemplate mockJdbc = mock(JdbcTemplate.class);
        SensitiveDataCipher mockCipher = mock(SensitiveDataCipher.class);
        PlatformTransactionManager mockTxManager = mock(PlatformTransactionManager.class);

        CryptoProperties props = new CryptoProperties();
        assertFalse(props.isAutoRunMigration(), "autoRunMigration must be false by default");

        CryptoMigrationService service = new CryptoMigrationService(mockJdbc, mockCipher, props, mockTxManager);

        // Execute startup hook
        service.onStartup();

        // Must perform zero interactions with database and cipher
        verifyNoInteractions(mockJdbc);
        verifyNoInteractions(mockCipher);
        verifyNoInteractions(mockTxManager);
    }
}
