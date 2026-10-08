package com.app.sme_health_backend.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 1800) // 30 minutes idle timeout
public class SessionConfig {

    @Bean
    public CookieSerializer cookieSerializer(
            @Value("${app.security.session.cookie-name:FINSIGHT_SESSION}") String cookieName,
            @Value("${app.security.session.cookie-secure:false}") boolean secure,
            @Value("${app.security.session.cookie-same-site:Lax}") String sameSite
    ) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(cookieName);
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite(sameSite);
        serializer.setCookiePath("/");
        // Explicitly avoid setting domain so __Host- prefix requirements are satisfied in prod
        return serializer;
    }
}
