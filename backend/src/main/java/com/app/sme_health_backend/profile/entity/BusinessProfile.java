package com.app.sme_health_backend.profile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "business_profiles")
public class BusinessProfile {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "business_type", nullable = false, length = 20)
    private String businessType;

    @Column(name = "language_preference", nullable = false, length = 5)
    private String languagePreference = "en";

    @Column(name = "whatsapp_number", length = 20)
    private String whatsappNumber;

    @Column(name = "whatsapp_opt_in", nullable = false)
    private boolean whatsappOptIn = false;

    @Column(name = "payment_behavior", length = 20)
    private String paymentBehavior;

    @Column(name = "ntn_registered")
    private Boolean ntnRegistered;

    @Column(name = "business_registered")
    private Boolean businessRegistered;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getLanguagePreference() {
        return languagePreference;
    }

    public void setLanguagePreference(String languagePreference) {
        this.languagePreference = languagePreference;
    }

    public String getWhatsappNumber() {
        return whatsappNumber;
    }

    public void setWhatsappNumber(String whatsappNumber) {
        this.whatsappNumber = whatsappNumber;
    }

    public boolean isWhatsappOptIn() {
        return whatsappOptIn;
    }

    public void setWhatsappOptIn(boolean whatsappOptIn) {
        this.whatsappOptIn = whatsappOptIn;
    }

    public String getPaymentBehavior() {
        return paymentBehavior;
    }

    public void setPaymentBehavior(String paymentBehavior) {
        this.paymentBehavior = paymentBehavior;
    }

    public Boolean getNtnRegistered() {
        return ntnRegistered;
    }

    public void setNtnRegistered(Boolean ntnRegistered) {
        this.ntnRegistered = ntnRegistered;
    }

    public Boolean getBusinessRegistered() {
        return businessRegistered;
    }

    public void setBusinessRegistered(Boolean businessRegistered) {
        this.businessRegistered = businessRegistered;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}