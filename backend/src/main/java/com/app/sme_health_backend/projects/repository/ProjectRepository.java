package com.app.sme_health_backend.projects.repository;

import com.app.sme_health_backend.projects.model.ProjectKind;
import com.app.sme_health_backend.projects.model.ProjectRecord;
import com.app.sme_health_backend.projects.model.ProjectStatus;
import com.app.sme_health_backend.projects.service.ProjectException;
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
public class ProjectRepository {
    private static final RowMapper<ProjectRecord> PROJECT_ROW_MAPPER = (rs, rowNum) -> {
        Date start = rs.getDate("start_date");
        Date end = rs.getDate("target_end_date");
        return new ProjectRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("business_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                ProjectKind.valueOf(rs.getString("kind")),
                rs.getString("description"),
                ProjectStatus.valueOf(rs.getString("status")),
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

    public ProjectRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lock Level 1: Acquire parent business row lock to serialize project creation and code uniqueness checks.
     */
    public void lockActiveBusiness(UUID businessId) {
        List<UUID> locked = jdbc.queryForList(
                "SELECT id FROM businesses WHERE id = ? AND status = 'ACTIVE' FOR UPDATE",
                UUID.class,
                businessId
        );
        if (locked.isEmpty()) {
            throw new ProjectException("business_unavailable", 403, "The active business is unavailable.");
        }
    }

    /**
     * Lock Level 2: Acquire project row lock for all project and budget mutations.
     */
    public Optional<ProjectRecord> findForUpdate(UUID businessId, UUID projectId) {
        return jdbc.query(
                "SELECT * FROM projects WHERE business_id = ? AND id = ? FOR UPDATE",
                PROJECT_ROW_MAPPER,
                businessId,
                projectId
        ).stream().findFirst();
    }

    public Optional<ProjectRecord> findById(UUID businessId, UUID projectId) {
        return jdbc.query(
                "SELECT * FROM projects WHERE business_id = ? AND id = ?",
                PROJECT_ROW_MAPPER,
                businessId,
                projectId
        ).stream().findFirst();
    }

    public boolean existsByCode(UUID businessId, String code, UUID excludeProjectId) {
        if (excludeProjectId == null) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM projects WHERE business_id = ? AND code = ?",
                    Integer.class,
                    businessId,
                    code
            );
            return count != null && count > 0;
        }
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM projects WHERE business_id = ? AND code = ? AND id <> ?",
                Integer.class,
                businessId,
                code,
                excludeProjectId
        );
        return count != null && count > 0;
    }

    public List<ProjectRecord> findAllByBusiness(
            UUID businessId,
            ProjectStatus status,
            ProjectKind kind,
            String query,
            boolean includeArchived
    ) {
        StringBuilder sql = new StringBuilder("SELECT * FROM projects WHERE business_id = ?");
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
        return jdbc.query(sql.toString(), PROJECT_ROW_MAPPER, params.toArray());
    }

    public void insert(ProjectRecord record) {
        jdbc.update("""
                INSERT INTO projects (
                    id, business_id, code, name, kind, description, status,
                    start_date, target_end_date, base_currency, version,
                    created_by, updated_by, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                record.id(),
                record.businessId(),
                record.code(),
                record.name(),
                record.kind().name(),
                record.description(),
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
            UUID projectId,
            long expectedVersion,
            String code,
            String name,
            ProjectKind kind,
            String description,
            LocalDate startDate,
            LocalDate targetEndDate,
            UUID updatedBy,
            OffsetDateTime updatedAt
    ) {
        int rows = jdbc.update("""
                UPDATE projects
                SET code = ?,
                    name = ?,
                    kind = ?,
                    description = ?,
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
                startDate != null ? Date.valueOf(startDate) : null,
                targetEndDate != null ? Date.valueOf(targetEndDate) : null,
                updatedBy,
                updatedAt,
                businessId,
                projectId,
                expectedVersion
        );
        return rows == 1;
    }

    public boolean updateStatus(
            UUID businessId,
            UUID projectId,
            long expectedVersion,
            ProjectStatus targetStatus,
            UUID updatedBy,
            OffsetDateTime updatedAt
    ) {
        int rows = jdbc.update("""
                UPDATE projects
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
                projectId,
                expectedVersion
        );
        return rows == 1;
    }

    public void incrementVersion(UUID businessId, UUID projectId, UUID updatedBy, OffsetDateTime updatedAt) {
        jdbc.update("""
                UPDATE projects
                SET version = version + 1,
                    updated_by = ?,
                    updated_at = ?
                WHERE business_id = ? AND id = ?
                """,
                updatedBy,
                updatedAt,
                businessId,
                projectId
        );
    }

    public boolean deleteDraft(UUID businessId, UUID projectId, long expectedVersion) {
        int rows = jdbc.update(
                "DELETE FROM projects WHERE business_id = ? AND id = ? AND status = 'DRAFT' AND version = ?",
                businessId,
                projectId,
                expectedVersion
        );
        return rows == 1;
    }
}
