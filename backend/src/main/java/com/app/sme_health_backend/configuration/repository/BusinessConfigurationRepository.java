package com.app.sme_health_backend.configuration.repository;

import com.app.sme_health_backend.configuration.model.BusinessConfiguration;
import com.app.sme_health_backend.configuration.model.OperatingProfile;
import com.app.sme_health_backend.configuration.exception.ConfigurationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class BusinessConfigurationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public BusinessConfigurationRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public void lockBusiness(UUID businessId) {
        if (jdbc.queryForList("SELECT id FROM businesses WHERE id = ? AND status = 'ACTIVE' FOR UPDATE", UUID.class, businessId).isEmpty()) {
            throw new ConfigurationException("business_unavailable", 403, "The active business is unavailable.");
        }
    }
    public Optional<BusinessConfiguration> find(UUID businessId) {
        return jdbc.query("SELECT * FROM business_configuration WHERE business_id = ?", (rs, row) ->
                new BusinessConfiguration(rs.getObject("business_id", UUID.class), OperatingProfile.valueOf(rs.getString("operating_profile")),
                        json.readValue(rs.getString("operating_answers"), new TypeReference<Map<String, Boolean>>() {}),
                        json.readValue(rs.getString("requested_modules"), new TypeReference<List<String>>() {}),
                        rs.getLong("version"), rs.getBoolean("configured"), rs.getObject("updated_by", UUID.class),
                        rs.getObject("updated_at", OffsetDateTime.class)), businessId).stream().findFirst();
    }
    public String legacyCategory(UUID businessId) {
        return jdbc.queryForList("SELECT business_type FROM business_profiles WHERE user_id = ?", String.class, businessId)
                .stream().findFirst().orElse("services");
    }
    public void save(BusinessConfiguration value) {
        jdbc.update("""
                INSERT INTO business_configuration(business_id, operating_profile, operating_answers, requested_modules,
                        version, configured, updated_by, updated_at)
                VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?)
                ON CONFLICT (business_id) DO UPDATE SET operating_profile = EXCLUDED.operating_profile,
                    operating_answers = EXCLUDED.operating_answers, requested_modules = EXCLUDED.requested_modules,
                    version = EXCLUDED.version, configured = EXCLUDED.configured, updated_by = EXCLUDED.updated_by,
                    updated_at = EXCLUDED.updated_at
                """, value.businessId(), value.operatingProfile().name(), json.writeValueAsString(value.operatingAnswers()),
                json.writeValueAsString(value.requestedModules()), value.version(), value.configured(), value.updatedBy(), value.updatedAt());
    }
}
