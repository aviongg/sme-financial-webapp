package com.app.sme_health_backend;

import com.app.sme_health_backend.database.migration.FlywayMigrationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

@SpringBootApplication
public class SmeHealthBackendApplication {

	public static void main(String[] args) {
		if (isMigrationMode(args)) {
			int exitCode = FlywayMigrationRunner.runMigration(args);
			System.exit(exitCode);
			return;
		}

		SpringApplication.run(SmeHealthBackendApplication.class, args);
	}

	public static boolean isMigrationMode(String[] args) {
		String profiles = System.getProperty("spring.profiles.active", "");
		String envProfiles = System.getenv("SPRING_PROFILES_ACTIVE");
		if (envProfiles != null) {
			profiles = profiles + "," + envProfiles;
		}
		if (args != null) {
			for (String arg : args) {
				if (arg.contains("spring.profiles.active")) {
					profiles = profiles + "," + arg;
				}
			}
		}
		return Arrays.stream(profiles.split("[,=]"))
				.map(String::trim)
				.anyMatch(p -> p.equalsIgnoreCase("migrate"));
	}
}
