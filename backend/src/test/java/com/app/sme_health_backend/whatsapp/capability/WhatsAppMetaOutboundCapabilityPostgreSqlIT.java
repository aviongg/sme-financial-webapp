package com.app.sme_health_backend.whatsapp.capability;

import com.app.sme_health_backend.whatsapp.client.MetaWhatsAppCloudApiClient;
import com.app.sme_health_backend.whatsapp.client.WhatsAppClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

/**
 * Real Spring + disposable PostgreSQL with app.whatsapp.provider=meta and synthetic credentials. The Cloud API base URL
 * is an unroutable loopback port, so no request could leave this machine; nothing is sent in these tests. A configured
 * Meta provider is reported as configuration only, never as proof of transmission.
 */
@SpringBootTest(properties = {"app.whatsapp.provider=meta", "app.whatsapp.scheduler.enabled=false",
        "app.whatsapp.meta.phone-number-id=123456789012345", "app.whatsapp.meta.access-token=synthetic-meta-token",
        "app.whatsapp.meta.base-url=http://127.0.0.1:9"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class WhatsAppMetaOutboundCapabilityPostgreSqlIT extends WhatsAppOutboundCapabilityContract {
    @Override
    String expectedProvider() {
        return "meta";
    }

    @Override
    Class<? extends WhatsAppClient> expectedClient() {
        return MetaWhatsAppCloudApiClient.class;
    }
}
