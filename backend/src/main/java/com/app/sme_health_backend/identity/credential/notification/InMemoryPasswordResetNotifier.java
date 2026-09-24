package com.app.sme_health_backend.identity.credential.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory password reset notifier for dev and test environments.
 * Captures reset links in-memory without logging raw tokens or token hashes.
 */
@Component
public class InMemoryPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPasswordResetNotifier.class);

    private final String baseUrl;
    private final Map<String, ResetLinkDetails> capturedLinks = new ConcurrentHashMap<>();

    public record ResetLinkDetails(String email, String rawToken, String resetUrl, OffsetDateTime expiresAt) {}

    public InMemoryPasswordResetNotifier(
            @Value("${app.security.password-reset.base-url:http://localhost:3000/reset-password}") String baseUrl
    ) {
        this.baseUrl = baseUrl;
    }

    @Override
    public void sendPasswordResetNotification(String email, String rawToken, OffsetDateTime expiresAt) {
        // Construct URL fragment link: <configured-reset-page>#token=<rawToken>
        String resetUrl = baseUrl + "#token=" + rawToken;
        capturedLinks.put(email, new ResetLinkDetails(email, rawToken, resetUrl, expiresAt));
        log.info("InMemoryPasswordResetNotifier: captured reset link for recipient domain {} (token withheld)", 
                maskEmailDomain(email));
    }

    public ResetLinkDetails getCapturedDetails(String email) {
        return capturedLinks.get(email);
    }

    public String getLastCapturedToken(String email) {
        ResetLinkDetails details = capturedLinks.get(email);
        return details != null ? details.rawToken() : null;
    }

    public String getLastDeliveredRawToken(String email) {
        return getLastCapturedToken(email);
    }

    public void clear() {
        capturedLinks.clear();
    }

    private String maskEmailDomain(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIdx = email.indexOf('@');
        return email.substring(0, Math.min(2, atIdx)) + "***@" + email.substring(atIdx + 1);
    }
}
