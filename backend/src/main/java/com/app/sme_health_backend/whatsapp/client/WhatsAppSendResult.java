package com.app.sme_health_backend.whatsapp.client;

import com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus;

public record WhatsAppSendResult(
        boolean success,
        WhatsAppDeliveryStatus status,
        String providerMessageId,
        String providerName,
        String failureReason,
        int attempts
) {
    public WhatsAppSendResult(
            boolean success,
            WhatsAppDeliveryStatus status,
            String providerMessageId,
            String providerName,
            String failureReason
    ) {
        this(success, status, providerMessageId, providerName, failureReason, 1);
    }

    public static WhatsAppSendResult sent(String providerMessageId, String providerName) {
        return new WhatsAppSendResult(true, WhatsAppDeliveryStatus.SENT, providerMessageId, providerName, null, 1);
    }

    public static WhatsAppSendResult sent(String providerMessageId, String providerName, int attempts) {
        return new WhatsAppSendResult(true, WhatsAppDeliveryStatus.SENT, providerMessageId, providerName, null, attempts);
    }

    public static WhatsAppSendResult failed(String failureReason, String providerName) {
        return new WhatsAppSendResult(false, WhatsAppDeliveryStatus.FAILED, null, providerName, failureReason, 1);
    }

    public static WhatsAppSendResult failed(String failureReason, String providerName, int attempts) {
        return new WhatsAppSendResult(false, WhatsAppDeliveryStatus.FAILED, null, providerName, failureReason, attempts);
    }

    public static WhatsAppSendResult indeterminate(String reason, String providerName) {
        return new WhatsAppSendResult(false, WhatsAppDeliveryStatus.INDETERMINATE, null, providerName, reason, 1);
    }

    public static WhatsAppSendResult indeterminate(String reason, String providerName, int attempts) {
        return new WhatsAppSendResult(false, WhatsAppDeliveryStatus.INDETERMINATE, null, providerName, reason, attempts);
    }
}
