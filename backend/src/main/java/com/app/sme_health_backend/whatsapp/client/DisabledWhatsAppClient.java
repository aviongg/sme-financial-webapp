package com.app.sme_health_backend.whatsapp.client;

/**
 * Selected by {@code app.whatsapp.provider=disabled} (the production default). Nothing is sent and nothing is
 * reported as sent: every attempt is an explicit failure, so no fabricated provider message id can reach a delivery
 * record and block the real send for that weekly cycle.
 */
public class DisabledWhatsAppClient implements WhatsAppClient {

    public static final String PROVIDER_NAME = "disabled";
    public static final String FAILURE_REASON = "WhatsApp provider is not configured";

    @Override
    public WhatsAppSendResult sendSummary(WhatsAppSendRequest request) {
        return WhatsAppSendResult.failed(FAILURE_REASON, PROVIDER_NAME);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }
}
