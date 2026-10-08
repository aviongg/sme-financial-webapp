package com.app.sme_health_backend.whatsapp.client;

import java.util.List;

public record WhatsAppSendRequest(
        String destinationNumber,
        String messageBody,
        String language,
        String templateName,
        List<String> templateParameters
) {
    public WhatsAppSendRequest(
            String destinationNumber,
            String messageBody,
            String language,
            String templateName
    ) {
        this(destinationNumber, messageBody, language, templateName, List.of());
    }

    public WhatsAppSendRequest {
        if (destinationNumber == null || destinationNumber.isBlank()) {
            throw new IllegalArgumentException("Destination number is required");
        }
        if (messageBody == null || messageBody.isBlank()) {
            throw new IllegalArgumentException("Message body is required");
        }
        if (templateParameters == null) {
            templateParameters = List.of();
        }
    }
}
