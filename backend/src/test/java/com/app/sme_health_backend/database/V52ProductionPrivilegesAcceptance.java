package com.app.sme_health_backend.database;

import com.app.sme_health_backend.testsupport.ProductionPostgresTarget;
import com.app.sme_health_backend.testsupport.V52CatalogAssertions;
import java.sql.DriverManager;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in, read-only real PostgreSQL/TLS/role acceptance. No Spring or migrations run. */
class V52ProductionPrivilegesAcceptance extends ProductionPostgresTarget {
    @Test void integratedOwnersGrantsAndRestrictedRoleLogins() throws Exception {
        for (String role : new String[]{"APP", "MIGRATOR", "BACKUP"}) {
            String name = "finsight_" + role.toLowerCase(java.util.Locale.ROOT);
            var properties = new Properties();
            properties.setProperty("user", name);
            properties.setProperty("password", setting("FINSIGHT_TEST_" + role + "_PASSWORD"));
            properties.setProperty("sslmode", "verify-full");
            properties.setProperty("sslrootcert", setting("FINSIGHT_TEST_DB_CA"));
            // Reject URL credentials/TLS overrides: JDBC URL parameters override Properties.
            String url = setting("FINSIGHT_TEST_DB_URL");
            assertFalse(url.contains("?"), "Use a credential/parameter-free acceptance JDBC URL");
            try (var connection = DriverManager.getConnection(url, properties);
                 var statement = connection.createStatement()) {
                try (var result = statement.executeQuery("SELECT current_user, rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls, rolinherit FROM pg_roles WHERE rolname=current_user")) {
                    assertTrue(result.next());
                    assertEquals(name, result.getString(1));
                    for (int i = 2; i <= 7; i++) assertFalse(result.getBoolean(i), name + " forbidden role flag " + i);
                }
                try (var result = statement.executeQuery("SELECT count(*) FROM pg_auth_members WHERE member=(SELECT oid FROM pg_roles WHERE rolname=current_user)")) {
                    assertTrue(result.next());
                    assertEquals(0, result.getInt(1), "No indirect elevated role memberships");
                }
                V52CatalogAssertions.verify(connection);
            }
        }
    }
}
