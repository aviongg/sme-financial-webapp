package com.app.sme_health_backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class Argon2PasswordEncoderTest {

    @Test
    @DisplayName("Production Argon2PasswordEncoder generates valid Argon2id hash and verifies correctly")
    void testProductionArgon2idEncoder() {
        // Target production parameters: salt 16 bytes, hash 32 bytes, parallelism 1, memory 64 MiB (65536 KiB), iterations 3
        PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 65536, 3);

        String rawPassword = "correct-horse-battery-staple-2026!";
        String encoded = encoder.encode(rawPassword);

        assertNotNull(encoded);
        // Verify prefix is argon2id, version 19, memory 65536, iterations 3, parallelism 1
        assertTrue(encoded.startsWith("$argon2id$v=19$m=65536,t=3,p=1$"),
                "Encoded hash must be Argon2id with exact production parameters");
        assertNotEquals(rawPassword, encoded, "Password must never be stored as plaintext");

        // Verify matches
        assertTrue(encoder.matches(rawPassword, encoded), "Correct password must match");
        assertFalse(encoder.matches("wrong-password-attempt!", encoded), "Wrong password must fail");
    }

    @Test
    @DisplayName("Argon2PasswordEncoder produces different hashes for identical passwords due to random salt")
    void testSaltRandomness() {
        PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
        String password = "secure-financial-passphrase-123!";

        String hash1 = encoder.encode(password);
        String hash2 = encoder.encode(password);

        assertNotEquals(hash1, hash2, "Identical passwords must produce distinct salted hashes");
        assertTrue(encoder.matches(password, hash1));
        assertTrue(encoder.matches(password, hash2));
    }
}
