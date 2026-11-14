package com.app.sme_health_backend.commercial.lifecycle;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.obligation.FinancialObligationService;
import com.app.sme_health_backend.obligation.dto.ObligationVoidRequest;
import com.app.sme_health_backend.productsupport.AuditRecorder;
import com.app.sme_health_backend.productsupport.DomainException;
import com.app.sme_health_backend.productsupport.Hashing;
import com.app.sme_health_backend.productsupport.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * F04 full lifecycle over the single AR/AP authority (Developer A's financial_obligations):
 * DRAFT -> FINALIZED (issued) -> RECOGNIZED (C04 endpoint) -> settled (C04 allocations) -> corrected (credit/debit
 * notes) -> cancelled/voided through compensating records only.
 *
 * <p>Credit notes (invoices) and debit notes (bills) never edit the recognized obligation. Each is an append-only
 * commercial fact plus one non-cash CREDIT_NOTE allocation, so outstanding = original - effective allocations keeps
 * holding in every Developer-A reader (aging, forecast, funding, facade) without any change to those readers.
 */
@Service
public class CommercialLifecycleService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Karachi");

    private final JdbcTemplate jdbc;
    private final FinancialObligationService obligations;
    private final AuditRecorder audit;

    public CommercialLifecycleService(JdbcTemplate jdbc, FinancialObligationService obligations, AuditRecorder audit) {
        this.jdbc = jdbc;
        this.obligations = obligations;
        this.audit = audit;
    }

    public record Adjustment(UUID id, String kind, String number, BigDecimal amount, LocalDate date, String reason,
                             String status, UUID obligationId, UUID allocationId, OffsetDateTime createdAt,
                             UUID createdBy, OffsetDateTime cancelledAt, String cancelReason) { }

    // ---------------------------------------------------------------- read model

    @Transactional(readOnly = true)
    public Map<String, Object> lifecycle(BusinessAccessContext ctx, UUID documentId) {
        Map<String, Object> doc = document(ctx.businessId(), documentId, false);
        String kind = (String) doc.get("kind");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("documentId", documentId);
        body.put("kind", kind);
        body.put("status", doc.get("status"));
        body.put("documentNumber", doc.get("document_number"));
        body.put("documentDate", str(doc.get("document_date")));
        body.put("dueDate", str(doc.get("due_date")));
        BigDecimal total = doc.get("total") == null ? null : (BigDecimal) doc.get("total");
        body.put("total", Money.str(total));

        Map<String, Object> obligation = obligation(ctx.businessId(), documentId, kind, false);
        List<Adjustment> adjustments = adjustments(ctx.businessId(), documentId);
        BigDecimal effectiveAdjustments = adjustments.stream().filter(a -> "EFFECTIVE".equals(a.status()))
                .map(Adjustment::amount).reduce(Money.ZERO, BigDecimal::add);
        body.put("adjustments", adjustments.stream().map(CommercialLifecycleService::adjustmentBody).toList());
        body.put("effectiveAdjustmentTotal", Money.str(effectiveAdjustments));
        body.put("netDocumentAmount", total == null ? null : Money.str(total.setScale(4, RoundingMode.UNNECESSARY).subtract(effectiveAdjustments)));

        List<String> voidBlockers = new ArrayList<>();
        boolean recognized = obligation != null;
        body.put("recognized", recognized);
        if (recognized) {
            UUID obligationId = (UUID) obligation.get("id");
            Map<String, BigDecimal> split = allocationSplit(ctx.businessId(), obligationId);
            BigDecimal original = (BigDecimal) obligation.get("original_amount");
            BigDecimal allocated = split.values().stream().reduce(Money.ZERO, BigDecimal::add);
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("obligationId", obligationId);
            rec.put("obligationStatus", obligation.get("status"));
            rec.put("direction", obligation.get("direction"));
            rec.put("counterpartyId", obligation.get("counterparty_id"));
            rec.put("originalAmount", Money.str(original));
            rec.put("cashSettled", Money.str(split.get("CASH")));
            rec.put("advanceApplied", Money.str(split.get("ADVANCE")));
            rec.put("adjusted", Money.str(split.get("CREDIT_NOTE")));
            rec.put("outstandingAmount", Money.str("VOIDED".equals(obligation.get("status")) ? Money.ZERO
                    : original.subtract(allocated).max(Money.ZERO)));
            rec.put("settlementState", settlementState(original, allocated, (String) obligation.get("status")));
            rec.put("allocations", allocations(ctx.businessId(), obligationId));
            body.put("recognition", rec);
            if ("OPEN".equals(obligation.get("status"))
                    && split.get("CASH").add(split.get("ADVANCE")).signum() > 0) {
                voidBlockers.add("SETTLEMENTS_MUST_BE_REVERSED");
            }
        } else {
            body.put("recognition", null);
        }
        Integer stock = jdbc.queryForObject("""
                SELECT count(*) FROM inventory_movements m WHERE m.business_id = ? AND m.source_id = ?
                  AND m.source_type IN ('SUPPLIER_BILL', 'CUSTOMER_INVOICE') AND m.movement_type IN ('RECEIPT', 'ISSUE')
                  AND NOT EXISTS (SELECT 1 FROM inventory_movements x WHERE x.business_id = m.business_id
                                  AND x.related_movement_id = m.id AND x.movement_type = 'REVERSAL')
                """, Integer.class, ctx.businessId(), documentId);
        body.put("effectiveStockMovements", stock == null ? 0 : stock);
        if (stock != null && stock > 0) {
            voidBlockers.add("STOCK_MOVEMENTS_MUST_BE_REVERSED");
        }
        Integer itemLines = jdbc.queryForObject("SELECT count(*) FROM commercial_document_lines WHERE business_id = ? "
                + "AND document_id = ? AND inventory_item_id IS NOT NULL", Integer.class, ctx.businessId(), documentId);
        body.put("inventoryLinkedLines", itemLines == null ? 0 : itemLines);
        body.put("projectLinks", jdbc.queryForList("SELECT project_id, allocated_amount, link_status FROM "
                + "project_commercial_document_links WHERE business_id = ? AND document_id = ? AND link_status = 'ACTIVE'",
                ctx.businessId(), documentId).stream().map(r -> Map.of("projectId", r.get("project_id"),
                "allocatedAmount", Money.str((BigDecimal) r.get("allocated_amount")))).toList());

        body.put("evidence", jdbc.queryForList("""
                SELECT l.id, l.uploaded_document_id, l.status, l.created_at, u.processing_status, u.document_type_hint
                FROM commercial_document_evidence_links l
                JOIN uploaded_documents u ON u.id = l.uploaded_document_id AND u.user_id = l.business_id
                WHERE l.business_id = ? AND l.document_id = ? ORDER BY l.created_at
                """, ctx.businessId(), documentId).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("uploadedDocumentId", r.get("uploaded_document_id"));
            m.put("status", r.get("status"));
            m.put("reviewStatus", r.get("processing_status"));
            m.put("typeHint", r.get("document_type_hint"));
            m.put("linkedAt", str(r.get("created_at")));
            return m;
        }).toList());
        boolean finalized = "FINALIZED".equals(doc.get("status"));
        boolean obligationOpen = recognized && "OPEN".equals(obligation.get("status"));
        Map<String, Object> actions = new LinkedHashMap<>();
        actions.put("canRecognize", finalized && !recognized);
        actions.put("canAdjust", finalized && obligationOpen);
        actions.put("canVoid", finalized && voidBlockers.isEmpty());
        actions.put("voidRequiresFinancialReversal", obligationOpen);
        actions.put("canReceiveStock", finalized && "BILL".equals(kind) && itemLines != null && itemLines > 0);
        actions.put("canFulfilFromStock", finalized && "INVOICE".equals(kind) && itemLines != null && itemLines > 0);
        body.put("actions", actions);
        body.put("voidBlockers", voidBlockers);
        return body;
    }

    // ---------------------------------------------------------------- credit / debit notes

    @Transactional
    public Map<String, Object> issueAdjustment(BusinessAccessContext ctx, String key, UUID documentId, BigDecimal amount,
                                               LocalDate date, String reason) {
        requireKey(key);
        String cleanReason = reason(reason, "reason");
        LocalDate effective = date != null ? date : today();
        String hash = Hashing.sha256(ctx.businessId(), documentId, amount.stripTrailingZeros().toPlainString(), effective, cleanReason);

        List<Map<String, Object>> replay = jdbc.queryForList("SELECT id, payload_hash FROM commercial_adjustments "
                + "WHERE business_id = ? AND idempotency_key = ?", ctx.businessId(), key);
        if (!replay.isEmpty()) {
            if (!hash.equals(replay.get(0).get("payload_hash"))) {
                throw DomainException.conflict("idempotency_key_reused", "This Idempotency-Key was used for a different adjustment.");
            }
            return adjustmentBody(adjustment(ctx.businessId(), (UUID) replay.get(0).get("id")));
        }

        Map<String, Object> doc = document(ctx.businessId(), documentId, true);
        if (!"FINALIZED".equals(doc.get("status"))) {
            throw DomainException.conflict("document_not_finalized", "Only finalized documents can be adjusted.");
        }
        String kind = (String) doc.get("kind");
        Map<String, Object> obligation = obligation(ctx.businessId(), documentId, kind, true);
        if (obligation == null || !"OPEN".equals(obligation.get("status"))) {
            throw DomainException.conflict("document_not_recognized",
                    "Recognize the document into receivables/payables before issuing a credit or debit note.");
        }
        LocalDate documentDate = ((Date) doc.get("document_date")).toLocalDate();
        if (effective.isBefore(documentDate) || effective.isAfter(today())) {
            throw DomainException.invalid("adjustmentDate", "date_out_of_range");
        }
        UUID obligationId = (UUID) obligation.get("id");
        BigDecimal original = (BigDecimal) obligation.get("original_amount");
        BigDecimal allocated = allocationSplit(ctx.businessId(), obligationId).values().stream()
                .reduce(Money.ZERO, BigDecimal::add);
        BigDecimal outstanding = original.subtract(allocated);
        if (amount.compareTo(outstanding) > 0) {
            throw new DomainException("adjustment_exceeds_outstanding", 409,
                    "The adjustment exceeds the outstanding balance (" + Money.str(outstanding)
                            + "). Reverse settlements first or record a refund.",
                    Map.of("outstandingAmount", Money.str(outstanding)));
        }

        String adjustmentKind = "INVOICE".equals(kind) ? "CREDIT_NOTE" : "DEBIT_NOTE";
        long next = nextNumber(ctx.businessId(), adjustmentKind);
        String number = ("CREDIT_NOTE".equals(adjustmentKind) ? "CN-" : "DN-") + String.format("%06d", next);
        UUID adjustmentId = UUID.randomUUID();
        UUID allocationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO obligation_payment_allocations(id, business_id, obligation_id, allocation_type,
                    commercial_adjustment_id, amount, effective_date, status, created_by_user_id, created_at, version)
                VALUES (?, ?, ?, 'CREDIT_NOTE', ?, ?, ?, 'EFFECTIVE', ?, ?, 0)
                """, allocationId, ctx.businessId(), obligationId, adjustmentId, amount, Date.valueOf(effective),
                ctx.userId(), now);
        jdbc.update("""
                INSERT INTO commercial_adjustments(id, business_id, document_id, adjustment_kind, adjustment_number, amount,
                    adjustment_date, reason, obligation_id, allocation_id, status, idempotency_key, payload_hash,
                    created_by_user_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'EFFECTIVE', ?, ?, ?, ?)
                """, adjustmentId, ctx.businessId(), documentId, adjustmentKind, number,
                amount.setScale(2, RoundingMode.UNNECESSARY), Date.valueOf(effective), cleanReason, obligationId,
                allocationId, key, hash, ctx.userId(), now);
        audit.record(ctx, AuditEventType.COMMERCIAL_ADJUSTMENT_ISSUED, "COMMERCIAL_ADJUSTMENT", adjustmentId,
                Map.of("documentId", documentId.toString(), "number", number, "amount", Money.str(amount),
                        "obligationId", obligationId.toString()));
        return adjustmentBody(adjustment(ctx.businessId(), adjustmentId));
    }

    @Transactional
    public Map<String, Object> cancelAdjustment(BusinessAccessContext ctx, UUID adjustmentId, String reason) {
        String cleanReason = reason(reason, "reason");
        Adjustment current = lockAdjustment(ctx.businessId(), adjustmentId);
        if (!"EFFECTIVE".equals(current.status())) {
            throw DomainException.conflict("adjustment_already_cancelled", "This adjustment is already cancelled.");
        }
        Map<String, Object> obligation = jdbc.queryForList("SELECT id, status FROM financial_obligations "
                + "WHERE business_id = ? AND id = ? FOR UPDATE", ctx.businessId(), current.obligationId()).get(0);
        if (!"OPEN".equals(obligation.get("status"))) {
            throw DomainException.conflict("obligation_not_open", "The recognized obligation is no longer open.");
        }
        cancel(ctx, current, cleanReason);
        return adjustmentBody(adjustment(ctx.businessId(), adjustmentId));
    }

    // ---------------------------------------------------------------- void after recognition

    /**
     * Voids a recognized document by compensation only: requires settlements and stock movements to be reversed
     * first, cancels effective credit/debit notes, voids the obligation through Developer A's own
     * FinancialObligationService.voidObligation, then voids the commercial document. Nothing is deleted.
     */
    @Transactional
    public Map<String, Object> voidRecognized(BusinessAccessContext ctx, UUID documentId, String reason) {
        String cleanReason = reason(reason, "reason");
        Map<String, Object> doc = document(ctx.businessId(), documentId, true);
        if (!"FINALIZED".equals(doc.get("status"))) {
            throw DomainException.conflict("document_not_finalized", "Only finalized documents can be voided.");
        }
        String kind = (String) doc.get("kind");
        Map<String, Object> obligation = obligation(ctx.businessId(), documentId, kind, true);
        Map<String, String> blockers = new LinkedHashMap<>();
        if (obligation != null && "OPEN".equals(obligation.get("status"))) {
            Map<String, BigDecimal> split = allocationSplit(ctx.businessId(), (UUID) obligation.get("id"));
            if (split.get("CASH").add(split.get("ADVANCE")).signum() > 0) {
                blockers.put("settlements", "SETTLEMENTS_MUST_BE_REVERSED");
            }
        }
        Integer stock = jdbc.queryForObject("""
                SELECT count(*) FROM inventory_movements m WHERE m.business_id = ? AND m.source_id = ?
                  AND m.source_type IN ('SUPPLIER_BILL', 'CUSTOMER_INVOICE') AND m.movement_type IN ('RECEIPT', 'ISSUE')
                  AND NOT EXISTS (SELECT 1 FROM inventory_movements x WHERE x.business_id = m.business_id
                                  AND x.related_movement_id = m.id AND x.movement_type = 'REVERSAL')
                """, Integer.class, ctx.businessId(), documentId);
        if (stock != null && stock > 0) {
            blockers.put("stock", "STOCK_MOVEMENTS_MUST_BE_REVERSED");
        }
        if (!blockers.isEmpty()) {
            throw new DomainException("void_prerequisites_unmet", 409,
                    "Reverse settlements and stock movements before voiding this recognized document.", blockers);
        }
        String number = (String) doc.get("document_number");
        if (obligation != null && "OPEN".equals(obligation.get("status"))) {
            for (Adjustment a : adjustments(ctx.businessId(), documentId)) {
                if ("EFFECTIVE".equals(a.status())) {
                    cancel(ctx, lockAdjustment(ctx.businessId(), a.id()), "Superseded by void of " + number + ": " + cleanReason);
                }
            }
            obligations.voidForCommercialDocument(ctx, (UUID) obligation.get("id"),
                    new ObligationVoidRequest("Commercial document " + number + " voided: " + cleanReason));
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int updated = jdbc.update("""
                UPDATE commercial_documents SET status = 'VOID', voided_by_user_id = ?, voided_at = ?, void_reason = ?,
                    updated_by_user_id = ?, updated_at = ?, version = version + 1
                WHERE business_id = ? AND id = ? AND status = 'FINALIZED'
                """, ctx.userId(), now, cleanReason, ctx.userId(), now, ctx.businessId(), documentId);
        if (updated != 1) {
            throw DomainException.conflict("stale_document", "The document changed; reload and try again.");
        }
        audit.record(ctx, AuditEventType.COMMERCIAL_RECOGNIZED_DOCUMENT_VOIDED, "COMMERCIAL_DOCUMENT", documentId,
                Map.of("documentNumber", number, "obligationVoided", obligation != null));
        return lifecycle(ctx, documentId);
    }

    /**
     * Attaches REVIEWED OCR evidence (processing_status = confirmed) of the same business. Provenance only: no
     * recognition, posting or amount change. Unreviewed extractions must first be reviewed in the document workflow.
     */
    @Transactional
    public Map<String, Object> linkEvidence(BusinessAccessContext ctx, UUID documentId, UUID uploadedDocumentId) {
        if (uploadedDocumentId == null) throw DomainException.invalid("uploadedDocumentId", "required");
        Map<String, Object> doc = document(ctx.businessId(), documentId, true);
        if ("VOID".equals(doc.get("status"))) throw DomainException.conflict("document_void", "Void documents cannot receive evidence.");
        List<String> status = jdbc.queryForList("SELECT processing_status FROM uploaded_documents WHERE id = ? AND user_id = ?",
                String.class, uploadedDocumentId, ctx.businessId());
        if (status.isEmpty()) throw DomainException.notFound("Uploaded document");
        if (!"confirmed".equals(status.get(0))) {
            throw DomainException.conflict("evidence_not_reviewed", "Review and confirm the scanned document before linking it as evidence.");
        }
        Integer existing = jdbc.queryForObject("SELECT count(*) FROM commercial_document_evidence_links WHERE business_id = ? "
                + "AND document_id = ? AND uploaded_document_id = ? AND status = 'ACTIVE'", Integer.class, ctx.businessId(), documentId, uploadedDocumentId);
        if (existing != null && existing > 0) throw DomainException.conflict("evidence_already_linked", "This evidence is already linked.");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO commercial_document_evidence_links(id, business_id, document_id, uploaded_document_id, created_by) "
                + "VALUES (?, ?, ?, ?, ?)", id, ctx.businessId(), documentId, uploadedDocumentId, ctx.userId());
        audit.record(ctx, AuditEventType.COMMERCIAL_ADJUSTMENT_ISSUED, "COMMERCIAL_EVIDENCE_LINK", id,
                Map.of("documentId", documentId.toString(), "uploadedDocumentId", uploadedDocumentId.toString(), "action", "LINKED"));
        return lifecycle(ctx, documentId);
    }

    @Transactional
    public Map<String, Object> unlinkEvidence(BusinessAccessContext ctx, UUID documentId, UUID linkId, String reason) {
        String clean = reason(reason, "reason");
        int n = jdbc.update("UPDATE commercial_document_evidence_links SET status = 'UNLINKED', unlinked_by = ?, unlinked_at = now(), "
                + "unlink_reason = ?, version = version + 1 WHERE business_id = ? AND document_id = ? AND id = ? AND status = 'ACTIVE'",
                ctx.userId(), clean.length() > 300 ? clean.substring(0, 300) : clean, ctx.businessId(), documentId, linkId);
        if (n != 1) throw DomainException.notFound("Active evidence link");
        audit.record(ctx, AuditEventType.COMMERCIAL_ADJUSTMENT_CANCELLED, "COMMERCIAL_EVIDENCE_LINK", linkId, Map.of("action", "UNLINKED"));
        return lifecycle(ctx, documentId);
    }

    /** Blockers that prevent the plain F04 void (used by CommercialDocumentService). */
    @Transactional(readOnly = true)
    public List<String> plainVoidBlockers(UUID businessId, UUID documentId, String kind) {
        List<String> blockers = new ArrayList<>();
        Map<String, Object> obligation = obligation(businessId, documentId, kind, false);
        if (obligation != null && "OPEN".equals(obligation.get("status"))) {
            blockers.add("DOCUMENT_RECOGNIZED");
        }
        Integer stock = jdbc.queryForObject("""
                SELECT count(*) FROM inventory_movements m WHERE m.business_id = ? AND m.source_id = ?
                  AND m.source_type IN ('SUPPLIER_BILL', 'CUSTOMER_INVOICE') AND m.movement_type IN ('RECEIPT', 'ISSUE')
                  AND NOT EXISTS (SELECT 1 FROM inventory_movements x WHERE x.business_id = m.business_id
                                  AND x.related_movement_id = m.id AND x.movement_type = 'REVERSAL')
                """, Integer.class, businessId, documentId);
        if (stock != null && stock > 0) {
            blockers.add("STOCK_MOVEMENTS_MUST_BE_REVERSED");
        }
        return blockers;
    }

    // ---------------------------------------------------------------- helpers

    private void cancel(BusinessAccessContext ctx, Adjustment adjustment, String reason) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int allocation = jdbc.update("""
                UPDATE obligation_payment_allocations SET status = 'CANCELLED', cancelled_at = ?, cancellation_reason = ?,
                    version = version + 1
                WHERE business_id = ? AND id = ? AND status = 'EFFECTIVE' AND allocation_type = 'CREDIT_NOTE'
                """, now, reason, ctx.businessId(), adjustment.allocationId());
        int row = jdbc.update("""
                UPDATE commercial_adjustments SET status = 'CANCELLED', cancelled_at = ?, cancelled_by_user_id = ?,
                    cancel_reason = ?, version = version + 1
                WHERE business_id = ? AND id = ? AND status = 'EFFECTIVE'
                """, now, ctx.userId(), reason, ctx.businessId(), adjustment.id());
        if (allocation != 1 || row != 1) {
            throw DomainException.conflict("stale_adjustment", "The adjustment changed; reload and try again.");
        }
        audit.record(ctx, AuditEventType.COMMERCIAL_ADJUSTMENT_CANCELLED, "COMMERCIAL_ADJUSTMENT", adjustment.id(),
                Map.of("number", adjustment.number(), "amount", Money.str(adjustment.amount())));
    }

    private Map<String, Object> document(UUID businessId, UUID documentId, boolean lock) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, kind, status, document_number, document_date, "
                + "due_date, total FROM commercial_documents WHERE business_id = ? AND id = ?" + (lock ? " FOR UPDATE" : ""),
                businessId, documentId);
        if (rows.isEmpty()) {
            throw DomainException.notFound("Commercial document");
        }
        return rows.get(0);
    }

    /** The recognized obligation for a document (latest; OPEN preferred), or null. */
    private Map<String, Object> obligation(UUID businessId, UUID documentId, String kind, boolean lock) {
        String sourceType = "INVOICE".equals(kind) ? "CUSTOMER_INVOICE" : "SUPPLIER_BILL";
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, status, original_amount, direction, counterparty_id "
                + "FROM financial_obligations WHERE business_id = ? AND source_type = ? AND source_id = ?"
                + " ORDER BY (status = 'OPEN') DESC, created_at DESC" + (lock ? " FOR UPDATE" : ""),
                businessId, sourceType, documentId.toString());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, BigDecimal> allocationSplit(UUID businessId, UUID obligationId) {
        Map<String, BigDecimal> split = new LinkedHashMap<>();
        split.put("CASH", Money.ZERO);
        split.put("ADVANCE", Money.ZERO);
        split.put("CREDIT_NOTE", Money.ZERO);
        jdbc.query("SELECT allocation_type, COALESCE(sum(amount), 0) FROM obligation_payment_allocations "
                        + "WHERE business_id = ? AND obligation_id = ? AND status = 'EFFECTIVE' GROUP BY allocation_type",
                rs -> {
                    split.put(rs.getString(1), Money.nz(rs.getBigDecimal(2)));
                }, businessId, obligationId);
        return split;
    }

    private List<Map<String, Object>> allocations(UUID businessId, UUID obligationId) {
        return jdbc.queryForList("""
                SELECT id, allocation_type, amount, effective_date, status, financial_transaction_id, advance_id,
                       commercial_adjustment_id, cancelled_at, cancellation_reason
                FROM obligation_payment_allocations WHERE business_id = ? AND obligation_id = ?
                ORDER BY effective_date, created_at
                """, businessId, obligationId).stream().map(r -> {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("id", r.get("id"));
            a.put("type", r.get("allocation_type"));
            a.put("amount", Money.str((BigDecimal) r.get("amount")));
            a.put("effectiveDate", str(r.get("effective_date")));
            a.put("status", r.get("status"));
            a.put("transactionId", r.get("financial_transaction_id"));
            a.put("advanceId", r.get("advance_id"));
            a.put("adjustmentId", r.get("commercial_adjustment_id"));
            a.put("cancellationReason", r.get("cancellation_reason"));
            return a;
        }).toList();
    }

    private List<Adjustment> adjustments(UUID businessId, UUID documentId) {
        return jdbc.query("SELECT * FROM commercial_adjustments WHERE business_id = ? AND document_id = ? "
                + "ORDER BY adjustment_date, created_at", (rs, n) -> map(rs), businessId, documentId);
    }

    private Adjustment adjustment(UUID businessId, UUID id) {
        return jdbc.query("SELECT * FROM commercial_adjustments WHERE business_id = ? AND id = ?", (rs, n) -> map(rs),
                businessId, id).stream().findFirst().orElseThrow(() -> DomainException.notFound("Adjustment"));
    }

    private Adjustment lockAdjustment(UUID businessId, UUID id) {
        return jdbc.query("SELECT * FROM commercial_adjustments WHERE business_id = ? AND id = ? FOR UPDATE",
                (rs, n) -> map(rs), businessId, id).stream().findFirst().orElseThrow(() -> DomainException.notFound("Adjustment"));
    }

    private static Adjustment map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Adjustment(rs.getObject("id", UUID.class), rs.getString("adjustment_kind"),
                rs.getString("adjustment_number"), Money.nz(rs.getBigDecimal("amount")),
                rs.getObject("adjustment_date", LocalDate.class), rs.getString("reason"), rs.getString("status"),
                rs.getObject("obligation_id", UUID.class), rs.getObject("allocation_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("created_by_user_id", UUID.class),
                rs.getObject("cancelled_at", OffsetDateTime.class), rs.getString("cancel_reason"));
    }

    static Map<String, Object> adjustmentBody(Adjustment a) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", a.id());
        body.put("kind", a.kind());
        body.put("number", a.number());
        body.put("amount", Money.str(a.amount()));
        body.put("adjustmentDate", a.date().toString());
        body.put("reason", a.reason());
        body.put("status", a.status());
        body.put("obligationId", a.obligationId());
        body.put("allocationId", a.allocationId());
        body.put("createdAt", a.createdAt() == null ? null : a.createdAt().toString());
        body.put("cancelledAt", a.cancelledAt() == null ? null : a.cancelledAt().toString());
        body.put("cancelReason", a.cancelReason());
        return body;
    }

    private long nextNumber(UUID businessId, String kind) {
        jdbc.update("INSERT INTO commercial_adjustment_sequences(business_id, kind, next_value) VALUES (?, ?, 1) "
                + "ON CONFLICT (business_id, kind) DO NOTHING", businessId, kind);
        Long value = jdbc.queryForObject("SELECT next_value FROM commercial_adjustment_sequences WHERE business_id = ? "
                + "AND kind = ? FOR UPDATE", Long.class, businessId, kind);
        jdbc.update("UPDATE commercial_adjustment_sequences SET next_value = next_value + 1 WHERE business_id = ? AND kind = ?",
                businessId, kind);
        return value == null ? 1 : value;
    }

    private static String settlementState(BigDecimal original, BigDecimal allocated, String status) {
        if ("VOIDED".equals(status)) return "VOIDED";
        if (allocated.signum() == 0) return "UNPAID";
        if (allocated.compareTo(original) >= 0) return "SETTLED";
        return "PARTIALLY_SETTLED";
    }

    private static String reason(String reason, String field) {
        if (reason == null || reason.isBlank()) {
            throw DomainException.invalid(field, "required");
        }
        if (reason.trim().length() > 400) {
            throw DomainException.invalid(field, "too_long");
        }
        return reason.trim();
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 120) {
            throw DomainException.invalid("Idempotency-Key", "required");
        }
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }
}
