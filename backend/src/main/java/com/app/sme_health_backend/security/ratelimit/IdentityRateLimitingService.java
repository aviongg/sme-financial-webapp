package com.app.sme_health_backend.security.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Layer B: Account/Identity-based in-memory rate limiter for single-node Spring runtime.
 * Protects login and password-reset endpoints against targeted distributed credential stuffing.
 * Keys are SHA-256 derived hashes to avoid storing raw recipient identifiers.
 * Employs a bounded cache with TTL eviction.
 *
 * Note: Horizontal multi-instance deployment would require a shared rate-limit store (e.g., Redis).
 */
@Service
public class IdentityRateLimitingService {

    private static final Logger log = LoggerFactory.getLogger(IdentityRateLimitingService.class);
    private static final int MAX_CACHE_SIZE = 10_000;

    private final Clock clock;
    private final int loginMaxAttempts;
    private final long loginWindowSeconds;
    private final int resetMaxAttempts;
    private final long resetWindowSeconds;

    private final Object lock = new Object();
    private final Map<String, AttemptBucket> cache = new LinkedHashMap<>(128, 0.75f, true);

    public static class AttemptBucket {
        private int count;
        private Instant windowStart;

        public AttemptBucket(Instant now) {
            this.count = 1;
            this.windowStart = now;
        }

        public void increment() {
            this.count++;
        }

        public int getCount() {
            return count;
        }

        public Instant getWindowStart() {
            return windowStart;
        }

        public void reset(Instant now) {
            this.count = 1;
            this.windowStart = now;
        }
    }

    public IdentityRateLimitingService() {
        this(Clock.systemUTC(), 5, 300, 3, 900);
    }

    @Autowired
    public IdentityRateLimitingService(
            Clock clock,
            @Value("${app.security.rate-limit.login.max-attempts:5}") int loginMaxAttempts,
            @Value("${app.security.rate-limit.login.window-seconds:300}") long loginWindowSeconds,
            @Value("${app.security.rate-limit.reset.max-attempts:3}") int resetMaxAttempts,
            @Value("${app.security.rate-limit.reset.window-seconds:900}") long resetWindowSeconds
    ) {
        this.clock = clock;
        this.loginMaxAttempts = loginMaxAttempts;
        this.loginWindowSeconds = loginWindowSeconds;
        this.resetMaxAttempts = resetMaxAttempts;
        this.resetWindowSeconds = resetWindowSeconds;
    }

    public void checkAndRecordLoginAttempt(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String key = hashKey("login:" + email.trim().toLowerCase());
        checkAndRecord(key, loginMaxAttempts, loginWindowSeconds);
    }

    public void recordLoginSuccess(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String key = hashKey("login:" + email.trim().toLowerCase());
        synchronized (lock) {
            cache.remove(key);
        }
    }

    public void checkAndRecordPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String key = hashKey("reset:" + email.trim().toLowerCase());
        checkAndRecord(key, resetMaxAttempts, resetWindowSeconds);
    }

    private void checkAndRecord(String key, int maxAttempts, long windowSeconds) {
        Instant now = clock.instant();
        synchronized (lock) {
            pruneExpiredOrOverflow(now, windowSeconds);

            AttemptBucket bucket = cache.get(key);
            if (bucket == null) {
                cache.put(key, new AttemptBucket(now));
                return;
            }

            long elapsedSeconds = now.getEpochSecond() - bucket.getWindowStart().getEpochSecond();
            if (elapsedSeconds >= windowSeconds) {
                bucket.reset(now);
                return;
            }

            if (bucket.getCount() >= maxAttempts) {
                int retryAfter = (int) Math.max(1, windowSeconds - elapsedSeconds);
                log.warn("Identity rate limit exceeded for key prefix {}... (retry after {}s)", 
                        key.substring(0, Math.min(8, key.length())), retryAfter);
                throw new RateLimitExceededException(retryAfter);
            }

            bucket.increment();
        }
    }

    private void pruneExpiredOrOverflow(Instant now, long defaultWindowSeconds) {
        if (cache.size() >= MAX_CACHE_SIZE) {
            Iterator<Map.Entry<String, AttemptBucket>> it = cache.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, AttemptBucket> entry = it.next();
                long elapsed = now.getEpochSecond() - entry.getValue().getWindowStart().getEpochSecond();
                if (elapsed >= defaultWindowSeconds) {
                    it.remove();
                }
            }
            // If still full, evict oldest entry
            while (cache.size() >= MAX_CACHE_SIZE) {
                Iterator<String> keyIt = cache.keySet().iterator();
                if (keyIt.hasNext()) {
                    keyIt.next();
                    keyIt.remove();
                } else {
                    break;
                }
            }
        }
    }

    private String hashKey(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
