package com.app.sme_health_backend.whatsapp.entity;

public enum WhatsAppDeliveryStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
    INDETERMINATE;

    public String toDbValue() {
        return name().toLowerCase();
    }

    public static WhatsAppDeliveryStatus fromDbValue(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
