package com.app.sme_health_backend.samples.repository;

import com.app.sme_health_backend.samples.model.SampleKind;
import com.app.sme_health_backend.samples.model.RecoveryIntent;
import com.app.sme_health_backend.samples.model.SampleRecord;
import com.app.sme_health_backend.samples.model.SampleStatus;
import com.app.sme_health_backend.samples.service.SampleException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SampleRepository {
    private static final RowMapper<SampleRecord> SAMPLE_ROW_MAPPER = (rs, rowNum) -> {
        Date start = rs.getDate("start_date");
        Date end = rs.getDate("target_end_date");
        return new SampleRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("business_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                SampleKind.valueOf(rs.getString("kind")),
                rs.getString("description"),
                RecoveryIntent.valueOf(rs.getString("recovery_intent")),
                rs.getObject("project_id", UUID.class),
                SampleStatus.valueOf(rs.getString("status")),
                start != null ? start.toLocalDate() : null,
                end != null ? end.toLocalDate() : null,
                rs.getString("base_currency").trim(),
                rs.getLong("version"),
                rs.getObject("created_by", UUID.class),
                rs.getObject("updated_by", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    };

    private final JdbcTemplate jdbc;

    public SampleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lock Level 1: Acquire parent business row lock to serialize activity creation and code uniqueness checks.
     */
    public void lockActiveBusiness(UUID businessId) {
        List<UUID> locked = jdbc.queryForList(
                "SELECT id FROM businesses WHERE id = ? AND status = 'ACTIVE' FOR UPDATE",
                UUID.class,
                businessId
        );
        if (locked.isEmpty()) {
            throw new SampleException("business_unavailable", 403, "The active business is unavailable.");
        }
    }

    /**
     * Lock Level 2: Acquire activity row lock for all activity and plan mutations.
     */
    public Optional<SampleRecord> findForUpdate(UUID businessId, UUID activityId) {
        return jdbc.query(
                "SELECT * FROM sample_activities WHERE business_id = ? AND id = ? FOR UPDATE",
                SAMPLE_ROW_MAPPER,
                businessId,
                activityId
        ).stream().findFirst();
    }

    public Optional<SampleRecord> findById(UUID businessId, UUID activityId) {
        return jdbc.query(
                "SELECT * FROM sample_activities WHERE business_id = ? AND id = ?",
                SAMPLE_ROW_MAPPER,
                businessId,
                activityId
        ).stream().findFirst();
    }

    /** Read/link only; locks existing project against concurrent archival without changing it. */
    public void validateProjectLink(UUID businessId, UUID projectId, UUID previousProjectId) {
        if (projectId == null) return;
        List<String> status = jdbc.queryForList(
                "SELECT status FROM projects WHERE business_id = ? AND id = ? FOR SHARE", String.class, businessId, projectId);
        if (status.isEmpty()) throw new SampleException("project_not_found", 404, "Project not found.");
        if ("ARCHIVED".equals(status.getFirst()) && !projectId.equals(previousProjectId)) {
            throw new SampleException("project_unavailable", 409, "Archived projects cannot be newly linked.");
        }
    }

    public boolean existsByCode(UUID businessId, String code, UUID excludeActivityId) {
        if (excludeActivityId == null) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM sample_activities WHERE business_id = ? AND code = ?",
                    Integer.class,
                    businessId,
                    code
            );
            return count != null && count > 0;
        }
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM sample_activities WHERE business_id = ? AND code = ? AND id <> ?",
                Integer.class,
                businessId,
                code,
                excludeActivityId
        );
        return count != null && count > 0;
    }

    public List<SampleRecord> findAllByBusiness(
            UUID businessId,
            SampleStatus status,
            SampleKind kind,
            String query,
            boolean includeArchived
    ) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sample_activities WHERE business_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(businessId);

        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.name());
        } else if (!includeArchived) {
            sql.append(" AND status <> 'ARCHIVED'");
        }

        if (kind != null) {
            sql.append(" AND kind = ?");
            params.add(kind.name());
        }

        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            sql.append(" AND (LOWER(code) LIKE ? OR LOWER(name) LIKE ?)");
            params.add(pattern);
            params.add(pattern);
        }

        sql.append(" ORDER BY updated_at DESC, id ASC LIMIT 200");
        return jdbc.query(sql.toString(), SAMPLE_ROW_MAPPER, params.toArray());
    }

    public void insert(SampleRecord record) {
        jdbc.update("""
                INSERT INTO sample_activities (
                    id, business_id, code, name, kind, description, recovery_intent, project_id, status,
                    start_date, target_end_date, base_currency, version,
                    created_by, updated_by, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                record.id(),
                record.businessId(),
                record.code(),
                record.name(),
                record.kind().name(),
                record.description(),
                record.recoveryIntent().name(),
                record.projectId(),
                record.status().name(),
                record.startDate() != null ? Date.valueOf(record.startDate()) : null,
                record.targetEndDate() != null ? Date.valueOf(record.targetEndDate()) : null,
                record.baseCurrency(),
                record.version(),
                record.createdBy(),
                record.updatedBy(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    public boolean updateMetadata(
            UUID businessId,
            UUID activityId,
            long expectedVersion,
            String code,
            String name,
            SampleKind kind,
            String description,
            RecoveryIntent recoveryIntent,
            UUID projectId,
            LocalDate startDate,
            LocalDate targetEndDate,
            UUID updatedBy,
            OffsetDateTime updatedAt
    ) {
        int rows = jdbc.update("""
                UPDATE sample_activities
                SET code = ?,
                    name = ?,
                    kind = ?,
                    description = ?,
                    recovery_intent = ?,
                    project_id = ?,
                    start_date = ?,
                    target_end_date = ?,
                    version = version + 1,
                    updated_by = ?,
                    updated_at = ?
                WHERE business_id = ? AND id = ? AND version = ?
                """,
                code,
                name,
                kind.name(),
                description,
                recoveryIntent.name(),
                projectId,
                startDate != null ? Date.valueOf(startDate) : null,
                targetEndDate != null ? Date.valueOf(targetEndDate) : null,
                updatedBy,
                updatedAt,
                businessId,
                activityId,
                expectedVersion
        );
        return rows == 1;
    }

    public boolean updateStatus(
            UUID businessId,
            UUID activityId,
            long expectedVersion,
            SampleStatus targetStatus,
            UUID updatedBy,
            OffsetDateTime updatedAt
    ) {
        int rows = jdbc.update("""
                UPDATE sample_activities
                SET status = ?,
                    version = version + 1,
                    updated_by = ?,
                    updated_at = ?
                WHERE business_id = ? AND id = ? AND version = ?
                """,
                targetStatus.name(),
                updatedBy,
                updatedAt,
                businessId,
                activityId,
                expectedVersion
        );
        return rows == 1;
    }

    public void incrementVersion(UUID businessId, UUID activityId, UUID updatedBy, OffsetDateTime updatedAt) {
        jdbc.update("""
                UPDATE sample_activities
                SET version = version + 1,
                    updated_by = ?,
                    updated_at = ?
                WHERE business_id = ? AND id = ?
                """,
                updatedBy,
                updatedAt,
                businessId,
                activityId
        );
    }

    public boolean deleteDraft(UUID businessId, UUID activityId, long expectedVersion) {
        int rows = jdbc.update(
                "DELETE FROM sample_activities WHERE business_id = ? AND id = ? AND status = 'DRAFT' AND version = ?",
                businessId,
                activityId,
                expectedVersion
        );
        return rows == 1;
    }
}
