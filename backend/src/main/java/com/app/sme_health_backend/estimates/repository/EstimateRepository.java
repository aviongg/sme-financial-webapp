package com.app.sme_health_backend.estimates.repository;

import com.app.sme_health_backend.estimates.dto.EstimateLineRequest.CostGroup;
import com.app.sme_health_backend.estimates.dto.PricingPreviewRequest.PricingMode;
import com.app.sme_health_backend.estimates.model.EstimateConversionRecord;
import com.app.sme_health_backend.estimates.model.EstimateRecord;
import com.app.sme_health_backend.estimates.model.EstimateRevisionRecord;
import com.app.sme_health_backend.estimates.model.EstimateRevisionStatus;
import com.app.sme_health_backend.estimates.model.EstimateStatus;
import com.app.sme_health_backend.estimates.service.EstimateCostCalculator.RawLine;
import com.app.sme_health_backend.estimates.service.PricingCalculator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped JDBC access to the F06 Phase B tables only. Every statement filters by business_id.
 * Lock order is always estimate row first, then its revisions in (revision, id) order.
 */
@Repository
public class EstimateRepository {
    private static final RowMapper<EstimateRecord> ESTIMATE = (rs, row) -> new EstimateRecord(
            rs.getObject("id", UUID.class), rs.getObject("business_id", UUID.class), rs.getString("code"), rs.getString("name"),
            rs.getString("description"), EstimateStatus.valueOf(rs.getString("status")), rs.getString("base_currency").trim(),
            rs.getLong("version"), rs.getObject("created_by_user_id", UUID.class), rs.getObject("updated_by_user_id", UUID.class),
            rs.getObject("archived_by_user_id", UUID.class), rs.getObject("archived_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));

    private static final RowMapper<EstimateRevisionRecord> REVISION = (rs, row) -> new EstimateRevisionRecord(
            rs.getObject("id", UUID.class), rs.getObject("business_id", UUID.class), rs.getObject("estimate_id", UUID.class),
            rs.getInt("revision"), EstimateRevisionStatus.valueOf(rs.getString("status")), PricingMode.valueOf(rs.getString("pricing_mode")),
            rs.getBigDecimal("pricing_value"), rs.getBigDecimal("saleable_quantity"), rs.getBigDecimal("total_planned_cost"),
            rs.getBigDecimal("planned_batch_price"), rs.getBigDecimal("planned_contribution"), rs.getBigDecimal("planned_margin"),
            rs.getBigDecimal("planned_markup"), rs.getBigDecimal("planned_per_unit_price"), rs.getString("calculation_version"),
            rs.getLong("version"), rs.getObject("created_by_user_id", UUID.class), rs.getObject("updated_by_user_id", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("approved_by_user_id", UUID.class), rs.getObject("approved_at", OffsetDateTime.class),
            rs.getObject("superseded_by_version_id", UUID.class), rs.getObject("superseded_at", OffsetDateTime.class), List.of());

    private static final RowMapper<EstimateConversionRecord> CONVERSION = (rs, row) -> new EstimateConversionRecord(
            rs.getObject("id", UUID.class), rs.getObject("business_id", UUID.class), rs.getObject("estimate_id", UUID.class),
            rs.getObject("estimate_version_id", UUID.class), rs.getObject("project_id", UUID.class),
            rs.getObject("project_budget_version_id", UUID.class), rs.getString("conversion_contract_version"),
            rs.getString("source_calculation_version"), rs.getString("target_calculation_version"),
            rs.getObject("converted_by_user_id", UUID.class), rs.getObject("converted_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public EstimateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- estimates ----

    public void insertEstimate(EstimateRecord estimate) {
        jdbc.update("""
                INSERT INTO estimates (id, business_id, code, name, description, status, base_currency, version,
                    created_by_user_id, updated_by_user_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'PKR', 0, ?, ?, ?, ?)
                """, estimate.id(), estimate.businessId(), estimate.code(), estimate.name(), estimate.description(),
                estimate.createdByUserId(), estimate.updatedByUserId(), estimate.createdAt(), estimate.updatedAt());
    }

    public Optional<EstimateRecord> findEstimate(UUID businessId, UUID id) {
        return jdbc.query("SELECT * FROM estimates WHERE business_id = ? AND id = ?", ESTIMATE, businessId, id).stream().findFirst();
    }

    public Optional<EstimateRecord> lockEstimate(UUID businessId, UUID id) {
        return jdbc.query("SELECT * FROM estimates WHERE business_id = ? AND id = ? FOR UPDATE", ESTIMATE, businessId, id)
                .stream().findFirst();
    }

    public List<EstimateRecord> listEstimates(UUID businessId, EstimateStatus status, boolean includeArchived) {
        if (status != null) {
            return jdbc.query("SELECT * FROM estimates WHERE business_id = ? AND status = ? ORDER BY updated_at DESC, id",
                    ESTIMATE, businessId, status.name());
        }
        return jdbc.query("SELECT * FROM estimates WHERE business_id = ? AND (? OR status = 'ACTIVE') ORDER BY updated_at DESC, id",
                ESTIMATE, businessId, includeArchived);
    }

    public boolean codeExists(UUID businessId, String code, UUID excludeId) {
        Boolean exists = excludeId == null
                ? jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM estimates WHERE business_id = ? AND code = ?)",
                        Boolean.class, businessId, code)
                : jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM estimates WHERE business_id = ? AND code = ? AND id <> ?)",
                        Boolean.class, businessId, code, excludeId);
        return Boolean.TRUE.equals(exists);
    }

    public boolean updateMetadata(UUID businessId, UUID id, long expectedVersion, String code, String name, String description,
                                  UUID actor, OffsetDateTime now) {
        return jdbc.update("""
                UPDATE estimates SET code = ?, name = ?, description = ?, version = version + 1, updated_by_user_id = ?, updated_at = ?
                WHERE business_id = ? AND id = ? AND status = 'ACTIVE' AND version = ?
                """, code, name, description, actor, now, businessId, id, expectedVersion) == 1;
    }

    public void incrementVersion(UUID businessId, UUID id, UUID actor, OffsetDateTime now) {
        jdbc.update("UPDATE estimates SET version = version + 1, updated_by_user_id = ?, updated_at = ? WHERE business_id = ? AND id = ?",
                actor, now, businessId, id);
    }

    public boolean archive(UUID businessId, UUID id, long expectedVersion, UUID actor, OffsetDateTime now) {
        return jdbc.update("""
                UPDATE estimates SET status = 'ARCHIVED', archived_by_user_id = ?, archived_at = ?, version = version + 1,
                    updated_by_user_id = ?, updated_at = ?
                WHERE business_id = ? AND id = ? AND status = 'ACTIVE' AND version = ?
                """, actor, now, actor, now, businessId, id, expectedVersion) == 1;
    }

    public boolean deleteEstimate(UUID businessId, UUID id, long expectedVersion) {
        jdbc.update("DELETE FROM estimate_versions WHERE business_id = ? AND estimate_id = ? AND status = 'DRAFT'", businessId, id);
        return jdbc.update("DELETE FROM estimates WHERE business_id = ? AND id = ? AND status = 'ACTIVE' AND version = ?",
                businessId, id, expectedVersion) == 1;
    }

    // ---- revisions ----

    public List<EstimateRevisionRecord> lockRevisions(UUID businessId, UUID estimateId) {
        return attachLines(businessId, jdbc.query("""
                SELECT * FROM estimate_versions WHERE business_id = ? AND estimate_id = ?
                ORDER BY revision ASC, id ASC FOR UPDATE
                """, REVISION, businessId, estimateId));
    }

    public List<EstimateRevisionRecord> findRevisions(UUID businessId, UUID estimateId) {
        return attachLines(businessId, jdbc.query(
                "SELECT * FROM estimate_versions WHERE business_id = ? AND estimate_id = ? ORDER BY revision DESC, id ASC",
                REVISION, businessId, estimateId));
    }

    public Optional<EstimateRevisionRecord> findRevision(UUID businessId, UUID estimateId, UUID id) {
        return attachLines(businessId, jdbc.query(
                "SELECT * FROM estimate_versions WHERE business_id = ? AND estimate_id = ? AND id = ?",
                REVISION, businessId, estimateId, id)).stream().findFirst();
    }

    /** Revision headers (without lines) for list summaries, grouped by estimate, newest revision first. */
    public Map<UUID, List<EstimateRevisionRecord>> findRevisionHeaders(UUID businessId, Collection<UUID> estimateIds) {
        Map<UUID, List<EstimateRevisionRecord>> grouped = new LinkedHashMap<>();
        if (estimateIds.isEmpty()) return grouped;
        List<Object> args = new ArrayList<>();
        args.add(businessId);
        args.addAll(estimateIds);
        String in = String.join(",", Collections.nCopies(estimateIds.size(), "?"));
        for (EstimateRevisionRecord revision : jdbc.query("SELECT * FROM estimate_versions WHERE business_id = ? AND estimate_id IN ("
                + in + ") ORDER BY revision DESC, id ASC", REVISION, args.toArray())) {
            grouped.computeIfAbsent(revision.estimateId(), ignored -> new ArrayList<>()).add(revision);
        }
        return grouped;
    }

    public void insertDraftRevision(EstimateRevisionRecord revision) {
        jdbc.update("""
                INSERT INTO estimate_versions (id, business_id, estimate_id, revision, status, pricing_mode, pricing_value,
                    saleable_quantity, total_planned_cost, planned_batch_price, planned_contribution, planned_margin, planned_markup,
                    planned_per_unit_price, planned_margin_unavailable_reason, planned_markup_unavailable_reason,
                    planned_per_unit_price_unavailable_reason, calculation_version, version, created_by_user_id, updated_by_user_id,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)
                """, revision.id(), revision.businessId(), revision.estimateId(), revision.revision(), revision.pricingMode().name(),
                revision.pricingValue(), revision.saleableQuantity(), revision.totalPlannedCost(), revision.plannedBatchPrice(),
                revision.plannedContribution(), revision.plannedMargin(), revision.plannedMarkup(), revision.plannedPerUnitPrice(),
                revision.plannedMargin() == null ? "ZERO_PRICE" : null, revision.plannedMarkup() == null ? "ZERO_COST" : null,
                perUnitReason(revision), PricingCalculator.CALCULATION_VERSION, revision.createdByUserId(), revision.updatedByUserId(),
                revision.createdAt(), revision.updatedAt());
        insertLines(revision.businessId(), revision.estimateId(), revision.id(), revision.lines());
    }

    public boolean replaceDraftRevision(UUID businessId, UUID estimateId, UUID id, long expectedVersion,
                                        PricingCalculator.Result result, UUID actor, OffsetDateTime now) {
        int updated = jdbc.update("""
                UPDATE estimate_versions SET pricing_mode = ?, pricing_value = ?, saleable_quantity = ?, total_planned_cost = ?,
                    planned_batch_price = ?, planned_contribution = ?, planned_margin = ?, planned_markup = ?, planned_per_unit_price = ?,
                    planned_margin_unavailable_reason = ?, planned_markup_unavailable_reason = ?,
                    planned_per_unit_price_unavailable_reason = ?, version = version + 1, updated_by_user_id = ?, updated_at = ?
                WHERE business_id = ? AND estimate_id = ? AND id = ? AND status = 'DRAFT' AND version = ?
                """, result.pricingMode().name(), result.pricingValue(), result.saleableQuantity(), result.costs().total(),
                result.price(), result.contribution(), result.margin(), result.markup(), result.perUnit(), result.marginUnavailable(),
                result.markupUnavailable(), result.perUnitUnavailable(), actor, now, businessId, estimateId, id, expectedVersion);
        if (updated != 1) return false;
        jdbc.update("DELETE FROM estimate_lines WHERE business_id = ? AND estimate_id = ? AND estimate_version_id = ?",
                businessId, estimateId, id);
        insertLines(businessId, estimateId, id, result.costs().rawLines());
        return true;
    }

    public boolean deleteDraftRevision(UUID businessId, UUID estimateId, UUID id, long expectedVersion) {
        return jdbc.update("DELETE FROM estimate_versions WHERE business_id = ? AND estimate_id = ? AND id = ? AND status = 'DRAFT' AND version = ?",
                businessId, estimateId, id, expectedVersion) == 1;
    }

    public boolean supersedeApproved(UUID businessId, UUID estimateId, UUID approvedId, UUID supersededBy, UUID actor, OffsetDateTime now) {
        return jdbc.update("""
                UPDATE estimate_versions SET status = 'SUPERSEDED', superseded_by_version_id = ?, superseded_at = ?,
                    updated_by_user_id = ?, updated_at = ?, version = version + 1
                WHERE business_id = ? AND estimate_id = ? AND id = ? AND status = 'APPROVED'
                """, supersededBy, now, actor, now, businessId, estimateId, approvedId) == 1;
    }

    public boolean approveDraft(UUID businessId, UUID estimateId, UUID id, long expectedVersion, UUID actor, OffsetDateTime now) {
        return jdbc.update("""
                UPDATE estimate_versions SET status = 'APPROVED', approved_by_user_id = ?, approved_at = ?,
                    updated_by_user_id = ?, updated_at = ?, version = version + 1
                WHERE business_id = ? AND estimate_id = ? AND id = ? AND status = 'DRAFT' AND version = ?
                """, actor, now, actor, now, businessId, estimateId, id, expectedVersion) == 1;
    }

    // ---- conversion lineage ----

    public void insertConversion(EstimateConversionRecord conversion) {
        jdbc.update("""
                INSERT INTO estimate_project_conversions (id, business_id, estimate_id, estimate_version_id, project_id,
                    project_budget_version_id, conversion_contract_version, source_calculation_version, target_calculation_version,
                    converted_by_user_id, converted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, conversion.id(), conversion.businessId(), conversion.estimateId(), conversion.estimateVersionId(),
                conversion.projectId(), conversion.projectBudgetVersionId(), conversion.conversionContractVersion(),
                conversion.sourceCalculationVersion(), conversion.targetCalculationVersion(), conversion.convertedByUserId(),
                conversion.convertedAt());
    }

    public Optional<EstimateConversionRecord> findConversionByRevision(UUID businessId, UUID estimateVersionId) {
        return jdbc.query("SELECT * FROM estimate_project_conversions WHERE business_id = ? AND estimate_version_id = ?",
                CONVERSION, businessId, estimateVersionId).stream().findFirst();
    }

    public List<EstimateConversionRecord> findConversions(UUID businessId, UUID estimateId) {
        return jdbc.query("SELECT * FROM estimate_project_conversions WHERE business_id = ? AND estimate_id = ? ORDER BY converted_at DESC, id",
                CONVERSION, businessId, estimateId);
    }

    public Map<UUID, Integer> countConversions(UUID businessId, Collection<UUID> estimateIds) {
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        if (estimateIds.isEmpty()) return counts;
        List<Object> args = new ArrayList<>();
        args.add(businessId);
        args.addAll(estimateIds);
        String in = String.join(",", Collections.nCopies(estimateIds.size(), "?"));
        jdbc.query("SELECT estimate_id, count(*) AS n FROM estimate_project_conversions WHERE business_id = ? AND estimate_id IN ("
                + in + ") GROUP BY estimate_id", rs -> {
            counts.put(rs.getObject("estimate_id", UUID.class), rs.getInt("n"));
        }, args.toArray());
        return counts;
    }

    private void insertLines(UUID businessId, UUID estimateId, UUID revisionId, List<RawLine> lines) {
        for (int index = 0; index < lines.size(); index++) {
            RawLine line = lines.get(index);
            jdbc.update("""
                    INSERT INTO estimate_lines (id, business_id, estimate_id, estimate_version_id, line_number, cost_group,
                        description, quantity, unit, unit_cost, planned_cost)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), businessId, estimateId, revisionId, index + 1, line.costGroup().name(),
                    line.description(), line.quantity(), line.unit(), line.unitCost(), line.plannedCost());
        }
    }

    private List<EstimateRevisionRecord> attachLines(UUID businessId, List<EstimateRevisionRecord> revisions) {
        if (revisions.isEmpty()) return List.of();
        List<Object> args = new ArrayList<>();
        args.add(businessId);
        revisions.forEach(revision -> args.add(revision.id()));
        String in = String.join(",", Collections.nCopies(revisions.size(), "?"));
        Map<UUID, List<RawLine>> byRevision = new LinkedHashMap<>();
        jdbc.query("SELECT * FROM estimate_lines WHERE business_id = ? AND estimate_version_id IN (" + in
                + ") ORDER BY estimate_version_id, line_number", rs -> {
            byRevision.computeIfAbsent(rs.getObject("estimate_version_id", UUID.class), ignored -> new ArrayList<>()).add(new RawLine(
                    CostGroup.valueOf(rs.getString("cost_group")), rs.getString("description"), rs.getBigDecimal("quantity"),
                    rs.getString("unit"), rs.getBigDecimal("unit_cost"), rs.getBigDecimal("planned_cost")));
        }, args.toArray());
        return revisions.stream().map(revision -> revision.withLines(byRevision.getOrDefault(revision.id(), List.of()))).toList();
    }

    private static String perUnitReason(EstimateRevisionRecord revision) {
        if (revision.saleableQuantity() == null) return "SALEABLE_QUANTITY_UNKNOWN";
        return revision.saleableQuantity().signum() == 0 ? "ZERO_SALEABLE_QUANTITY" : null;
    }
}
