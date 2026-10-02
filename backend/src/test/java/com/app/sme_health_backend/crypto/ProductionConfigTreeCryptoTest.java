package com.app.sme_health_backend.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionConfigTreeCryptoTest {
    @TempDir Path secrets;

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(CryptoOnly.class)
                .withPropertyValues("spring.profiles.active=prod",
                        "FINSIGHT_SECRETS_DIR=" + secrets.toAbsolutePath().toString().replace('\\', '/') + "/");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CryptoProperties.class)
    @Import({LocalKeyringProvider.class, AesGcmSensitiveDataCipher.class})
    static class CryptoOnly {}

    @Test
    void productionUsesDockerSecretAndRetainsHistoricalKeys() throws Exception {
        Files.writeString(secrets.resolve("crypto_key_k1"), Base64.getEncoder().encodeToString(new byte[32]));
        byte[] historicalKey = new byte[32];
        java.util.Arrays.fill(historicalKey, (byte) 1);
        Files.writeString(secrets.resolve("crypto_key_k2"), Base64.getEncoder().encodeToString(historicalKey));
        context().run(c -> {
            assertThat(c).hasNotFailed();
            var cipher = c.getBean(AesGcmSensitiveDataCipher.class);
            assertEquals("round trip", cipher.decrypt(cipher.encrypt("round trip", "test-aad"), "test-aad"));
            assertThat(c.getBean(LocalKeyringProvider.class).hasKey("k2")).isTrue();
        });
    }

    @Test
    void missingSecretCannotFallBackToMavenTestKey() {
        context().run(c -> assertThat(c).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-base64!", "c2hvcnQ="})
    void malformedSecretFailsClosed(String invalid) throws Exception {
        Files.writeString(secrets.resolve("crypto_key_k1"), invalid);
        context().run(c -> assertThat(c).hasFailed());
    }
}
