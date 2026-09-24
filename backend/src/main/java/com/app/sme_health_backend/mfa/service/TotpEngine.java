package com.app.sme_health_backend.mfa.service;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.OptionalLong;

/**
 * Interoperable RFC 6238 TOTP engine.
 * Profile: HMAC-SHA1, 6 digits, 30-second time-step, ±1 time-step skew tolerance, 160-bit secret.
 */
@Component
public class TotpEngine {

    public static final int TIME_STEP_SECONDS = 30;
    public static final int CODE_DIGITS = 6;
    public static final int SECRET_BYTES = 20; // 160 bits
    private static final int DIGIT_MODULO = 1_000_000;
    private static final String HMAC_ALGORITHM = "HmacSHA1";

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a 160-bit cryptographically secure random secret, encoded in Base32.
     */
    public String generateSecret() {
        byte[] secretBytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secretBytes);
        return Base32.encode(secretBytes);
    }

    /**
     * Constructs a standard otpauth:// provisioning URI for authenticator applications.
     */
    public String buildProvisioningUri(String email, String base32Secret) {
        String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedIssuer = URLEncoder.encode("FinSight", StandardCharsets.UTF_8);
        return String.format(
                "otpauth://totp/FinSight:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                encodedEmail, base32Secret, encodedIssuer, CODE_DIGITS, TIME_STEP_SECONDS
        );
    }

    /**
     * Validates a candidate 6-digit code against the Base32 secret within the allowable ±1 skew window.
     *
     * @param base32Secret Base32 encoded secret
     * @param candidateCode 6-digit code submitted by user
     * @param currentEpochSeconds current epoch time in seconds
     * @return OptionalLong containing the matched accepted time-step, or empty if validation fails
     */
    public OptionalLong validateCode(String base32Secret, String candidateCode, long currentEpochSeconds) {
        if (base32Secret == null || candidateCode == null || candidateCode.length() != CODE_DIGITS) {
            return OptionalLong.empty();
        }

        byte[] keyBytes;
        try {
            keyBytes = Base32.decode(base32Secret);
        } catch (IllegalArgumentException e) {
            return OptionalLong.empty();
        }

        long currentStep = currentEpochSeconds / TIME_STEP_SECONDS;

        // Check time steps in window [currentStep - 1, currentStep + 1] (90 seconds total)
        for (long step = currentStep - 1; step <= currentStep + 1; step++) {
            String expectedCode = generateCodeForStep(keyBytes, step);
            if (expectedCode.equals(candidateCode)) {
                return OptionalLong.of(step);
            }
        }

        return OptionalLong.empty();
    }

    /**
     * Computes the 6-digit TOTP string for a specific discrete time-step index.
     */
    public String generateCodeForStep(byte[] keyBytes, long step) {
        byte[] counterBytes = ByteBuffer.allocate(8).putLong(step).array();
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(keyBytes, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(counterBytes);

            // Dynamic truncation (RFC 4226 Section 5.4)
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % DIGIT_MODULO;
            return String.format("%06d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA1 for TOTP", e);
        }
    }

    public String generateCode(String base32Secret) {
        return generateCode(base32Secret, java.time.Instant.now().getEpochSecond() / TIME_STEP_SECONDS);
    }

    public String generateCode(String base32Secret, long step) {
        return generateCodeForStep(Base32.decode(base32Secret), step);
    }

    /**
     * Minimal self-contained RFC 4648 Base32 encoder/decoder.
     */
    public static class Base32 {
        private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

        public static String encode(byte[] data) {
            StringBuilder sb = new StringBuilder();
            int buffer = 0;
            int bitsLeft = 0;

            for (byte b : data) {
                buffer = (buffer << 8) | (b & 0xFF);
                bitsLeft += 8;
                while (bitsLeft >= 5) {
                    bitsLeft -= 5;
                    sb.append(ALPHABET.charAt((buffer >> bitsLeft) & 0x1F));
                }
            }

            if (bitsLeft > 0) {
                sb.append(ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
            }

            return sb.toString();
        }

        public static byte[] decode(String encoded) {
            String clean = encoded.trim().toUpperCase().replaceAll("[^A-Z2-7]", "");
            ByteBuffer buffer = ByteBuffer.allocate((clean.length() * 5) / 8);
            int currentByte = 0;
            int bitsLeft = 0;

            for (char c : clean.toCharArray()) {
                int val = ALPHABET.indexOf(c);
                if (val < 0) {
                    throw new IllegalArgumentException("Invalid Base32 character: " + c);
                }
                currentByte = (currentByte << 5) | val;
                bitsLeft += 5;
                if (bitsLeft >= 8) {
                    bitsLeft -= 8;
                    buffer.put((byte) ((currentByte >> bitsLeft) & 0xFF));
                }
            }

            return buffer.array();
        }
    }
}
