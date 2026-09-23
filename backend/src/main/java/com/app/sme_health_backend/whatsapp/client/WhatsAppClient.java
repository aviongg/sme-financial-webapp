package com.app.sme_health_backend.whatsapp.client;

public interface WhatsAppClient {

    WhatsAppSendResult sendSummary(WhatsAppSendRequest request);

    String getProviderName();
}
