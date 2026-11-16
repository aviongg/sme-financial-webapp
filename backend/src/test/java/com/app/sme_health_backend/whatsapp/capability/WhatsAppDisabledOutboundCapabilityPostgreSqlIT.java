package com.app.sme_health_backend.whatsapp.capability;

import com.app.sme_health_backend.whatsapp.client.DisabledWhatsAppClient;
import com.app.sme_health_backend.whatsapp.client.WhatsAppClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

/** Real Spring + disposable PostgreSQL with the production default app.whatsapp.provider=disabled and empty history. */
@SpringBootTest(properties = {"app.whatsapp.provider=disabled", "app.whatsapp.scheduler.enabled=false"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class WhatsAppDisabledOutboundCapabilityPostgreSqlIT extends WhatsAppOutboundCapabilityContract {
    @Override
    String expectedProvider() {
        return "disabled";
    }

    @Override
    Class<? extends WhatsAppClient> expectedClient() {
        return DisabledWhatsAppClient.class;
    }
}
