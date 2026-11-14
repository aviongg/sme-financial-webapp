package com.app.sme_health_backend.operations.health;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** Private probes: no financial data or paid-provider calls. */
@RestController
@Profile("!migrate")
@RequestMapping("/internal/health")
public class RuntimeHealthController {
    private final ApplicationAvailability availability;
    private final JdbcTemplate jdbc;

    public RuntimeHealthController(ApplicationAvailability availability, JdbcTemplate jdbc) {
        this.availability = availability;
        this.jdbc = jdbc;
    }

    @GetMapping("/live")
    public Map<String, String> live() { return Map.of("status", "UP"); }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        if (availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC) return unavailable();
        try {
            Integer result = jdbc.query(connection -> {
                var statement = connection.prepareStatement("SELECT 1");
                statement.setQueryTimeout(2);
                return statement;
            }, rows -> rows.next() ? rows.getInt(1) : null);
            return Integer.valueOf(1).equals(result)
                    ? ResponseEntity.ok(Map.of("status", "UP")) : unavailable();
        } catch (Exception ignored) {
            return unavailable(); // Do not expose connection details or credentials.
        }
    }

    private ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
    }
}
