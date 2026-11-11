package com.app.sme_health_backend.samples.actuals;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.inventory.ledger.InventoryReadService;
import com.app.sme_health_backend.productsupport.AuditRecorder;
import com.app.sme_health_backend.productsupport.DomainException;
import com.app.sme_health_backend.productsupport.Hashing;
import com.app.sme_health_backend.productsupport.Money;
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
 * F09 actual sample / production-activity finance ({@value #VERSION}).
 *
 * <p>Frozen policy:
 * <ul>
 *   <li>Actual cost = F08 SAMPLE consumption (inventory authority) + direct costs attributed from un-reversed ledger
 *       operating payments. A payment's attributed total across samples, C05 projects and inventory capitalization
 *       never exceeds the payment.</li>
 *   <li>Recognized recovery = linked share of RECOGNIZED customer invoices, net of credit notes pro rata.
 *       Collected/outstanding recovery is read from that invoice's single AR obligation.</li>
 *   <li>FREE samples cannot carry recovery. PAID expects recovery &gt;= actual cost; PARTIAL expects 0 &lt; recovery
 *       &lt; cost. Contribution = recognized recovery - actual cost.</li>
 *   <li>Every correction is compensating (reverse / unlink); nothing is deleted.</li>
 * </ul>
 */
@Service
public class SampleActualsService {
    public static final String VERSION = "f09-actuals-v1";
    private static final Set<String> CATEGORIES = Set.of("MATERIAL", "LABOUR", "FREIGHT", "OTHER");

    private final JdbcTemplate jdbc;
    private final InventoryReadService inventory;
    private final AuditRecorder audit;

    public SampleActualsService(JdbcTemplate jdbc, InventoryReadService inventory, AuditRecorder audit) {
        this.jdbc = jdbc;
        this.inventory = inventory;
        this.audit = audit;
    }

    public record Totals(BigDecimal actualCost, BigDecimal recognizedRecovery, BigDecimal recoveryNotLinkedToProject) { }

    // ---------------------------------------------------------------- writes

    @Transactional
    public Map<String, Object> addCost(BusinessAccessContext ctx, String key, UUID sampleId, UUID transactionId,
                                       BigDecimal amount, String category, String notes) {
        if (key == null || key.isBlank() || key.length() > 120) throw DomainException.invalid("Idempotency-Key", "required");
        if (transactionId == null) throw DomainException.invalid("transactionId", "required");
        if (category == null || !CATEGORIES.contains(category)) throw DomainException.invalid("costCategory", "enum_invalid");
        if (notes != null && notes.length() > 500) throw DomainException.invalid("notes", "too_long");
        String hash = Hashing.sha256(ctx.businessId(), sampleId, transactionId, amount.stripTrailingZeros().toPlainString(), category);
        List<Map<String, Object>> replay = jdbc.queryForList("SELECT id, payload_hash FROM sample_actual_costs "
                + "WHERE business_id = ? AND idempotency_key = ?", ctx.businessId(), key);
        if (!replay.isEmpty()) {
            if (!hash.equals(replay.get(0).get("payload_hash"))) {
                throw DomainException.conflict("idempotency_key_reused", "This Idempotency-Key was used for a different cost.");
            }
            return actuals(ctx.businessId(), sampleId);
        }
        requireOpenSample(ctx.businessId(), sampleId);
        // Serialize every attribution of this payment (transaction rows are append-only, so use an advisory lock).
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))", "ledger-tx:" + transactionId);
        List<Map<String, Object>> tx = jdbc.queryForList("""
                SELECT t.event_type, t.base_amount_pkr, t.effective_date, t.reversed_transaction_id,
                       EXISTS (SELECT 1 FROM financial_transactions r WHERE r.business_id = t.business_id
                               AND r.reversed_transaction_id = t.id) AS reversed
                FROM financial_transactions t WHERE t.business_id = ? AND t.id = ?
                """, ctx.businessId(), transactionId);
        if (tx.isEmpty()) throw DomainException.notFound("Ledger transaction");
        Map<String, Object> t = tx.get(0);
        if (!"OPERATING_EXPENSE_PAYMENT".equals(t.get("event_type")) || t.get("reversed_transaction_id") != null
                || Boolean.TRUE.equals(t.get("reversed"))) {
            throw DomainException.conflict("transaction_not_attributable", "Only an un-reversed operating payment can be attributed.");
        }
        BigDecimal used = Money.nz(jdbc.queryForObject("SELECT COALESCE(sum(amount), 0) FROM sample_actual_costs "
                + "WHERE business_id = ? AND transaction_id = ? AND status = 'EFFECTIVE'", BigDecimal.class, ctx.businessId(), transactionId))
                .add(Money.nz(jdbc.queryForObject("SELECT COALESCE(sum(base_amount), 0) FROM project_financial_attributions "
                        + "WHERE business_id = ? AND source_type = 'TRANSACTION' AND source_id = ? AND status = 'ACTIVE'",
                        BigDecimal.class, ctx.businessId(), transactionId)))
                .add(Money.nz(inventory.capitalizedTransactions(ctx.businessId()).get(transactionId)));
        BigDecimal capacity = Money.nz((BigDecimal) t.get("base_amount_pkr")).subtract(used);
        if (amount.compareTo(capacity) > 0) {
            throw new DomainException("attribution_exceeds_payment", 409,
                    "Only " + Money.str(capacity.max(Money.ZERO)) + " of this payment is still unattributed.",
                    Map.of("remaining", Money.str(capacity.max(Money.ZERO))));
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO sample_actual_costs(id, business_id, sample_id, transaction_id, amount, cost_category, cost_date,
                    notes, idempotency_key, payload_hash, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, ctx.businessId(), sampleId, transactionId, amount, category, t.get("effective_date"),
                notes == null || notes.isBlank() ? null : notes.trim(), key, hash, ctx.userId());
        audit.record(ctx, AuditEventType.SAMPLE_ACTUAL_RECORDED, "SAMPLE_ACTUAL_COST", id,
                Map.of("sampleId", sampleId.toString(), "transactionId", transactionId.toString(), "amount", Money.str(amount)));
        return actuals(ctx.businessId(), sampleId);
    }

    @Transactional
    public Map<String, Object> reverseCost(BusinessAccessContext ctx, UUID sampleId, UUID costId, String reason) {
        if (reason == null || reason.isBlank()) throw DomainException.invalid("reason", "required");
        int n = jdbc.update("""
                UPDATE sample_actual_costs SET status = 'REVERSED', reversed_by = ?, reversed_at = now(), reversal_reason = ?,
                    version = version + 1
                WHERE business_id = ? AND sample_id = ? AND id = ? AND status = 'EFFECTIVE'
                """, ctx.userId(), reason.trim(), ctx.businessId(), sampleId, costId);
        if (n != 1) {
            Integer exists = jdbc.queryForObject("SELECT count(*) FROM sample_actual_costs WHERE business_id = ? AND sample_id = ? AND id = ?",
                    Integer.class, ctx.businessId(), sampleId, costId);
            if (exists == null || exists == 0) throw DomainException.notFound("Sample cost");
            throw DomainException.conflict("already_reversed", "This cost has already been reversed.");
        }
        audit.record(ctx, AuditEventType.SAMPLE_ACTUAL_REVERSED, "SAMPLE_ACTUAL_COST", costId, Map.of("reason", reason.trim()));
        return actuals(ctx.businessId(), sampleId);
    }

    @Transactional
    public Map<String, Object> linkRecovery(BusinessAccessContext ctx, UUID sampleId, UUID documentId, BigDecimal amount, String notes) {
        Map<String, Object> sample = requireOpenSample(ctx.businessId(), sampleId);
        if ("FREE".equals(sample.get("recovery_intent"))) {
            throw DomainException.conflict("free_sample_no_recovery", "FREE samples cannot carry recovery; change the plan intent first.");
        }
        List<Map<String, Object>> docs = jdbc.queryForList("SELECT kind, status, total FROM commercial_documents "
                + "WHERE business_id = ? AND id = ? FOR UPDATE", ctx.businessId(), documentId);
        if (docs.isEmpty() || !"INVOICE".equals(docs.get(0).get("kind"))) throw DomainException.notFound("Invoice");
        if (!"FINALIZED".equals(docs.get(0).get("status"))) {
            throw DomainException.conflict("document_not_finalized", "Only finalized invoices can recover sample cost.");
        }
        Integer recognized = jdbc.queryForObject("SELECT count(*) FROM financial_obligations WHERE business_id = ? "
                + "AND source_type = 'CUSTOMER_INVOICE' AND source_id = ? AND status = 'OPEN'", Integer.class, ctx.businessId(),
                documentId.toString());
        if (recognized == null || recognized == 0) {
            throw DomainException.conflict("document_not_recognized", "Recognize the invoice into receivables before linking recovery.");
        }
        BigDecimal net = Money.nz((BigDecimal) docs.get(0).get("total")).subtract(Money.nz(jdbc.queryForObject(
                "SELECT COALESCE(sum(amount), 0) FROM commercial_adjustments WHERE business_id = ? AND document_id = ? "
                        + "AND status = 'EFFECTIVE'", BigDecimal.class, ctx.businessId(), documentId)));
        BigDecimal linked = Money.nz(jdbc.queryForObject("SELECT COALESCE(sum(amount), 0) FROM sample_recovery_links "
                + "WHERE business_id = ? AND document_id = ? AND status = 'ACTIVE'", BigDecimal.class, ctx.businessId(), documentId));
        if (amount.add(linked).compareTo(net) > 0) {
            throw new DomainException("recovery_exceeds_invoice", 409, "Only " + Money.str(net.subtract(linked).max(Money.ZERO))
                    + " of this invoice is still available for sample recovery.",
                    Map.of("remaining", Money.str(net.subtract(linked).max(Money.ZERO))));
        }
        Integer duplicate = jdbc.queryForObject("SELECT count(*) FROM sample_recovery_links WHERE business_id = ? AND sample_id = ? "
                + "AND document_id = ? AND status = 'ACTIVE'", Integer.class, ctx.businessId(), sampleId, documentId);
        if (duplicate != null && duplicate > 0) {
            throw DomainException.conflict("already_linked", "This invoice is already linked to the sample; unlink it to change the amount.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO sample_recovery_links(id, business_id, sample_id, document_id, amount, notes, created_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", id, ctx.businessId(), sampleId, documentId, amount,
                notes == null || notes.isBlank() ? null : notes.trim(), ctx.userId());
        audit.record(ctx, AuditEventType.SAMPLE_ACTUAL_RECORDED, "SAMPLE_RECOVERY_LINK", id,
                Map.of("sampleId", sampleId.toString(), "documentId", documentId.toString(), "amount", Money.str(amount)));
        return actuals(ctx.businessId(), sampleId);
    }

    @Transactional
    public Map<String, Object> unlinkRecovery(BusinessAccessContext ctx, UUID sampleId, UUID linkId, String reason) {
        if (reason == null || reason.isBlank()) throw DomainException.invalid("reason", "required");
        int n = jdbc.update("""
                UPDATE sample_recovery_links SET status = 'UNLINKED', unlinked_by = ?, unlinked_at = now(), unlink_reason = ?,
                    version = version + 1
                WHERE business_id = ? AND sample_id = ? AND id = ? AND status = 'ACTIVE'
                """, ctx.userId(), reason.trim(), ctx.businessId(), sampleId, linkId);
        if (n != 1) throw DomainException.notFound("Active recovery link");
        audit.record(ctx, AuditEventType.SAMPLE_ACTUAL_REVERSED, "SAMPLE_RECOVERY_LINK", linkId, Map.of("reason", reason.trim()));
        return actuals(ctx.businessId(), sampleId);
    }

    // ---------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public Totals totals(UUID businessId, UUID sampleId, UUID projectId) {
        Map<String, Object> a = actuals(businessId, sampleId);
        @SuppressWarnings("unchecked") Map<String, Object> cost = (Map<String, Object>) a.get("cost");
        @SuppressWarnings("unchecked") Map<String, Object> recovery = (Map<String, Object>) a.get("recovery");
        BigDecimal notLinked = Money.ZERO;
        @SuppressWarnings("unchecked") List<Map<String, Object>> links = (List<Map<String, Object>>) recovery.get("links");
        for (Map<String, Object> l : links) {
            if (!"ACTIVE".equals(l.get("status"))) continue;
            Integer onProject = projectId == null ? 0 : jdbc.queryForObject("SELECT count(*) FROM project_commercial_document_links "
                    + "WHERE business_id = ? AND project_id = ? AND document_id = ? AND link_status = 'ACTIVE'", Integer.class,
                    businessId, projectId, l.get("documentId"));
            if (onProject == null || onProject == 0) notLinked = notLinked.add(new BigDecimal((String) l.get("recognizedAmount")));
        }
        return new Totals(new BigDecimal((String) cost.get("total")), new BigDecimal((String) recovery.get("recognized")), notLinked);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> actuals(UUID businessId, UUID sampleId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, code, name, kind, recovery_intent, status, project_id "
                + "FROM sample_activities WHERE business_id = ? AND id = ?", businessId, sampleId);
        if (rows.isEmpty()) throw DomainException.notFound("Sample");
        Map<String, Object> sample = rows.get(0);
        String intent = (String) sample.get("recovery_intent");

        BigDecimal material = inventory.sampleMaterialCost(businessId, sampleId);
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (String c : List.of("MATERIAL", "LABOUR", "FREIGHT", "OTHER")) byCategory.put(c, Money.ZERO);
        List<Map<String, Object>> costRows = new ArrayList<>();
        BigDecimal direct = Money.ZERO;
        BigDecimal sourceReversed = Money.ZERO;
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT c.*, EXISTS (SELECT 1 FROM financial_transactions x WHERE x.business_id = c.business_id
                                    AND x.reversed_transaction_id = c.transaction_id) AS source_reversed
                FROM sample_actual_costs c WHERE c.business_id = ? AND c.sample_id = ? ORDER BY c.cost_date, c.created_at
                """, businessId, sampleId)) {
            BigDecimal amount = Money.nz((BigDecimal) r.get("amount"));
            boolean effective = "EFFECTIVE".equals(r.get("status"));
            boolean reversedSource = Boolean.TRUE.equals(r.get("source_reversed"));
            if (effective && !reversedSource) {
                direct = direct.add(amount);
                byCategory.merge((String) r.get("cost_category"), amount, BigDecimal::add);
            } else if (effective) {
                sourceReversed = sourceReversed.add(amount);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("transactionId", r.get("transaction_id"));
            m.put("amount", Money.str(amount));
            m.put("costCategory", r.get("cost_category"));
            m.put("costDate", r.get("cost_date").toString());
            m.put("status", reversedSource && effective ? "SOURCE_REVERSED" : r.get("status"));
            m.put("notes", r.get("notes"));
            m.put("reversalReason", r.get("reversal_reason"));
            costRows.add(m);
        }
        byCategory.merge("MATERIAL", material, BigDecimal::add);
        BigDecimal totalCost = direct.add(material);

        BigDecimal recognized = Money.ZERO, collected = Money.ZERO;
        List<Map<String, Object>> links = new ArrayList<>();
        for (Map<String, Object> l : jdbc.queryForList("""
                SELECT l.*, d.total, d.document_number, d.status AS doc_status, o.id AS obligation_id, o.original_amount
                FROM sample_recovery_links l
                JOIN commercial_documents d ON d.business_id = l.business_id AND d.id = l.document_id
                LEFT JOIN financial_obligations o ON o.business_id = l.business_id AND o.source_type = 'CUSTOMER_INVOICE'
                     AND o.source_id = l.document_id::text AND o.status = 'OPEN'
                WHERE l.business_id = ? AND l.sample_id = ? ORDER BY l.created_at
                """, businessId, sampleId)) {
            BigDecimal amount = Money.nz((BigDecimal) l.get("amount"));
            BigDecimal total = Money.nz((BigDecimal) l.get("total"));
            boolean active = "ACTIVE".equals(l.get("status")) && l.get("obligation_id") != null;
            BigDecimal recognizedShare = Money.ZERO, collectedShare = Money.ZERO;
            if (active) {
                UUID documentId = (UUID) l.get("document_id");
                BigDecimal adjustments = Money.nz(jdbc.queryForObject("SELECT COALESCE(sum(amount), 0) FROM commercial_adjustments "
                        + "WHERE business_id = ? AND document_id = ? AND status = 'EFFECTIVE'", BigDecimal.class, businessId, documentId));
                BigDecimal net = total.subtract(adjustments);
                // A link can never recover more than the invoice's current net amount.
                recognizedShare = amount.min(net).max(Money.ZERO);
                BigDecimal cashApplied = Money.nz(jdbc.queryForObject("SELECT COALESCE(sum(amount), 0) FROM obligation_payment_allocations "
                        + "WHERE business_id = ? AND obligation_id = ? AND status = 'EFFECTIVE' AND allocation_type IN ('CASH', 'ADVANCE')",
                        BigDecimal.class, businessId, l.get("obligation_id")));
                collectedShare = net.signum() == 0 ? Money.ZERO
                        : recognizedShare.multiply(cashApplied.min(net)).divide(net, 4, RoundingMode.HALF_UP);
                recognized = recognized.add(recognizedShare);
                collected = collected.add(collectedShare);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.get("id"));
            m.put("documentId", l.get("document_id"));
            m.put("documentNumber", l.get("document_number"));
            m.put("linkedAmount", Money.str(amount));
            m.put("recognizedAmount", Money.str(recognizedShare));
            m.put("collectedAmount", Money.str(collectedShare));
            m.put("status", "ACTIVE".equals(l.get("status")) && l.get("obligation_id") == null ? "INVOICE_NOT_OPEN" : l.get("status"));
            m.put("unlinkReason", l.get("unlink_reason"));
            links.add(m);
        }
        BigDecimal contribution = recognized.subtract(totalCost);

        List<Map<String, Object>> plans = jdbc.queryForList("SELECT revision, planned_cost, planned_recovery, recovery_intent "
                + "FROM sample_cost_plan_versions WHERE business_id = ? AND activity_id = ? AND status = 'APPROVED' "
                + "ORDER BY revision DESC LIMIT 1", businessId, sampleId);
        Map<String, Object> plan = new LinkedHashMap<>();
        if (plans.isEmpty()) {
            plan.put("status", "NO_APPROVED_PLAN");
        } else {
            BigDecimal plannedCost = ((BigDecimal) plans.get(0).get("planned_cost")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal plannedRecovery = (BigDecimal) plans.get(0).get("planned_recovery");
            plan.put("status", "APPROVED_PLAN");
            plan.put("revision", plans.get(0).get("revision"));
            plan.put("plannedCost", Money.str(plannedCost));
            plan.put("plannedRecovery", plannedRecovery == null ? null : Money.str(plannedRecovery));
            plan.put("costVariance", Money.str(totalCost.subtract(plannedCost)));
            plan.put("recoveryVariance", plannedRecovery == null ? null : Money.str(recognized.subtract(plannedRecovery)));
        }

        String recoveryStatus = switch (intent) {
            case "FREE" -> "FREE_SAMPLE";
            case "PAID" -> totalCost.signum() == 0 ? (recognized.signum() > 0 ? "RECOVERED" : "NO_COST_YET")
                    : recognized.compareTo(totalCost) >= 0 ? "FULLY_RECOVERED" : recognized.signum() > 0 ? "UNDER_RECOVERED" : "NOT_RECOVERED";
            default -> recognized.signum() == 0 ? "NOT_RECOVERED"
                    : recognized.compareTo(totalCost) >= 0 ? "FULLY_RECOVERED" : "PARTIALLY_RECOVERED";
        };

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sampleId", sampleId);
        body.put("code", sample.get("code"));
        body.put("name", sample.get("name"));
        body.put("kind", sample.get("kind"));
        body.put("status", sample.get("status"));
        body.put("projectId", sample.get("project_id"));
        body.put("recoveryIntent", intent);
        body.put("version", VERSION);
        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("inventoryMaterial", Money.str(material));
        cost.put("directCosts", Money.str(direct));
        cost.put("byCategory", byCategory.entrySet().stream().map(e -> Map.of("category", e.getKey(), "amount", Money.str(e.getValue()))).toList());
        cost.put("total", Money.str(totalCost));
        cost.put("excludedSourceReversed", Money.str(sourceReversed));
        cost.put("entries", costRows);
        body.put("cost", cost);
        Map<String, Object> recovery = new LinkedHashMap<>();
        recovery.put("recognized", Money.str(recognized));
        recovery.put("collected", Money.str(collected));
        recovery.put("outstanding", Money.str(recognized.subtract(collected)));
        recovery.put("status", recoveryStatus);
        recovery.put("recoveryPct", Money.pct(recognized, totalCost) == null ? null : Money.pct(recognized, totalCost).toPlainString());
        recovery.put("links", links);
        body.put("recovery", recovery);
        body.put("contribution", Money.str(contribution));
        body.put("plan", plan);
        return body;
    }

    private Map<String, Object> requireOpenSample(UUID businessId, UUID sampleId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT status, recovery_intent FROM sample_activities "
                + "WHERE business_id = ? AND id = ? FOR UPDATE", businessId, sampleId);
        if (rows.isEmpty()) throw DomainException.notFound("Sample");
        String status = (String) rows.get(0).get("status");
        if ("CANCELLED".equals(status) || "ARCHIVED".equals(status)) {
            throw DomainException.conflict("sample_closed", "Cancelled or archived samples cannot record actuals.");
        }
        return rows.get(0);
    }
}
