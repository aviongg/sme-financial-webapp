package com.app.sme_health_backend.documents.ocr;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class OcrIntegrationConfigurationTests {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(OcrIntegrationConfiguration.class);

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
