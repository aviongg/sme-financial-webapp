package com.app.sme_health_backend.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class IdentityRateLimitingServiceTest {

    private MutableClock clock;
    private IdentityRateLimitingService rateLimiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-25T10:00:00Z"));
        // 5 login attempts / 300s (5m), 3 reset attempts / 900s (15m)
        rateLimiter = new IdentityRateLimitingService(clock, 5, 300, 3, 900);
    }

    @Test
    @DisplayName("Login attempts within limit succeed, exceeding limit throws 429 exception")
    void testLoginRateLimitExceeded() {
        String email = "User@Example.COM ";

        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> rateLimiter.checkAndRecordLoginAttempt(email),
                    "Attempt " + (i + 1) + " should be allowed");
        }

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class,
                () -> rateLimiter.checkAndRecordLoginAttempt(email));
        assertTrue(ex.getRetryAfterSeconds() > 0);
        assertEquals("Too many requests. Please try again later.", ex.getMessage());
    }

    @Test
    @DisplayName("Password reset attempts within limit succeed, exceeding limit throws 429 exception")
    void testPasswordResetRateLimitExceeded() {
        String email = "reset-target@example.com";

        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> rateLimiter.checkAndRecordPasswordReset(email),
                    "Attempt " + (i + 1) + " should be allowed");
        }

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class,
                () -> rateLimiter.checkAndRecordPasswordReset(email));
        assertTrue(ex.getRetryAfterSeconds() > 0);
        assertEquals("Too many requests. Please try again later.", ex.getMessage());
    }

    @Test
    @DisplayName("Normalized email ensures case and whitespace insensitivity")
    void testEmailNormalization() {
        rateLimiter.checkAndRecordLoginAttempt("test@example.com");
        rateLimiter.checkAndRecordLoginAttempt("  TEST@example.COM  ");
        rateLimiter.checkAndRecordLoginAttempt("Test@Example.Com");
        rateLimiter.checkAndRecordLoginAttempt("test@EXAMPLE.com");
        rateLimiter.checkAndRecordLoginAttempt("TEST@EXAMPLE.COM");

        // 6th attempt with mixed case must be blocked
        assertThrows(RateLimitExceededException.class,
                () -> rateLimiter.checkAndRecordLoginAttempt("tEsT@ExAmPlE.cOm"));
    }

    @Test
    @DisplayName("Login and reset limits are isolated from each other")
    void testActionIsolation() {
        String email = "isolated@example.com";

        // Exhaust login limit
        for (int i = 0; i < 5; i++) {
            rateLimiter.checkAndRecordLoginAttempt(email);
        }
        assertThrows(RateLimitExceededException.class, () -> rateLimiter.checkAndRecordLoginAttempt(email));

        // Password reset for same email should still be permitted
        assertDoesNotThrow(() -> rateLimiter.checkAndRecordPasswordReset(email));
    }

    @Test
    @DisplayName("Login success clears failed attempts for identity")
    void testLoginSuccessClearsLimit() {
        String email = "success@example.com";

        for (int i = 0; i < 4; i++) {
            rateLimiter.checkAndRecordLoginAttempt(email);
        }

        // Successful login
        rateLimiter.recordLoginSuccess(email);

        // Can attempt 5 more times without hitting limit
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> rateLimiter.checkAndRecordLoginAttempt(email));
        }
    }

    @Test
    @DisplayName("Rate limit resets after window expires")
    void testWindowExpiry() {
        String email = "expiry@example.com";

        for (int i = 0; i < 5; i++) {
            rateLimiter.checkAndRecordLoginAttempt(email);
        }
        assertThrows(RateLimitExceededException.class, () -> rateLimiter.checkAndRecordLoginAttempt(email));

        // Advance clock by 5 minutes and 1 second
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        // Should be permitted again
        assertDoesNotThrow(() -> rateLimiter.checkAndRecordLoginAttempt(email));
    }

    private static class MutableClock extends Clock {
        private Instant currentInstant;
        private final ZoneId zone = ZoneId.of("UTC");

        public MutableClock(Instant initialInstant) {
            this.currentInstant = initialInstant;
        }

        public void advance(Duration duration) {
            this.currentInstant = this.currentInstant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }
}
