package com.app.sme_health_backend.projects.profitability;

import com.app.sme_health_backend.inventory.ledger.InventoryReadService;
import com.app.sme_health_backend.productsupport.DomainException;
import com.app.sme_health_backend.productsupport.Money;
import com.app.sme_health_backend.projectattribution.dto.EligibleSourceItem;
import com.app.sme_health_backend.projectattribution.dto.ProjectCommitmentsSummaryResponse;
import com.app.sme_health_backend.projectattribution.service.ProjectCommitmentService;
import com.app.sme_health_backend.projectattribution.service.ProjectFinancialAttributionService;
import com.app.sme_health_backend.samples.actuals.SampleActualsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * F05 actual project/job/order profitability ({@value #VERSION}). It composes existing authorities and owns no
 * financial fact:
 * <ul>
 *   <li>Revenue: the project's share of RECOGNIZED linked customer invoices (C05 links x C04 obligations) net of
 *       credit notes pro rata, plus cash sales attributed through C05. Collections of recognized receivables are
 *       cash timing only and excluded.</li>
 *   <li>Material: F08 PROJECT consumption and project-tagged SALE issues.</li>
 *   <li>Labour/freight/other/overhead: C05 posted cash attributions of operating payments (overhead ONLY when
 *       explicitly attributed). Payments capitalized into inventory are excluded (their cost arrives through F08).</li>
 *   <li>Supplier bills: the project's share of recognized linked bills net of debit notes and of the share
 *       capitalized into stock. Settlement-sourced attributions are excluded (the bill already carries the cost).</li>
 *   <li>Commission: C08 recognized cost attributed through C05. FX: non-cash C09 evidence, shown separately.</li>
 *   <li>Samples linked to the project: F09 actual cost less recovery not already linked to the project.</li>
 * </ul>
 * Unknown inputs stay null/partial with explicit reasons; nothing missing is reported as zero.
 */
@Service
public class ProjectProfitabilityService {
    public static final String VERSION = "f05-profitability-v1";
    private static final Logger log = LoggerFactory.getLogger(ProjectProfitabilityService.class);
    private static final Set<String> COST_CATEGORIES = Set.of("MATERIAL", "LABOUR", "FREIGHT", "OTHER", "OVERHEAD", "COMMISSION");

    private final JdbcTemplate jdbc;
    private final InventoryReadService inventory;
    private final ProjectFinancialAttributionService attributions;
    private final ProjectCommitmentService commitments;
    private final SampleActualsService samples;

    public ProjectProfitabilityService(JdbcTemplate jdbc, InventoryReadService inventory,
                                       ProjectFinancialAttributionService attributions,
                                       ProjectCommitmentService commitments, SampleActualsService samples) {
        this.jdbc = jdbc;
        this.inventory = inventory;
        this.attributions = attributions;
        this.commitments = commitments;
        this.samples = samples;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> profitability(UUID businessId, UUID projectId) {
        List<Map<String, Object>> projectRows = jdbc.queryForList("SELECT id, code, name, kind, status, start_date, "
                + "target_end_date FROM projects WHERE business_id = ? AND id = ?", businessId, projectId);
        if (projectRows.isEmpty()) {
            throw DomainException.notFound("Project");
        }
        Map<String, Object> project = projectRows.get(0);
        List<String> notes = new ArrayList<>();
        List<Map<String, Object>> evidence = new ArrayList<>();

        // ---------------------------------------------------------------- revenue
        BigDecimal invoiceRevenue = Money.ZERO;
        BigDecimal pendingRecognition = Money.ZERO;
        Map<String, BigDecimal> costs = new LinkedHashMap<>();
        for (String c : COST_CATEGORIES) costs.put(c, Money.ZERO);
        BigDecimal capitalizedBillShare = Money.ZERO;
        for (Map<String, Object> link : jdbc.queryForList("""
                SELECT l.document_id, l.document_kind, l.allocated_amount, d.total, d.status AS doc_status, d.document_number,
                       o.id AS obligation_id, o.status AS obligation_status
                FROM project_commercial_document_links l
                JOIN commercial_documents d ON d.business_id = l.business_id AND d.id = l.document_id
                LEFT JOIN financial_obligations o ON o.business_id = l.business_id AND o.source_id = l.document_id::text
                     AND o.source_type = CASE l.document_kind WHEN 'INVOICE' THEN 'CUSTOMER_INVOICE' ELSE 'SUPPLIER_BILL' END
                     AND o.status = 'OPEN'
                WHERE l.business_id = ? AND l.project_id = ? AND l.link_status = 'ACTIVE'
                """, businessId, projectId)) {
            UUID documentId = (UUID) link.get("document_id");
            BigDecimal allocated = Money.nz((BigDecimal) link.get("allocated_amount"));
            BigDecimal total = Money.nz((BigDecimal) link.get("total"));
            boolean recognized = link.get("obligation_id") != null;
            boolean invoice = "INVOICE".equals(link.get("document_kind"));
            if ("VOID".equals(link.get("doc_status"))) {
                notes.add("LINKED_DOCUMENT_VOID:" + link.get("document_number"));
                continue;
            }
            if (!recognized) {
                if (invoice) pendingRecognition = pendingRecognition.add(allocated);
                evidence.add(evidenceItem("COMMERCIAL_DOCUMENT", documentId, (String) link.get("document_number"),
                        allocated, "PENDING_RECOGNITION"));
                continue;
            }
            BigDecimal adjustments = Money.nz(jdbc.queryForObject("SELECT COALESCE(sum(amount), 0) FROM commercial_adjustments "
                    + "WHERE business_id = ? AND document_id = ? AND status = 'EFFECTIVE'", BigDecimal.class, businessId, documentId));
            BigDecimal share = proportional(allocated, total.subtract(adjustments), total);
            if (invoice) {
                invoiceRevenue = invoiceRevenue.add(share);
                evidence.add(evidenceItem("COMMERCIAL_DOCUMENT", documentId, (String) link.get("document_number"), share, "RECOGNIZED_REVENUE"));
            } else {
                BigDecimal capitalized = inventory.capitalizedFromBill(businessId, documentId);
                BigDecimal capShare = proportional(allocated, capitalized, total);
                capitalizedBillShare = capitalizedBillShare.add(capShare);
                BigDecimal cost = share.subtract(capShare).max(Money.ZERO);
                String category = billCategory(businessId, documentId);
                costs.merge(category, cost, BigDecimal::add);
                evidence.add(evidenceItem("COMMERCIAL_DOCUMENT", documentId, (String) link.get("document_number"), cost,
                        "RECOGNIZED_BILL_COST:" + category));
            }
        }

        Map<UUID, BigDecimal> capitalizedTx = inventory.capitalizedTransactions(businessId);
        BigDecimal cashSales = Money.ZERO;
        BigDecimal collectionsExcluded = Money.ZERO;
        BigDecimal settlementCostExcluded = Money.ZERO;
        BigDecimal capitalizedAttributionExcluded = Money.ZERO;
        BigDecimal fxImpact = Money.ZERO;
        for (Map<String, Object> a : jdbc.queryForList("""
                SELECT a.id, a.source_type, a.source_id, a.source_direction, a.cost_category, a.economic_classification,
                       a.base_amount, t.event_type
                FROM project_financial_attributions a
                LEFT JOIN financial_transactions t ON t.business_id = a.business_id AND t.id = a.source_id
                     AND a.source_type = 'TRANSACTION'
                WHERE a.business_id = ? AND a.project_id = ? AND a.status = 'ACTIVE'
                """, businessId, projectId)) {
            BigDecimal amount = Money.nz((BigDecimal) a.get("base_amount"));
            String classification = (String) a.get("economic_classification");
            String sourceType = (String) a.get("source_type");
            String eventType = (String) a.get("event_type");
            String category = (String) a.get("cost_category");
            UUID sourceId = (UUID) a.get("source_id");
            if ("NONCASH_FINANCIAL_EVIDENCE".equals(classification)) {
                fxImpact = fxImpact.add(amount);
                continue;
            }
            if ("RECOGNIZED_ECONOMIC_COST".equals(classification)) {
                costs.merge("COMMISSION", amount, BigDecimal::add);
                evidence.add(evidenceItem("C05_ATTRIBUTION", (UUID) a.get("id"), "commission", amount, "RECOGNIZED_COMMISSION"));
                continue;
            }
            if ("INFLOW".equals(a.get("source_direction"))) {
                if ("DIRECT_SALE_RECEIPT".equals(eventType)) {
                    cashSales = cashSales.add(amount);
                    evidence.add(evidenceItem("C05_ATTRIBUTION", (UUID) a.get("id"), "cash sale", amount, "CASH_SALE_REVENUE"));
                } else {
                    collectionsExcluded = collectionsExcluded.add(amount);
                }
                continue;
            }
            if (!"TRANSACTION".equals(sourceType) || !"OPERATING_EXPENSE_PAYMENT".equals(eventType)) {
                settlementCostExcluded = settlementCostExcluded.add(amount);
                continue;
            }
            if (capitalizedTx.containsKey(sourceId)) {
                capitalizedAttributionExcluded = capitalizedAttributionExcluded.add(amount);
                continue;
            }
            String key = category == null || "REVENUE".equals(category) ? "OTHER" : category;
            costs.merge(key, amount, BigDecimal::add);
            evidence.add(evidenceItem("C05_ATTRIBUTION", (UUID) a.get("id"), key.toLowerCase(), amount, "POSTED_DIRECT_COST:" + key));
        }
        if (settlementCostExcluded.signum() > 0) notes.add("SETTLEMENT_ATTRIBUTIONS_EXCLUDED_COST_CARRIED_BY_BILLS");
        if (capitalizedAttributionExcluded.signum() > 0) notes.add("CAPITALIZED_PAYMENT_ATTRIBUTIONS_EXCLUDED_COST_VIA_INVENTORY");
        if (collectionsExcluded.signum() > 0) notes.add("COLLECTIONS_EXCLUDED_REVENUE_CARRIED_BY_INVOICES");

        BigDecimal inventoryMaterial = inventory.projectMaterialCost(businessId, projectId);
        costs.merge("MATERIAL", inventoryMaterial, BigDecimal::add);
        if (inventoryMaterial.signum() != 0) {
            evidence.add(evidenceItem("F08_INVENTORY", projectId, "inventory consumption", inventoryMaterial, "INVENTORY_MATERIAL"));
        }

        // Samples executed for this project (F09): cost less recovery not already linked to this project.
        BigDecimal sampleNetCost = Money.ZERO;
        for (Map<String, Object> s : jdbc.queryForList("SELECT id, code FROM sample_activities WHERE business_id = ? AND project_id = ?",
                businessId, projectId)) {
            UUID sampleId = (UUID) s.get("id");
            SampleActualsService.Totals t = samples.totals(businessId, sampleId, projectId);
            BigDecimal net = t.actualCost().subtract(t.recoveryNotLinkedToProject());
            sampleNetCost = sampleNetCost.add(net);
            evidence.add(evidenceItem("F09_SAMPLE", sampleId, (String) s.get("code"), net, "SAMPLE_NET_COST"));
        }

        BigDecimal revenue = invoiceRevenue.add(cashSales);
        BigDecimal directCosts = costs.get("MATERIAL").add(costs.get("LABOUR")).add(costs.get("FREIGHT"))
                .add(costs.get("OTHER")).add(costs.get("COMMISSION")).add(sampleNetCost);
        BigDecimal overhead = costs.get("OVERHEAD");
        BigDecimal totalCost = directCosts.add(overhead);
        BigDecimal contribution = revenue.subtract(directCosts);
        BigDecimal profit = revenue.subtract(totalCost);
        BigDecimal netAfterFx = profit.add(fxImpact);

        // ---------------------------------------------------------------- completeness
        BigDecimal unattributed = null;
        String discoveryStatus = "OK";
        try {
            BigDecimal sum = Money.ZERO;
            for (EligibleSourceItem e : attributions.getEligibleSources(businessId, projectId)) {
                if (e.direction() != null && "OUTFLOW".equals(e.direction().name())) {
                    sum = sum.add(Money.nz(e.remainingEligibleAmount()));
                }
            }
            unattributed = sum;
        } catch (RuntimeException ex) {
            // Never swallow silently: completeness becomes UNKNOWN and the reason is reported.
            log.warn("Project source discovery failed for project {}: {}", projectId, ex.getClass().getSimpleName());
            discoveryStatus = "FAILED";
        }
        String revenueStatus = revenue.signum() == 0 && pendingRecognition.signum() == 0 ? "NONE"
                : pendingRecognition.signum() > 0 ? "PARTIAL_PENDING_RECOGNITION" : "COMPLETE";
        String costStatus = "FAILED".equals(discoveryStatus) ? "UNKNOWN_SOURCE_DISCOVERY_FAILED"
                : totalCost.signum() == 0 ? "NONE" : "RECORDED";
        String profitabilityStatus;
        if (revenue.signum() == 0) profitabilityStatus = "UNAVAILABLE_NO_RECOGNIZED_REVENUE";
        else if (!"COMPLETE".equals(revenueStatus) || "UNKNOWN_SOURCE_DISCOVERY_FAILED".equals(costStatus)) profitabilityStatus = "PARTIAL";
        else profitabilityStatus = "AVAILABLE";

        // ---------------------------------------------------------------- plan / estimate comparison
        Map<String, Object> plan = plan(businessId, projectId, revenue, totalCost, costs, sampleNetCost);
        Map<String, Object> estimate = estimate(businessId, projectId, revenue, totalCost);
        Map<String, Object> commitmentSummary = null;
        try {
            ProjectCommitmentsSummaryResponse c = commitments.getCommitmentsSummary(businessId, projectId);
            commitmentSummary = new LinkedHashMap<>();
            commitmentSummary.put("totalOutstandingCommitments", Money.str(c.totalOutstandingCommitments()));
            commitmentSummary.put("authoritativeFinancialOutstanding", Money.str(c.authoritativeFinancialOutstanding()));
            commitmentSummary.put("commercialUnpostedExposure", Money.str(c.commercialUnpostedExposure()));
            commitmentSummary.put("outstandingSupplierPayables", Money.str(c.outstandingSupplierPayables()));
            commitmentSummary.put("outstandingCustomerReceivables", Money.str(c.outstandingCustomerReceivables()));
        } catch (RuntimeException ex) {
            log.warn("Commitment summary unavailable for project {}: {}", projectId, ex.getClass().getSimpleName());
            notes.add("COMMITMENTS_UNAVAILABLE");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", projectId);
        body.put("code", project.get("code"));
        body.put("name", project.get("name"));
        body.put("kind", project.get("kind"));
        body.put("status", project.get("status"));
        body.put("version", VERSION);
        Map<String, Object> rev = new LinkedHashMap<>();
        rev.put("recognizedInvoiceRevenue", Money.str(invoiceRevenue));
        rev.put("attributedCashSales", Money.str(cashSales));
        rev.put("total", Money.str(revenue));
        rev.put("pendingRecognition", Money.str(pendingRecognition));
        rev.put("status", revenueStatus);
        body.put("revenue", rev);
        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("material", Money.str(costs.get("MATERIAL")));
        cost.put("inventoryMaterial", Money.str(inventoryMaterial));
        cost.put("labour", Money.str(costs.get("LABOUR")));
        cost.put("freight", Money.str(costs.get("FREIGHT")));
        cost.put("otherDirect", Money.str(costs.get("OTHER")));
        cost.put("commission", Money.str(costs.get("COMMISSION")));
        cost.put("sampleNetCost", Money.str(sampleNetCost));
        cost.put("attributedOverhead", Money.str(overhead));
        cost.put("directCosts", Money.str(directCosts));
        cost.put("total", Money.str(totalCost));
        cost.put("status", costStatus);
        cost.put("unattributedEligibleOutflows", unattributed == null ? null : Money.str(unattributed));
        cost.put("excluded", Map.of(
                "settlementAttributions", Money.str(settlementCostExcluded),
                "capitalizedPaymentAttributions", Money.str(capitalizedAttributionExcluded),
                "capitalizedBillShare", Money.str(capitalizedBillShare),
                "collectionAttributions", Money.str(collectionsExcluded)));
        body.put("costs", cost);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contribution", Money.str(contribution));
        result.put("contributionMarginPct", pct(contribution, revenue));
        result.put("profit", Money.str(profit));
        result.put("marginPct", pct(profit, revenue));
        result.put("fxImpact", Money.str(fxImpact));
        result.put("profitAfterFx", Money.str(netAfterFx));
        result.put("status", profitabilityStatus);
        body.put("profitability", result);
        body.put("plan", plan);
        body.put("estimate", estimate);
        body.put("commitments", commitmentSummary);
        body.put("sourceDiscovery", discoveryStatus);
        body.put("notes", notes);
        body.put("evidence", evidence.size() > 200 ? evidence.subList(0, 200) : evidence);
        body.put("overheadPolicy", "EXPLICIT_ATTRIBUTION_ONLY");
        return body;
    }

    private Map<String, Object> plan(UUID businessId, UUID projectId, BigDecimal revenue, BigDecimal totalCost,
                                     Map<String, BigDecimal> costs, BigDecimal sampleNetCost) {
        List<Map<String, Object>> budgets = jdbc.queryForList("SELECT id, revision, planned_revenue, planned_cost, planned_margin "
                + "FROM project_budget_versions WHERE business_id = ? AND project_id = ? AND status = 'APPROVED' "
                + "ORDER BY revision DESC LIMIT 1", businessId, projectId);
        Map<String, Object> body = new LinkedHashMap<>();
        if (budgets.isEmpty()) {
            body.put("status", "NO_APPROVED_BUDGET");
            body.put("plannedRevenue", null);
            body.put("plannedCost", null);
            return body;
        }
        Map<String, Object> b = budgets.get(0);
        BigDecimal plannedRevenue = (BigDecimal) b.get("planned_revenue");
        BigDecimal plannedCost = Money.nz((BigDecimal) b.get("planned_cost"));
        body.put("status", "APPROVED_BUDGET");
        body.put("revision", b.get("revision"));
        body.put("plannedRevenue", plannedRevenue == null ? null : Money.str(plannedRevenue));
        body.put("plannedCost", Money.str(plannedCost));
        body.put("plannedMargin", b.get("planned_margin") == null ? null : Money.str((BigDecimal) b.get("planned_margin")));
        body.put("revenueVariance", plannedRevenue == null ? null : Money.str(revenue.subtract(plannedRevenue)));
        body.put("costVariance", Money.str(totalCost.subtract(plannedCost)));
        body.put("costUtilizationPct", pct(totalCost, plannedCost));
        Map<String, BigDecimal> planned = new LinkedHashMap<>();
        for (Map<String, Object> line : jdbc.queryForList("SELECT category, planned_line_cost FROM project_budget_lines "
                + "WHERE business_id = ? AND budget_version_id = ?", businessId, b.get("id"))) {
            planned.merge((String) line.get("category"), Money.nz((BigDecimal) line.get("planned_line_cost")), BigDecimal::add);
        }
        List<Map<String, Object>> categories = new ArrayList<>();
        for (String c : List.of("MATERIAL", "LABOUR", "OVERHEAD", "FREIGHT", "OTHER")) {
            BigDecimal actual = costs.get(c);
            if ("OTHER".equals(c)) actual = actual.add(costs.get("COMMISSION")).add(sampleNetCost);
            BigDecimal p = planned.getOrDefault(c, Money.ZERO);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", c);
            m.put("planned", Money.str(p));
            m.put("actual", Money.str(actual));
            m.put("variance", Money.str(actual.subtract(p)));
            categories.add(m);
        }
        body.put("categories", categories);
        return body;
    }

    private Map<String, Object> estimate(UUID businessId, UUID projectId, BigDecimal revenue, BigDecimal totalCost) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.estimate_id, v.revision, v.total_planned_cost, v.planned_batch_price, e.code, e.name
                FROM estimate_project_conversions c
                JOIN estimate_versions v ON v.business_id = c.business_id AND v.id = c.estimate_version_id
                JOIN estimates e ON e.business_id = c.business_id AND e.id = c.estimate_id
                WHERE c.business_id = ? AND c.project_id = ? ORDER BY c.converted_at DESC LIMIT 1
                """, businessId, projectId);
        Map<String, Object> body = new LinkedHashMap<>();
        if (rows.isEmpty()) {
            body.put("status", "NO_CONVERTED_ESTIMATE");
            return body;
        }
        Map<String, Object> r = rows.get(0);
        BigDecimal estCost = ((BigDecimal) r.get("total_planned_cost")).setScale(4, RoundingMode.HALF_UP);
        BigDecimal estPrice = ((BigDecimal) r.get("planned_batch_price")).setScale(4, RoundingMode.HALF_UP);
        body.put("status", "CONVERTED_ESTIMATE");
        body.put("estimateId", r.get("estimate_id"));
        body.put("code", r.get("code"));
        body.put("name", r.get("name"));
        body.put("revision", r.get("revision"));
        body.put("estimatedCost", Money.str(estCost));
        body.put("estimatedPrice", Money.str(estPrice));
        body.put("costVariance", Money.str(totalCost.subtract(estCost)));
        body.put("priceVariance", Money.str(revenue.subtract(estPrice)));
        body.put("costAccuracyPct", pct(totalCost, estCost));
        return body;
    }

    private String billCategory(UUID businessId, UUID documentId) {
        List<String> rows = jdbc.queryForList("SELECT category FROM financial_classifications WHERE business_id = ? "
                + "AND source_kind = 'COMMERCIAL_DOCUMENT' AND source_id = ?", String.class, businessId, documentId);
        if (rows.isEmpty()) return "OTHER";
        return switch (rows.get(0)) {
            case "DIRECT_MATERIALS" -> "MATERIAL";
            case "DIRECT_LABOUR", "SALARIES_WAGES" -> "LABOUR";
            case "TRANSPORT_LOGISTICS" -> "FREIGHT";
            default -> "OTHER";
        };
    }

    /** allocated * numerator / total at scale 4 HALF_UP; exact when allocated == total. */
    static BigDecimal proportional(BigDecimal allocated, BigDecimal numerator, BigDecimal total) {
        if (total.signum() == 0) return Money.ZERO;
        if (allocated.compareTo(total) == 0) return numerator.setScale(4, RoundingMode.HALF_UP);
        return allocated.multiply(numerator).divide(total, 4, RoundingMode.HALF_UP);
    }

    private static String pct(BigDecimal n, BigDecimal d) {
        BigDecimal p = Money.pct(n, d);
        return p == null ? null : p.toPlainString();
    }

    private static Map<String, Object> evidenceItem(String source, UUID id, String label, BigDecimal amount, String role) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", source);
        m.put("id", id);
        m.put("label", label);
        m.put("amount", Money.str(amount));
        m.put("role", role);
        return m;
    }
}
