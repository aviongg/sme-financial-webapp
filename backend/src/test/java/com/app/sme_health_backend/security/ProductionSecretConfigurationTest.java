package com.app.sme_health_backend.security;

import com.app.sme_health_backend.SmeHealthBackendApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ProductionSecretConfigurationTest {

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("Runtime environment strictly lacks migrator and DBA secrets")
    void testRuntimeLacksMigratorAndDbaSecrets() {
        assertNull(environment.getProperty("migrator_db_password"),
                "Runtime container must NOT receive migrator_db_password");
        assertNull(environment.getProperty("dba_db_password"),
                "Runtime container must NOT receive dba_db_password");
        assertNull(environment.getProperty("MIGRATOR_DB_PASSWORD"),
                "Runtime container must NOT receive MIGRATOR_DB_PASSWORD");
        assertNull(environment.getProperty("DBA_DB_PASSWORD"),
                "Runtime container must NOT receive DBA_DB_PASSWORD");
    }

    @Test
    @DisplayName("Missing production runtime secret app_db_password fails closed")
    void testMissingProductionRuntimeSecretFailsClosed() {
        // Attempting to boot application with 'prod' profile without /run/secrets/app_db_password must fail
        SpringApplicationBuilder builder = new SpringApplicationBuilder(SmeHealthBackendApplication.class)
                .profiles("prod")
                .properties(
                        "server.port=-1",
                        "spring.main.web-application-type=none"
                );

        assertThrows(Exception.class, builder::run,
                "Starting production runtime without app_db_password must fail closed");
    }
}
