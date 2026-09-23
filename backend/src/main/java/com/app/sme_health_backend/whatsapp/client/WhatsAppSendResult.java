package com.app.sme_health_backend.whatsapp.client;

import com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus;

public record WhatsAppSendResult(
        boolean success,
        WhatsAppDeliveryStatus status,
        String providerMessageId,
        String providerName,
        String failureReason
) {
    public static WhatsAppSendResult sent(String providerMessageId, String providerName) {
        return new WhatsAppSendResult(true, WhatsAppDeliveryStatus.SENT, providerMessageId, providerName, null);
    }

    public static WhatsAppSendResult failed(String failureReason, String providerName) {
        return new WhatsAppSendResult(false, WhatsAppDeliveryStatus.FAILED, null, providerName, failureReason);
    }

    public static WhatsAppSendResult indeterminate(String reason, String providerName) {
        return new WhatsAppSendResult(false, WhatsAppDeliveryStatus.INDETERMINATE, null, providerName, reason);
    }
}
