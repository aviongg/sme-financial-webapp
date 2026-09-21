package com.app.sme_health_backend.documents.ocr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.net.http.HttpClient;

/** Opt-in client only; no worker, database adapter, upload endpoint or polling is registered. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "OCR_INTEGRATION_ENABLED", havingValue = "true", matchIfMissing = false)
public class OcrIntegrationConfiguration {
    @Bean
    @ConditionalOnMissingBean(OcrClient.class)
    OcrClient ocrClient(Environment environment) {
        OcrClientSettings settings = OcrClientSettings.from(environment);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new HttpOcrClient(settings, new JdkOcrHttpTransport(httpClient), new OcrJsonCodec());
    }
}
