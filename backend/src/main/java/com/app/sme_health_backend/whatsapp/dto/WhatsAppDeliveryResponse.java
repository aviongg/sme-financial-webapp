package com.app.sme_health_backend.whatsapp.dto;

import com.app.sme_health_backend.whatsapp.entity.WhatsAppDelivery;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus;
import com.app.sme_health_backend.whatsapp.validation.PhoneNumberValidator;

import java.time.LocalDateTime;
import java.util.UUID;

public record WhatsAppDeliveryResponse(
        UUID id,
        UUID userId,
        UUID scoreResultId,
        String targetMonth,
        String sourceFingerprint,
        String deliveryCycle,
        String idempotencyKey,
        String maskedDestinationNumber,
        String language,
        String templateName,
        String providerName,
        String providerMessageId,
        WhatsAppDeliveryStatus deliveryStatus,
        int attemptCount,
        LocalDateTime scheduledAt,
        LocalDateTime sentAt,
        LocalDateTime failedAt,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static WhatsAppDeliveryResponse fromEntity(WhatsAppDelivery entity) {
        if (entity == null) {
            return null;
        }
        return new WhatsAppDeliveryResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getScoreResultId(),
                entity.getTargetMonth(),
                entity.getSourceFingerprint(),
                entity.getDeliveryCycle(),
                entity.getIdempotencyKey(),
                PhoneNumberValidator.mask(entity.getDestinationNumber()),
                entity.getLanguage(),
                entity.getTemplateName(),
                entity.getProviderName(),
                entity.getProviderMessageId(),
                entity.getDeliveryStatus(),
                entity.getAttemptCount(),
                entity.getScheduledAt(),
                entity.getSentAt(),
                entity.getFailedAt(),
                entity.getFailureReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
