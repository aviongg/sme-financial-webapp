package com.app.sme_health_backend.whatsapp.config;

import com.app.sme_health_backend.whatsapp.client.DisabledWhatsAppClient;
import com.app.sme_health_backend.whatsapp.client.MetaWhatsAppCloudApiClient;
import com.app.sme_health_backend.whatsapp.client.MockWhatsAppClient;
import com.app.sme_health_backend.whatsapp.client.WhatsAppClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class WhatsAppClientConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.whatsapp.provider", havingValue = "meta")
    public WhatsAppClient metaWhatsAppCloudApiClient(
            @Value("${app.whatsapp.meta.base-url:https://graph.facebook.com}") String baseUrl,
            @Value("${app.whatsapp.meta.api-version:v21.0}") String apiVersion,
            @Value("${app.whatsapp.meta.phone-number-id:}") String phoneNumberId,
            @Value("${app.whatsapp.meta.access-token:}") String accessToken,
            @Value("${app.whatsapp.meta.timeout-seconds:15}") int timeoutSeconds,
            @Value("${app.whatsapp.template-name:financial_health_weekly_summary_v1}") String templateName,
            ObjectMapper objectMapper
    ) {
        return new MetaWhatsAppCloudApiClient(
                baseUrl,
                apiVersion,
                phoneNumberId,
                accessToken,
                timeoutSeconds,
                templateName,
                objectMapper
        );
    }

    /** Development/test simulator only; {@link WhatsAppProductionGuard} refuses it under the production profile. */
    @Bean
    @ConditionalOnProperty(name = "app.whatsapp.provider", havingValue = "mock", matchIfMissing = true)
    public WhatsAppClient mockWhatsAppClient() {
        return new MockWhatsAppClient();
    }

    /** Production default: no provider configured, so nothing is sent and nothing is reported as sent. */
    @Bean
    @ConditionalOnProperty(name = "app.whatsapp.provider", havingValue = DisabledWhatsAppClient.PROVIDER_NAME)
    public WhatsAppClient disabledWhatsAppClient() {
        return new DisabledWhatsAppClient();
    }
}
