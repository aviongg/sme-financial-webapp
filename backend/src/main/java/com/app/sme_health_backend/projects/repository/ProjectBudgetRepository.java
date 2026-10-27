package com.app.sme_health_backend.projects.repository;

import com.app.sme_health_backend.projects.model.ProjectBudgetStatus;
import com.app.sme_health_backend.projects.model.ProjectBudgetVersionRecord;
import com.app.sme_health_backend.projects.model.ProjectBudgetVersionRecord.LineRecord;
import com.app.sme_health_backend.projects.model.ProjectCostCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class ProjectBudgetRepository {
    private static final RowMapper<ProjectBudgetVersionRecord> VERSION_HEADER_MAPPER = (rs, rowNum) -> new ProjectBudgetVersionRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("business_id", UUID.class),
            rs.getObject("project_id", UUID.class),
            rs.getInt("revision"),
            ProjectBudgetStatus.valueOf(rs.getString("status")),
            rs.getString("base_currency").trim(),
            rs.getBigDecimal("planned_revenue"),
            rs.getBigDecimal("planned_cost"),
            rs.getBigDecimal("planned_margin"),
            rs.getBigDecimal("planned_margin_pct"),
            rs.getString("margin_unavailable_reason"),
            rs.getString("calculation_version"),
            rs.getString("source_estimate_ref"),
            rs.getLong("version"),
            rs.getObject("created_by", UUID.class),
            rs.getObject("updated_by", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("approved_by", UUID.class),
            rs.getObject("approved_at", OffsetDateTime.class),
            rs.getObject("superseded_by_budget_id", UUID.class),
            rs.getObject("superseded_at", OffsetDateTime.class),
            List.of()
    );

    private static final RowMapper<LineRecord> LINE_ROW_MAPPER = (rs, rowNum) -> new LineRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("business_id", UUID.class),
            rs.getObject("project_id", UUID.class),
            rs.getObject("budget_version_id", UUID.class),
            rs.getInt("line_number"),
            ProjectCostCategory.valueOf(rs.getString("category")),
            rs.getString("description"),
            rs.getBigDecimal("quantity"),
            rs.getString("unit"),
            rs.getBigDecimal("unit_cost"),
            rs.getBigDecimal("planned_line_cost")
    );

    private final JdbcTemplate jdbc;

    public ProjectBudgetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lock Level 3: Lock all budget versions for a project in deterministic ascending (revision, id) order
     * after the parent project row is locked.
     */
    public List<ProjectBudgetVersionRecord> lockAllByProjectOrderByRevisionAsc(UUID businessId, UUID projectId) {
        List<ProjectBudgetVersionRecord> headers = jdbc.query("""
                SELECT * FROM project_budget_versions
                WHERE business_id = ? AND project_id = ?
                ORDER BY revision ASC, id ASC
                FOR UPDATE
                """,
                VERSION_HEADER_MAPPER,
                businessId,
                projectId
        );
        return attachLines(businessId, headers);
    }

    public List<ProjectBudgetVersionRecord> findAllByProjectOrderByRevisionDesc(UUID businessId, UUID projectId) {
        List<ProjectBudgetVersionRecord> headers = jdbc.query("""
                SELECT * FROM project_budget_versions
                WHERE business_id = ? AND project_id = ?
                ORDER BY revision DESC, id ASC
                """,
                VERSION_HEADER_MAPPER,
                businessId,
                projectId
        );
        return attachLines(businessId, headers);
    }

    public Map<UUID, List<ProjectBudgetVersionRecord>> findAllByProjectsOrderByRevisionDesc(
            UUID businessId,
            Collection<UUID> projectIds
    ) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(projectIds.size(), "?"));
        List<Object> args = new ArrayList<>(projectIds.size() + 1);
        args.add(businessId);
        args.addAll(projectIds);

        List<ProjectBudgetVersionRecord> headers = jdbc.query(
                "SELECT * FROM project_budget_versions WHERE business_id = ? AND project_id IN (" + placeholders + ") ORDER BY revision DESC, id ASC",
                VERSION_HEADER_MAPPER,
                args.toArray()
        );
        List<ProjectBudgetVersionRecord> withLines = attachLines(businessId, headers);
        Map<UUID, List<ProjectBudgetVersionRecord>> byProject = new LinkedHashMap<>();
        for (UUID projectId : projectIds) {
            byProject.put(projectId, new ArrayList<>());
        }
        for (ProjectBudgetVersionRecord version : withLines) {
            byProject.computeIfAbsent(version.projectId(), ignored -> new ArrayList<>()).add(version);
        }
        return byProject;
    }

    public void insertDraft(ProjectBudgetVersionRecord version) {
        jdbc.update("""
                INSERT INTO project_budget_versions (
                    id, business_id, project_id, revision, status, base_currency,
                    planned_revenue, planned_cost, planned_margin, planned_margin_pct,
                    margin_unavailable_reason, calculation_version, source_estimate_ref,
                    version, created_by, updated_by, created_at, updated_at,
                    approved_by, approved_at, superseded_by_budget_id, superseded_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                version.id(),
                version.businessId(),
                version.projectId(),
                version.revision(),
                version.status().name(),
                version.baseCurrency(),
                version.plannedRevenue(),
                version.plannedCost(),
                version.plannedMargin(),
                version.plannedMarginPct(),
                version.marginUnavailableReason(),
                version.calculationVersion(),
                version.sourceEstimateRef(),
                version.version(),
                version.createdBy(),
                version.updatedBy(),
                version.createdAt(),
                version.updatedAt(),
                version.approvedBy(),
                version.approvedAt(),
                version.supersededByBudgetId(),
                version.supersededAt()
        );
        insertLines(version.lines());
    }

    public boolean replaceDraft(
            UUID businessId,
            UUID projectId,
            UUID budgetVersionId,
            long expectedBudgetVersion,
            BigDecimal plannedRevenue,
            BigDecimal plannedCost,
            BigDecimal plannedMargin,
            BigDecimal plannedMarginPct,
            String marginUnavailableReason,
            List<LineRecord> lines,
            UUID updatedBy,
            OffsetDateTime updatedAt
    ) {
        int updated = jdbc.update("""
                UPDATE project_budget_versions
                SET planned_revenue = ?,
                    planned_cost = ?,
                    planned_margin = ?,
                    planned_margin_pct = ?,
                    margin_unavailable_reason = ?,
                    version = version + 1,
                    updated_by = ?,
                    updated_at = ?
                WHERE business_id = ? AND project_id = ? AND id = ? AND status = 'DRAFT' AND version = ?
                """,
                plannedRevenue,
                plannedCost,
                plannedMargin,
                plannedMarginPct,
                marginUnavailableReason,
                updatedBy,
                updatedAt,
                businessId,
                projectId,
                budgetVersionId,
                expectedBudgetVersion
        );
        if (updated != 1) {
            return false;
        }
        jdbc.update(
                "DELETE FROM project_budget_lines WHERE business_id = ? AND project_id = ? AND budget_version_id = ?",
                businessId,
                projectId,
                budgetVersionId
        );
        insertLines(lines);
        return true;
    }

    public boolean deleteDraft(
            UUID businessId,
            UUID projectId,
            UUID budgetVersionId,
            long expectedBudgetVersion
    ) {
        int rows = jdbc.update("""
                DELETE FROM project_budget_versions
                WHERE business_id = ? AND project_id = ? AND id = ? AND status = 'DRAFT' AND version = ?
                """,
                businessId,
                projectId,
                budgetVersionId,
                expectedBudgetVersion
        );
        return rows == 1;
    }

    public void deleteAllDraftsForProject(UUID businessId, UUID projectId) {
        jdbc.update(
                "DELETE FROM project_budget_versions WHERE business_id = ? AND project_id = ? AND status = 'DRAFT'",
                businessId,
                projectId
        );
    }

    public boolean supersedeApproved(
            UUID businessId,
            UUID projectId,
            UUID currentApprovedId,
            UUID supersededByBudgetId,
            UUID updatedBy,
            OffsetDateTime now
    ) {
        int rows = jdbc.update("""
                UPDATE project_budget_versions
                SET status = 'SUPERSEDED',
                    superseded_by_budget_id = ?,
                    superseded_at = ?,
                    updated_by = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE business_id = ? AND project_id = ? AND id = ? AND status = 'APPROVED'
                """,
                supersededByBudgetId,
                now,
                updatedBy,
                now,
                businessId,
                projectId,
                currentApprovedId
        );
        return rows == 1;
    }

    public boolean approveDraft(
            UUID businessId,
            UUID projectId,
            UUID draftBudgetId,
            long expectedBudgetVersion,
            UUID approvedBy,
            OffsetDateTime now
    ) {
        int rows = jdbc.update("""
                UPDATE project_budget_versions
                SET status = 'APPROVED',
                    approved_by = ?,
                    approved_at = ?,
                    updated_by = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE business_id = ? AND project_id = ? AND id = ? AND status = 'DRAFT' AND version = ?
                """,
                approvedBy,
                now,
                approvedBy,
                now,
                businessId,
                projectId,
                draftBudgetId,
                expectedBudgetVersion
        );
        return rows == 1;
    }

    private void insertLines(List<LineRecord> lines) {
        for (LineRecord line : lines) {
            jdbc.update("""
                    INSERT INTO project_budget_lines (
                        id, business_id, project_id, budget_version_id,
                        line_number, category, description, quantity, unit, unit_cost, planned_line_cost
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    line.id(),
                    line.businessId(),
                    line.projectId(),
                    line.budgetVersionId(),
                    line.lineNumber(),
                    line.category().name(),
                    line.description(),
                    line.quantity(),
                    line.unit(),
                    line.unitCost(),
                    line.plannedLineCost()
            );
        }
    }

    private List<ProjectBudgetVersionRecord> attachLines(UUID businessId, List<ProjectBudgetVersionRecord> headers) {
        if (headers.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(headers.size(), "?"));
        List<Object> args = new ArrayList<>(headers.size() + 1);
        args.add(businessId);
        for (ProjectBudgetVersionRecord header : headers) {
            args.add(header.id());
        }

        List<LineRecord> allLines = jdbc.query(
                "SELECT * FROM project_budget_lines WHERE business_id = ? AND budget_version_id IN (" + placeholders + ") ORDER BY budget_version_id, line_number ASC",
                LINE_ROW_MAPPER,
                args.toArray()
        );
        Map<UUID, List<LineRecord>> byVersion = new LinkedHashMap<>();
        for (ProjectBudgetVersionRecord header : headers) {
            byVersion.put(header.id(), new ArrayList<>());
        }
        for (LineRecord line : allLines) {
            byVersion.computeIfAbsent(line.budgetVersionId(), ignored -> new ArrayList<>()).add(line);
        }

        return headers.stream().map(header -> new ProjectBudgetVersionRecord(
                header.id(),
                header.businessId(),
                header.projectId(),
                header.revision(),
                header.status(),
                header.baseCurrency(),
                header.plannedRevenue(),
                header.plannedCost(),
                header.plannedMargin(),
                header.plannedMarginPct(),
                header.marginUnavailableReason(),
                header.calculationVersion(),
                header.sourceEstimateRef(),
                header.version(),
                header.createdBy(),
                header.updatedBy(),
                header.createdAt(),
                header.updatedAt(),
                header.approvedBy(),
                header.approvedAt(),
                header.supersededByBudgetId(),
                header.supersededAt(),
                List.copyOf(byVersion.getOrDefault(header.id(), List.of()))
        )).toList();
    }
}
