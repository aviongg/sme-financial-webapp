package com.app.sme_health_backend.whatsapp.config;

import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.whatsapp.client.DisabledWhatsAppClient;
import com.app.sme_health_backend.whatsapp.client.WhatsAppClient;
import com.app.sme_health_backend.whatsapp.client.WhatsAppSendResult;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus;
import com.app.sme_health_backend.whatsapp.inbound.WhatsAppReplySender;
import com.app.sme_health_backend.whatsapp.recovery.WhatsAppDeliveryRecovery;
import com.app.sme_health_backend.whatsapp.scheduler.WhatsAppSummaryScheduler;
import com.app.sme_health_backend.whatsapp.service.WhatsAppDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Regression: production must never report simulated WhatsApp traffic as delivered. Before the fix an unset
 * WHATSAPP_PROVIDER wired the in-memory mock in production, so every weekly summary was stored as SENT with a fake
 * "mock-msg-" id (blocking the real send for that cycle) and inbound Copilot replies were "SENT" to nobody.
 */
class WhatsAppProductionFailClosedTest {

    private static MockEnvironment prod(String provider) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        if (provider != null) env.setProperty("app.whatsapp.provider", provider);
        return env;
    }

    @Test
    void productionRefusesTheMockProviderExplicitlyOrByDefault() {
        assertThatThrownBy(() -> WhatsAppProductionGuard.verify(prod("mock")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not allowed in production");
        // Unset provider resolves to mock in the base profile: still refused under prod.
        assertThatThrownBy(() -> WhatsAppProductionGuard.verify(prod(null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionAcceptsDisabledAndFullyConfiguredMetaOnly() {
        assertThatCode(() -> WhatsAppProductionGuard.verify(prod("disabled"))).doesNotThrowAnyException();

        assertThatThrownBy(() -> WhatsAppProductionGuard.verify(prod("meta")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("WHATSAPP_META_ACCESS_TOKEN");

        MockEnvironment meta = prod("meta");
        meta.setProperty("app.whatsapp.meta.phone-number-id", "123456");
        meta.setProperty("app.whatsapp.meta.access-token", "token");
        assertThatCode(() -> WhatsAppProductionGuard.verify(meta)).doesNotThrowAnyException();
    }

    @Test
    void developmentStillAllowsTheMockSimulator() {
        MockEnvironment dev = new MockEnvironment();
        dev.setProperty("app.whatsapp.provider", "mock");
        assertThatCode(() -> WhatsAppProductionGuard.verify(dev)).doesNotThrowAnyException();
    }

    @Test
    void productionProfileDefaultsToTheDisabledProvider() throws Exception {
        Properties prodProps = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application-prod.properties")) {
            assertThat(in).isNotNull();
            prodProps.load(in);
        }
        assertThat(prodProps.getProperty("app.whatsapp.provider")).isEqualTo("${WHATSAPP_PROVIDER:disabled}");
    }

    @Test
    void disabledProviderWiresAClientThatNeverReportsSent() {
        new ApplicationContextRunner()
                .withUserConfiguration(WhatsAppClientConfiguration.class)
                .withPropertyValues("app.whatsapp.provider=disabled")
                .run(context -> {
                    assertThat(context).hasSingleBean(WhatsAppClient.class);
                    WhatsAppClient client = context.getBean(WhatsAppClient.class);
                    assertThat(client).isInstanceOf(DisabledWhatsAppClient.class);
                    WhatsAppSendResult result = client.sendSummary(null);
                    assertThat(result.status()).isEqualTo(WhatsAppDeliveryStatus.FAILED);
                    assertThat(result.success()).isFalse();
                    assertThat(result.providerMessageId()).isNull();
                });
    }

    @Test
    void schedulerSkipsWithoutRecordingDeliveriesWhenNoProviderIsConfigured() {
        WhatsAppDeliveryService delivery = mock(WhatsAppDeliveryService.class);
        BusinessProfileRepository profiles = mock(BusinessProfileRepository.class);
        WhatsAppDeliveryRecovery recovery = mock(WhatsAppDeliveryRecovery.class);
        WhatsAppSummaryScheduler scheduler = new WhatsAppSummaryScheduler(delivery, profiles, recovery, true, "disabled");

        scheduler.runScheduledWeeklyDelivery();

        assertThat(scheduler.isProviderConfigured()).isFalse();
        verifyNoInteractions(delivery, profiles, recovery);
    }

    @Test
    void inboundRepliesAreNotFakedWithoutAProvider() {
        WhatsAppReplySender disabled = new WhatsAppReplySender("disabled", "https://graph.facebook.com", "v21.0", "", "", 5);
        WhatsAppReplySender.Sent sent = disabled.send("+923001234567", "hello");
        assertThat(sent.status()).isEqualTo("FAILED");
        assertThat(sent.providerMessageId()).isNull();

        WhatsAppReplySender mockSender = new WhatsAppReplySender("mock", "https://graph.facebook.com", "v21.0", "", "", 5);
        assertThat(mockSender.send("+923001234567", "hello").status()).isEqualTo("SENT");
    }
}
