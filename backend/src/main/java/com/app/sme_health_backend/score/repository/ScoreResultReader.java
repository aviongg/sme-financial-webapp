package com.app.sme_health_backend.score.repository;

import com.app.sme_health_backend.score.dto.ScoreResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only adapter to Suleman's score_results table. This deliberately declares no scoring
 * entity or calculator, so merging the scoring module cannot create duplicate JPA mappings.
 * Row locks remain held by the caller's advice-generation transaction until its writes commit.
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class ScoreResultReader {

    private static final String SELECT = """
            SELECT user_id, month, composite_score, band, component_scores,
                   weakest_component, data_completeness, computed_at
            FROM score_results
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ScoreResultReader(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<ScoreResult> findLatest(UUID userId) {
        requireUserId(userId);
        return jdbcTemplate.query(SELECT + " WHERE user_id = ? ORDER BY month DESC LIMIT 1 FOR SHARE",
                this::mapRow, userId).stream().findFirst();
    }

    public Optional<ScoreResult> findByUserIdAndMonth(UUID userId, String month) {
        requireUserId(userId);
        ScoreResult.validateMonth(month);
        return jdbcTemplate.query(SELECT + " WHERE user_id = ? AND month = ? FOR SHARE",
                this::mapRow, userId, month).stream().findFirst();
    }

    private ScoreResult mapRow(ResultSet row, int rowNumber) throws SQLException {
        try {
            ScoreResult score = new ScoreResult(
                    row.getObject("user_id", UUID.class), row.getString("month"),
                    row.getBigDecimal("composite_score"), row.getString("band"),
                    readComponents(row.getString("component_scores")), row.getString("weakest_component"),
                    row.getBigDecimal("data_completeness"),
                    row.getTimestamp("computed_at") == null ? null : row.getTimestamp("computed_at").toLocalDateTime()
            );
            score.validate();
            return score;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Persisted score does not satisfy the shared scoring contract", exception);
        }
    }

    private Map<String, BigDecimal> readComponents(String json) {
        if (json == null) {
            throw new IllegalArgumentException("Component scores are required");
        }
        JsonNode components = objectMapper.readTree(json);
        if (components == null || !components.isObject() || components.size() != ScoreResult.COMPONENT_KEYS.size()
                || ScoreResult.COMPONENT_KEYS.stream().anyMatch(key -> !components.has(key))) {
            throw new IllegalArgumentException("Persisted components must use exactly the five canonical keys");
        }
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (String key : ScoreResult.COMPONENT_KEYS) {
            JsonNode value = components.get(key);
            if (!value.isNull() && !value.isNumber()) {
                throw new IllegalArgumentException("Component scores must be numeric or null");
            }
            values.put(key, value.isNull() ? null : value.decimalValue());
        }
        return values;
    }

    private static void requireUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
    }
}
