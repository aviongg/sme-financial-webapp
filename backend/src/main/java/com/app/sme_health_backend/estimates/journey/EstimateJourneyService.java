package com.app.sme_health_backend.estimates.journey;

import com.app.sme_health_backend.commercial.dto.CommercialDocumentDraftRequest;
import com.app.sme_health_backend.commercial.dto.CommercialDocumentResponse;
import com.app.sme_health_backend.commercial.dto.CommercialLineRequest;
import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.service.CommercialDocumentService;
import com.app.sme_health_backend.estimates.dto.EstimateConversionRequest;
import com.app.sme_health_backend.estimates.service.EstimateConversionService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.productsupport.DomainException;
import com.app.sme_health_backend.productsupport.Money;
import com.app.sme_health_backend.projects.dto.ProjectCreateRequest;
import com.app.sme_health_backend.projects.dto.ProjectResponse;
import com.app.sme_health_backend.projects.model.ProjectKind;
import com.app.sme_health_backend.projects.profitability.ProjectProfitabilityService;
import com.app.sme_health_backend.projects.service.ProjectPlanningService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * F06 product journeys over the existing calculator, revisions and exact-fit conversion (nothing is recalculated
 * or rounded here): approved estimate -> NEW project (atomic create + conversion), approved estimate -> DRAFT
 * customer invoice (quotation, non-posting), and estimate-vs-actual feedback from F05 profitability.
 */
@Service
public class EstimateJourneyService {
    private final JdbcTemplate jdbc;
    private final ProjectPlanningService projects;
    private final EstimateConversionService conversions;
    private final CommercialDocumentService documents;
    private final ProjectProfitabilityService profitability;

    public EstimateJourneyService(JdbcTemplate jdbc, ProjectPlanningService projects, EstimateConversionService conversions,
                                  CommercialDocumentService documents, ProjectProfitabilityService profitability) {
        this.jdbc = jdbc;
        this.projects = projects;
        this.conversions = conversions;
        this.documents = documents;
        this.profitability = profitability;
    }

    /** Creates the project and converts into it in ONE transaction: a non-representable estimate leaves no project. */
    @Transactional
    public Map<String, Object> convertToNewProject(BusinessAccessContext ctx, UUID estimateId, UUID versionId,
                                                   String code, String name, String kind) {
        approvedRevision(ctx.businessId(), estimateId, versionId);
        ProjectKind projectKind;
        try {
            projectKind = ProjectKind.valueOf(kind == null ? "PROJECT" : kind);
        } catch (IllegalArgumentException e) {
            throw DomainException.invalid("kind", "enum_invalid");
        }
        ProjectResponse project = projects.createProject(ctx, new ProjectCreateRequest(code, name, projectKind,
                "Created from approved estimate", null, null));
        EstimateConversionService.Outcome outcome = conversions.convert(ctx, estimateId, versionId,
                new EstimateConversionRequest(project.id(), project.version()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", project.id());
        body.put("projectCode", project.code());
        body.put("conversion", outcome.body());
        return body;
    }

    /** Seeds a DRAFT customer invoice from an approved revision; amounts must be exactly representable (no rounding). */
    @Transactional
    public Map<String, Object> draftInvoice(BusinessAccessContext ctx, UUID estimateId, UUID versionId, String customerName) {
        Map<String, Object> revision = approvedRevision(ctx.businessId(), estimateId, versionId);
        BigDecimal price = (BigDecimal) revision.get("planned_batch_price");
        BigDecimal quantity = (BigDecimal) revision.get("saleable_quantity");
        BigDecimal perUnit = (BigDecimal) revision.get("planned_per_unit_price");
        String code = (String) revision.get("code");
        String description = ("Estimate " + code + " R" + revision.get("revision") + " - " + revision.get("name"));
        if (description.length() > 200) description = description.substring(0, 200);
        List<CommercialLineRequest> lines;
        if (quantity != null && perUnit != null && exact(perUnit, 2) && exact(quantity, 6)
                && quantity.multiply(perUnit).compareTo(price) == 0) {
            lines = List.of(new CommercialLineRequest(description, plain(quantity), null, perUnit.setScale(2, RoundingMode.UNNECESSARY).toPlainString()));
        } else if (exact(price, 2)) {
            lines = List.of(new CommercialLineRequest(description, "1", null, price.setScale(2, RoundingMode.UNNECESSARY).toPlainString()));
        } else {
            throw DomainException.conflict("price_not_representable",
                    "The approved price has more than 2 decimals; F04 invoices never round. Adjust the estimate pricing.");
        }
        String customer = customerName == null || customerName.isBlank() ? null : customerName.trim();
        CommercialDocumentResponse draft = documents.createDraft(ctx, CommercialDocumentKind.INVOICE,
                new CommercialDocumentDraftRequest(customer, LocalDate.now(ZoneId.of("Asia/Karachi")).toString(), null,
                        "Quotation from estimate " + code + " revision " + revision.get("revision"), lines, null));
        jdbc.update("INSERT INTO estimate_commercial_links(business_id, estimate_id, estimate_version_id, document_id, created_by) "
                + "VALUES (?, ?, ?, ?, ?)", ctx.businessId(), estimateId, versionId, draft.id(), ctx.userId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("documentId", draft.id());
        body.put("status", draft.status());
        body.put("total", draft.total());
        return body;
    }

    /** Estimate vs actual: every conversion's project result plus quotation lineage. */
    @Transactional(readOnly = true)
    public Map<String, Object> feedback(BusinessAccessContext ctx, UUID estimateId) {
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM estimates WHERE business_id = ? AND id = ?", Integer.class,
                ctx.businessId(), estimateId);
        if (exists == null || exists == 0) throw DomainException.notFound("Estimate");
        List<Map<String, Object>> projectsOut = new ArrayList<>();
        for (Map<String, Object> c : jdbc.queryForList("SELECT c.project_id, v.revision FROM estimate_project_conversions c "
                + "JOIN estimate_versions v ON v.business_id = c.business_id AND v.id = c.estimate_version_id "
                + "WHERE c.business_id = ? AND c.estimate_id = ? ORDER BY c.converted_at", ctx.businessId(), estimateId)) {
            Map<String, Object> p = profitability.profitability(ctx.businessId(), (UUID) c.get("project_id"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("projectId", c.get("project_id"));
            m.put("projectCode", p.get("code"));
            m.put("revision", c.get("revision"));
            m.put("estimate", p.get("estimate"));
            m.put("revenue", p.get("revenue"));
            m.put("costs", p.get("costs"));
            m.put("profitability", p.get("profitability"));
            projectsOut.add(m);
        }
        List<Map<String, Object>> quotes = jdbc.queryForList("""
                SELECT l.document_id, d.status, d.document_number, d.total, v.revision
                FROM estimate_commercial_links l
                JOIN commercial_documents d ON d.business_id = l.business_id AND d.id = l.document_id
                JOIN estimate_versions v ON v.business_id = l.business_id AND v.id = l.estimate_version_id
                WHERE l.business_id = ? AND l.estimate_id = ? ORDER BY l.created_at
                """, ctx.businessId(), estimateId).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("documentId", r.get("document_id"));
            m.put("status", r.get("status"));
            m.put("documentNumber", r.get("document_number"));
            m.put("total", r.get("total") == null ? null : Money.str((BigDecimal) r.get("total")));
            m.put("revision", r.get("revision"));
            return m;
        }).toList();
        return Map.of("estimateId", estimateId, "projects", projectsOut, "quotations", quotes);
    }

    private Map<String, Object> approvedRevision(UUID businessId, UUID estimateId, UUID versionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT v.revision, v.status, v.planned_batch_price, v.saleable_quantity, v.planned_per_unit_price,
                       e.code, e.name, e.status AS estimate_status
                FROM estimate_versions v JOIN estimates e ON e.business_id = v.business_id AND e.id = v.estimate_id
                WHERE v.business_id = ? AND v.estimate_id = ? AND v.id = ?
                """, businessId, estimateId, versionId);
        if (rows.isEmpty()) throw DomainException.notFound("Estimate revision");
        if (!"APPROVED".equals(rows.get(0).get("status"))) {
            throw DomainException.conflict("revision_not_approved", "Only the approved estimate revision can be used.");
        }
        if (!"ACTIVE".equals(rows.get(0).get("estimate_status"))) {
            throw DomainException.conflict("estimate_archived", "Archived estimates are read-only.");
        }
        return rows.get(0);
    }

    private static boolean exact(BigDecimal value, int scale) {
        return value.stripTrailingZeros().scale() <= scale;
    }

    private static String plain(BigDecimal value) {
        BigDecimal s = value.stripTrailingZeros();
        return (s.scale() < 0 ? s.setScale(0) : s).toPlainString();
    }
}
