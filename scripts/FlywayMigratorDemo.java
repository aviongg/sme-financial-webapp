import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

public class FlywayMigratorDemo {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://127.0.0.1:5433/finsight_demo";
        String user = "finsight_migrator";
        String password = "DemoDbPassword2026!";
        System.out.println("Running Flyway migrations V1-V52 on: " + url);
        String migrationPath = "filesystem:C:/Users/PC/Projects/sme-financial-webapp-pr4-review/backend/src/main/resources/db/migration";
        Flyway flyway = Flyway.configure()
                .dataSource(url, user, password)
                .locations(migrationPath)
                .cleanDisabled(true)
                .load();
        MigrateResult result = flyway.migrate();
        System.out.println("Migrations applied: " + result.migrationsExecuted);
        System.out.println("Current Flyway target version: " + result.targetSchemaVersion);
        if (!"52".equals(result.targetSchemaVersion)) {
            throw new IllegalStateException("Expected target version 52, but got " + result.targetSchemaVersion);
        }
        System.out.println("Flyway migration V1-V52 completed successfully!");
    }
}

