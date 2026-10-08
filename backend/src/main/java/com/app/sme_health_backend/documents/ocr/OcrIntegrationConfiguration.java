package com.app.sme_health_backend.documents.ocr;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/** Opt-in client only; no worker, database adapter, upload endpoint or polling is registered. */
@AutoConfiguration
@ConditionalOnProperty(name = "OCR_INTEGRATION_ENABLED", havingValue = "true", matchIfMissing = false)
public class OcrIntegrationConfiguration {
    @Bean
    @ConditionalOnMissingBean(OcrClient.class)
    OcrClient ocrClient(Environment environment) {
        OcrClientSettings settings = OcrClientSettings.from(environment);

        boolean isProd = false;
        if (environment != null && environment.getActiveProfiles() != null) {
            for (String p : environment.getActiveProfiles()) {
                if ("prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p)) {
                    isProd = true;
                    break;
                }
            }
        }

        // S8-20: Plaintext production OCR endpoint prohibited
        if (isProd && "http".equalsIgnoreCase(settings.extractEndpoint().getScheme())) {
            throw new IllegalStateException("Plaintext production OCR endpoint prohibited. Target must use HTTPS.");
        }

        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER);

        String sslRootCert = environment.getProperty("OCR_SSL_ROOT_CERT");
        if (sslRootCert == null || sslRootCert.isBlank()) {
            sslRootCert = System.getenv("OCR_SSL_ROOT_CERT");
        }

        if (sslRootCert != null && !sslRootCert.isBlank()) {
            clientBuilder.sslContext(createSslContext(sslRootCert));
        }

        HttpClient httpClient = clientBuilder.build();
        return new HttpOcrClient(settings, new JdkOcrHttpTransport(httpClient), new OcrJsonCodec());
    }

    private static SSLContext createSslContext(String rootCertPath) {
        try {
            Path path = Paths.get(rootCertPath);
            if (!Files.exists(path)) {
                throw new IllegalStateException("Configured OCR CA root cert file does not exist: " + rootCertPath);
            }
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate caCert;
            try (InputStream is = Files.newInputStream(path)) {
                caCert = (X509Certificate) cf.generateCertificate(is);
            }

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setCertificateEntry("ocr-ca", caCert);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
            return sslContext;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize OCR SSLContext with CA cert: " + e.getMessage(), e);
        }
    }
}
