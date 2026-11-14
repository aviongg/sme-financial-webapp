package com.app.sme_health_backend.security.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression: 153e94b made {@code https://*.trycloudflare.com} a credentialed CORS default, so any attacker-created
 * quick tunnel was a trusted origin on every non-production deployment, and broad patterns were no longer filtered.
 */
class CorsOriginPolicyTest {

    @Test
    void baseDefaultTrustsOnlyLocalDevelopment() throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            props.load(in);
        }
        assertThat(props.getProperty("app.security.cors.allowed-origins")).isEqualTo("${CORS_ALLOWED_ORIGINS:http://localhost:3000}");
    }

    @Test
    void exactOriginsAndAnExplicitLeftmostWildcardAreAcceptedOutsideProduction() {
        assertThat(CorsOriginPolicy.parse("http://localhost:3000, https://app.example.com:8443", false))
                .containsExactly("http://localhost:3000", "https://app.example.com:8443");
        assertThat(CorsOriginPolicy.parse("https://*.trycloudflare.com", false)).containsExactly("https://*.trycloudflare.com");
    }

    @Test
    void broadOrMalformedEntriesAreRejected() {
        for (String bad : new String[] {"*", "null", "https://*", "https://*.com", "http://*.example.com",
                "https://a.*.example.com", "https://app.example.com/path", "https://user@app.example.com", "ftp://x.example.com"}) {
            assertThatThrownBy(() -> CorsOriginPolicy.parse(bad, false)).as(bad).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void productionRefusesWildcards() {
        assertThatThrownBy(() -> CorsOriginPolicy.parse("https://*.trycloudflare.com", true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("production");
        assertThat(CorsOriginPolicy.parse("https://finsight.example.pk", true)).containsExactly("https://finsight.example.pk");
    }
}
