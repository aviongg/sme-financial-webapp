package com.app.sme_health_backend.dashboard.workspace;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static com.app.sme_health_backend.dashboard.workspace.WorkspaceTypes.*;

@Repository
public class WorkspacePreferenceRepository {
    public record Stored(long version, List<Preference> overrides) { }
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public WorkspacePreferenceRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public Stored find(UUID businessId, UUID userId) {
        return jdbc.query("SELECT version, overrides_json FROM dashboard_preferences WHERE business_id = ? AND user_id = ?",
                (rs, row) -> new Stored(rs.getLong("version"), json.readValue(rs.getString("overrides_json"), new TypeReference<List<Preference>>() {})),
                businessId, userId).stream().findFirst().orElse(new Stored(0, List.of()));
    }
    /** Caller holds the business row lock, including for the first save and configuration changes. */
    public void save(UUID businessId, UUID userId, long version, List<Preference> overrides) {
        jdbc.update("""
                INSERT INTO dashboard_preferences(business_id,user_id,overrides_json,registry_version,version)
                VALUES (?,?,CAST(? AS jsonb),?,?) ON CONFLICT (business_id,user_id)
                DO UPDATE SET overrides_json = EXCLUDED.overrides_json, registry_version = EXCLUDED.registry_version,
                    version = EXCLUDED.version, updated_at = now()
                """, businessId, userId, json.writeValueAsString(overrides), WidgetRegistry.VERSION, version);
    }
}
