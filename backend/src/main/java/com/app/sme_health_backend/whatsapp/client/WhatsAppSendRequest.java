package com.app.sme_health_backend.whatsapp.client;

public record WhatsAppSendRequest(
        String destinationNumber,
        String messageBody,
        String language,
        String templateName
) {
    public WhatsAppSendRequest {
        if (destinationNumber == null || destinationNumber.isBlank()) {
            throw new IllegalArgumentException("Destination number is required");
        }
        if (messageBody == null || messageBody.isBlank()) {
            throw new IllegalArgumentException("Message body is required");
        }
    }
}
