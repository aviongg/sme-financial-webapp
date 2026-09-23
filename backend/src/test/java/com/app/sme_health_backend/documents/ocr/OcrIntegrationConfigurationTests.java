package com.app.sme_health_backend.documents.ocr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class OcrIntegrationConfigurationTests {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OcrIntegrationConfiguration.class));

    @Test
    void disabledByDefaultAndWhenExplicitlyDisabled() {
        context.run(result -> assertThat(result).doesNotHaveBean(OcrClient.class));
        context.withPropertyValues("OCR_INTEGRATION_ENABLED=false")
                .run(result -> assertThat(result).doesNotHaveBean(OcrClient.class));
    }

    @Test
    void enablingCreatesClientWithoutMakingNetworkCalls() {
        context.withPropertyValues("OCR_INTEGRATION_ENABLED=true")
                .run(result -> assertThat(result).hasSingleBean(OcrClient.class));
    }

    @Test
    void injectedReplacementWinsWithoutConstructingDefaultClient() {
        OcrClient replacement = mock(OcrClient.class);
        context.withPropertyValues("OCR_INTEGRATION_ENABLED=true", "OCR_SERVICE_URL=invalid")
                .withBean(OcrClient.class, () -> replacement)
                .run(result -> assertSame(replacement, result.getBean(OcrClient.class)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ordinaryReplacementWinsRegardlessOfConfigurationOrder(boolean replacementFirst) {
        Class<?>[] configurations = replacementFirst
                ? new Class<?>[] {ReplacementConfiguration.class, OcrImportConfiguration.class}
                : new Class<?>[] {OcrImportConfiguration.class, ReplacementConfiguration.class};
        new ApplicationContextRunner()
                .withUserConfiguration(configurations)
                .withInitializer(application -> application.getEnvironment().setActiveProfiles("ocr-client-replacement-test"))
                .withPropertyValues("OCR_INTEGRATION_ENABLED=true")
                .run(result -> {
                    assertThat(result).hasSingleBean(OcrClient.class);
                    assertSame(result.getBean("replacementOcrClient"), result.getBean(OcrClient.class));
                });
    }

    @Test
    void autoConfigurationIsRegisteredForBootDiscovery() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates())
                .contains(OcrIntegrationConfiguration.class.getName());
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(OcrIntegrationConfiguration.class)
    @Profile("ocr-client-replacement-test")
    static class OcrImportConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("ocr-client-replacement-test")
    static class ReplacementConfiguration {
        @Bean
        OcrClient replacementOcrClient() {
            return mock(OcrClient.class);
        }
    }

    @Test
    void readsIndependentTimeoutAndServiceConfiguration() {
        OcrClientSettings settings = OcrClientSettings.from(new MockEnvironment()
                .withProperty("OCR_SERVICE_URL", "http://ocr.internal:8010/v1/")
                .withProperty("OCR_CONNECT_TIMEOUT_SECONDS", "3")
                .withProperty("OCR_REQUEST_TIMEOUT_SECONDS", "120")
                .withProperty("OCR_MAX_RESPONSE_BYTES", "4096"));
        assertEquals(URI.create("http://ocr.internal:8010/v1/extract"), settings.extractEndpoint());
        assertEquals(Duration.ofSeconds(3), settings.connectTimeout());
        assertEquals(Duration.ofSeconds(120), settings.requestTimeout());
        assertEquals(4096, settings.maxResponseBytes());
    }

    @Test
    void rejectsInvalidSettingsWhenEnabled() {
        assertThrows(IllegalArgumentException.class, () -> OcrClientSettings.from(new MockEnvironment()
                .withProperty("OCR_SERVICE_URL", "https://secret:password@example.com")));
        assertThrows(IllegalArgumentException.class, () -> OcrClientSettings.from(new MockEnvironment()
                .withProperty("OCR_SERVICE_URL", "https://example.com?secret=abc")));
        assertThrows(IllegalArgumentException.class, () -> OcrClientSettings.from(new MockEnvironment()
                .withProperty("OCR_REQUEST_TIMEOUT_SECONDS", "0")));
        assertThrows(IllegalArgumentException.class, () -> OcrClientSettings.from(new MockEnvironment()
                .withProperty("OCR_MAX_RESPONSE_BYTES", "0")));
    }
}
