package com.app.sme_health_backend.mfa.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_mfa")
public class UserMfa {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "totp_secret", nullable = false)
    private String totpSecret;

    @Column(nullable = false, length = 20)
    private String status; // PENDING, ENABLED

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "enabled_at")
    private OffsetDateTime enabledAt;

    @Column(name = "last_used_time_step")
    private Long lastUsedTimeStep;

    public UserMfa() {
    }

    public UserMfa(UUID userId, String totpSecret, String status) {
        this.userId = userId;
        this.totpSecret = totpSecret;
        this.status = status;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getTotpSecret() {
        return totpSecret;
    }

    public void setTotpSecret(String totpSecret) {
        this.totpSecret = totpSecret;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(OffsetDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public OffsetDateTime getEnabledAt() {
        return enabledAt;
    }

    public void setEnabledAt(OffsetDateTime enabledAt) {
        this.enabledAt = enabledAt;
    }

    public Long getLastUsedTimeStep() {
        return lastUsedTimeStep;
    }

    public void setLastUsedTimeStep(Long lastUsedTimeStep) {
        this.lastUsedTimeStep = lastUsedTimeStep;
    }
}
