package com.app.sme_health_backend.testsupport;

import java.nio.file.Files;
import java.nio.file.Path;

/** Test-only exporter. Always creates DisposablePostgres; accepts no external DB URL. */
public final class ReleaseCatalogExporter {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).getParent();
        Path queryFile = root.resolve("scripts/backup/manifests/catalog.sql");
        if (args.length != 1) throw new IllegalArgumentException("One task-local output path required");
        var pg = DisposablePostgres.POSTGRES;
        try (var connection = pg.getPostgresDatabase().getConnection(); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery(Files.readString(queryFile))) {
                result.next();
                Files.writeString(Path.of(args[0]), result.getString(1));
            }
        } finally {
            pg.close();
        }
    }
}
