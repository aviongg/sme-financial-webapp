package com.app.sme_health_backend.whatsapp.capability;

import com.app.sme_health_backend.whatsapp.client.MockWhatsAppClient;
import com.app.sme_health_backend.whatsapp.client.WhatsAppClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

/** Real Spring + disposable PostgreSQL with the development default app.whatsapp.provider=mock and empty history. */
@SpringBootTest(properties = {"app.whatsapp.provider=mock", "app.whatsapp.scheduler.enabled=false"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class WhatsAppMockOutboundCapabilityPostgreSqlIT extends WhatsAppOutboundCapabilityContract {
    @Override
    String expectedProvider() {
        return "mock";
    }

    @Override
    Class<? extends WhatsAppClient> expectedClient() {
        return MockWhatsAppClient.class;
    }
}
