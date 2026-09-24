package com.app.sme_health_backend.security;

import com.app.sme_health_backend.SmeHealthBackendApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ProductionIngressSecurityIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Hostile CORS origin is rejected with 403 Forbidden")
    void testHostileCorsOriginRejected() throws Exception {
        mockMvc.perform(options("/api/auth/csrf")
                        .header("Origin", "https://hostile-site.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("CORS Origin 'null' is not trusted and rejected with 403 Forbidden")
    void testNullCorsOriginRejected() throws Exception {
        mockMvc.perform(options("/api/auth/csrf")
                        .header("Origin", "null")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("CORS credentials are never granted with wildcard origin")
    void testWildcardOriginAbsentWithCredentials() throws Exception {
        mockMvc.perform(options("/api/auth/csrf")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("TRACE request method is rejected")
    void testTraceMethodRejected() throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf("TRACE"), "/api/auth/csrf"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 400 || status == 405, "TRACE request must be rejected with 400 or 405, got " + status);
                });
    }

    @Test
    @DisplayName("Production profile enforces plaintext OCR endpoint rejection")
    void testPlaintextOcrEndpointFailsInProduction() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(SmeHealthBackendApplication.class)
                .profiles("prod")
                .properties(
                        "server.port=-1",
                        "spring.main.web-application-type=none",
                        "app.ocr.url=http://insecure-ai-service:8000/extract"
                );

        assertThrows(Exception.class, builder::run,
                "Production profile must fail closed if OCR URL uses plaintext HTTP");
    }
}
