package com.app.sme_health_backend.identity.credential.notification;

import java.time.OffsetDateTime;

public interface PasswordResetNotifier {

    /**
     * Dispatches a password reset notification to the user's email address.
     *
     * @param email user email address
     * @param rawToken unhashed raw URL-safe bearer token
     * @param expiresAt token expiration timestamp
     */
    void sendPasswordResetNotification(String email, String rawToken, OffsetDateTime expiresAt);
}
