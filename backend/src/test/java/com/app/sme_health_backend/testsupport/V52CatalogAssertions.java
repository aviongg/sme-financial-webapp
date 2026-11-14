package com.app.sme_health_backend.testsupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only acceptance inventory; never regenerates or blesses the reference. */
public final class V52CatalogAssertions {
    public static void verify(Connection connection) throws Exception {
        Path inventory = Path.of("../scripts/backup/manifests");
        var json = JsonMapper.builder().build();
        var expected = json.readTree(Files.readString(inventory.resolve("v52.json"))).get("catalog");
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery(Files.readString(inventory.resolve("catalog.sql")))) {
            assertTrue(rows.next());
            var actual = json.readTree(rows.getString(1));
            for (String section : new String[]{"schema_owner", "schema_grants", "extensions", "tables", "sequences", "functions"}) {
                assertEquals(expected.get(section), actual.get(section), "Frozen V52 catalog section: " + section);
            }
        }
    }
    private V52CatalogAssertions() {}
}
