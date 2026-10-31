package com.app.sme_health_backend.samples.repository;

import com.app.sme_health_backend.samples.model.SamplePlanStatus;
import com.app.sme_health_backend.samples.model.SamplePlanVersionRecord;
import com.app.sme_health_backend.samples.model.SamplePlanVersionRecord.LineRecord;
import com.app.sme_health_backend.projects.model.ProjectCostCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import com.app.sme_health_backend.samples.model.RecoveryIntent;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class SamplePlanRepository {
    private static final RowMapper<SamplePlanVersionRecord> VERSION_HEADER_MAPPER = (rs, rowNum) -> new SamplePlanVersionRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("business_id", UUID.class),
            rs.getObject("activity_id", UUID.class),
            rs.getInt("revision"),
            SamplePlanStatus.valueOf(rs.getString("status")),
            rs.getString("base_currency").trim(),
            RecoveryIntent.valueOf(rs.getString("recovery_intent")),
            rs.getBigDecimal("planned_recovery"),
            rs.getBigDecimal("planned_cost"),
            rs.getBigDecimal("planned_contribution"),
            rs.getString("contribution_unavailable_reason"),
            rs.getString("calculation_version"),
            rs.getLong("version"),
            rs.getObject("created_by", UUID.class),
            rs.getObject("updated_by", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("approved_by", UUID.class),
            rs.getObject("approved_at", OffsetDateTime.class),
            rs.getObject("superseded_by_plan_id", UUID.class),
            rs.getObject("superseded_at", OffsetDateTime.class),
            List.of()
    );

    private static final RowMapper<LineRecord> LINE_ROW_MAPPER = (rs, rowNum) -> new LineRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("business_id", UUID.class),
            rs.getObject("activity_id", UUID.class),
            rs.getObject("plan_version_id", UUID.class),
            rs.getInt("line_number"),
            ProjectCostCategory.valueOf(rs.getString("category")),
            rs.getString("description"),
            rs.getBigDecimal("quantity"),
            rs.getString("unit"),
            rs.getBigDecimal("unit_cost"),
            rs.getBigDecimal("planned_line_cost")
    );

    private final JdbcTemplate jdbc;

    public SamplePlanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lock Level 3: Lock all plan versions for a activity in deterministic ascending (revision, id) order
     * after the parent activity row is locked.
     */
    public List<SamplePlanVersionRecord> lockAllByActivityOrderByRevisionAsc(UUID businessId, UUID activityId) {
        List<SamplePlanVersionRecord> headers = jdbc.query("""
                SELECT * FROM sample_cost_plan_versions
                WHERE business_id = ? AND activity_id = ?
                ORDER BY revision ASC, id ASC
                FOR UPDATE
                """,
                VERSION_HEADER_MAPPER,
                businessId,
                activityId
        );
        return attachLines(businessId, headers);
    }

    public List<SamplePlanVersionRecord> findAllByActivityOrderByRevisionDesc(UUID businessId, UUID activityId) {
        List<SamplePlanVersionRecord> headers = jdbc.query("""
                SELECT * FROM sample_cost_plan_versions
                WHERE business_id = ? AND activity_id = ?
                ORDER BY revision DESC, id ASC
                """,
                VERSION_HEADER_MAPPER,
                businessId,
                activityId
        );
        return attachLines(businessId, headers);
    }

    public Map<UUID, List<SamplePlanVersionRecord>> findAllByActivitiesOrderByRevisionDesc(
            UUID businessId,
            Collection<UUID> activityIds
    ) {
        if (activityIds == null || activityIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(activityIds.size(), "?"));
        List<Object> args = new ArrayList<>(activityIds.size() + 1);
        args.add(businessId);
        args.addAll(activityIds);

        List<SamplePlanVersionRecord> headers = jdbc.query(
                "SELECT * FROM sample_cost_plan_versions WHERE business_id = ? AND activity_id IN (" + placeholders + ") ORDER BY revision DESC, id ASC",
                VERSION_HEADER_MAPPER,
                args.toArray()
        );
        List<SamplePlanVersionRecord> withLines = attachLines(businessId, headers);
        Map<UUID, List<SamplePlanVersionRecord>> bySample = new LinkedHashMap<>();
        for (UUID activityId : activityIds) {
            bySample.put(activityId, new ArrayList<>());
        }
        for (SamplePlanVersionRecord version : withLines) {
            bySample.computeIfAbsent(version.activityId(), ignored -> new ArrayList<>()).add(version);
        }
        return bySample;
    }

    public void insertDraft(SamplePlanVersionRecord version) {
        jdbc.update("""
                INSERT INTO sample_cost_plan_versions (
                    id, business_id, activity_id, revision, status, base_currency, recovery_intent,
                    planned_recovery, planned_cost, planned_contribution,
                    contribution_unavailable_reason, calculation_version,
                    version, created_by, updated_by, created_at, updated_at,
                    approved_by, approved_at, superseded_by_plan_id, superseded_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                version.id(),
                version.businessId(),
                version.activityId(),
                version.revision(),
                version.status().name(),
                version.baseCurrency(),
                version.recoveryIntent().name(),
                version.plannedRecovery(),
                version.plannedCost(),
                version.plannedContribution(),
                version.contributionUnavailableReason(),
                version.calculationVersion(),
                version.version(),
                version.createdBy(),
                version.updatedBy(),
                version.createdAt(),
                version.updatedAt(),
                version.approvedBy(),
                version.approvedAt(),
                version.supersededByPlanId(),
                version.supersededAt()
        );
        insertLines(version.lines());
    }

    public boolean replaceDraft(
            UUID businessId,
            UUID activityId,
            UUID planVersionId,
            long expectedPlanVersion,
            BigDecimal plannedRecovery,
            BigDecimal plannedCost,
            BigDecimal plannedContribution,
            String contributionUnavailableReason,
            List<LineRecord> lines,
            UUID updatedBy,
            OffsetDateTime updatedAt
    ) {
        int updated = jdbc.update("""
                UPDATE sample_cost_plan_versions
                SET planned_recovery = ?,
                    planned_cost = ?,
                    planned_contribution = ?,
                    contribution_unavailable_reason = ?,
                    version = version + 1,
                    updated_by = ?,
                    updated_at = ?
                WHERE business_id = ? AND activity_id = ? AND id = ? AND status = 'DRAFT' AND version = ?
                """,
                plannedRecovery,
                plannedCost,
                plannedContribution,
                contributionUnavailableReason,
                updatedBy,
                updatedAt,
                businessId,
                activityId,
                planVersionId,
                expectedPlanVersion
        );
        if (updated != 1) {
            return false;
        }
        jdbc.update(
                "DELETE FROM sample_cost_plan_lines WHERE business_id = ? AND activity_id = ? AND plan_version_id = ?",
                businessId,
                activityId,
                planVersionId
        );
        insertLines(lines);
        return true;
    }

    public boolean deleteDraft(
            UUID businessId,
            UUID activityId,
            UUID planVersionId,
            long expectedPlanVersion
    ) {
        int rows = jdbc.update("""
                DELETE FROM sample_cost_plan_versions
                WHERE business_id = ? AND activity_id = ? AND id = ? AND status = 'DRAFT' AND version = ?
                """,
                businessId,
                activityId,
                planVersionId,
                expectedPlanVersion
        );
        return rows == 1;
    }

    public void deleteAllDraftsForActivity(UUID businessId, UUID activityId) {
        jdbc.update(
                "DELETE FROM sample_cost_plan_versions WHERE business_id = ? AND activity_id = ? AND status = 'DRAFT'",
                businessId,
                activityId
        );
    }

    public boolean supersedeApproved(
            UUID businessId,
            UUID activityId,
            UUID currentApprovedId,
            UUID supersededByPlanId,
            UUID updatedBy,
            OffsetDateTime now
    ) {
        int rows = jdbc.update("""
                UPDATE sample_cost_plan_versions
                SET status = 'SUPERSEDED',
                    superseded_by_plan_id = ?,
                    superseded_at = ?,
                    updated_by = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE business_id = ? AND activity_id = ? AND id = ? AND status = 'APPROVED'
                """,
                supersededByPlanId,
                now,
                updatedBy,
                now,
                businessId,
                activityId,
                currentApprovedId
        );
        return rows == 1;
    }

    public boolean approveDraft(
            UUID businessId,
            UUID activityId,
            UUID draftPlanId,
            long expectedPlanVersion,
            UUID approvedBy,
            OffsetDateTime now
    ) {
        int rows = jdbc.update("""
                UPDATE sample_cost_plan_versions
                SET status = 'APPROVED',
                    approved_by = ?,
                    approved_at = ?,
                    updated_by = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE business_id = ? AND activity_id = ? AND id = ? AND status = 'DRAFT' AND version = ?
                """,
                approvedBy,
                now,
                approvedBy,
                now,
                businessId,
                activityId,
                draftPlanId,
                expectedPlanVersion
        );
        return rows == 1;
    }

    private void insertLines(List<LineRecord> lines) {
        for (LineRecord line : lines) {
            jdbc.update("""
                    INSERT INTO sample_cost_plan_lines (
                        id, business_id, activity_id, plan_version_id,
                        line_number, category, description, quantity, unit, unit_cost, planned_line_cost
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    line.id(),
                    line.businessId(),
                    line.activityId(),
                    line.planVersionId(),
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

    private List<SamplePlanVersionRecord> attachLines(UUID businessId, List<SamplePlanVersionRecord> headers) {
        if (headers.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(headers.size(), "?"));
        List<Object> args = new ArrayList<>(headers.size() + 1);
        args.add(businessId);
        for (SamplePlanVersionRecord header : headers) {
            args.add(header.id());
        }

        List<LineRecord> allLines = jdbc.query(
                "SELECT * FROM sample_cost_plan_lines WHERE business_id = ? AND plan_version_id IN (" + placeholders + ") ORDER BY plan_version_id, line_number ASC",
                LINE_ROW_MAPPER,
                args.toArray()
        );
        Map<UUID, List<LineRecord>> byVersion = new LinkedHashMap<>();
        for (SamplePlanVersionRecord header : headers) {
            byVersion.put(header.id(), new ArrayList<>());
        }
        for (LineRecord line : allLines) {
            byVersion.computeIfAbsent(line.planVersionId(), ignored -> new ArrayList<>()).add(line);
        }

        return headers.stream().map(header -> new SamplePlanVersionRecord(
                header.id(),
                header.businessId(),
                header.activityId(),
                header.revision(),
                header.status(),
                header.baseCurrency(),
                header.recoveryIntent(),
                header.plannedRecovery(),
                header.plannedCost(),
                header.plannedContribution(),
                header.contributionUnavailableReason(),
                header.calculationVersion(),
                header.version(),
                header.createdBy(),
                header.updatedBy(),
                header.createdAt(),
                header.updatedAt(),
                header.approvedBy(),
                header.approvedAt(),
                header.supersededByPlanId(),
                header.supersededAt(),
                List.copyOf(byVersion.getOrDefault(header.id(), List.of()))
        )).toList();
    }
}
