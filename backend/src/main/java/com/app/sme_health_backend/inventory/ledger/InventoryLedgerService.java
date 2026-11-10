package com.app.sme_health_backend.inventory.ledger;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.productsupport.AuditRecorder;
import com.app.sme_health_backend.productsupport.DomainException;
import com.app.sme_health_backend.productsupport.Hashing;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * F08 authoritative perpetual inventory under frozen policy {@value #POLICY_VERSION}.
 *
 * <p>Every write locks the item's catalog row, so concurrent movements of one item are serialized and an issue can
 * never consume stock that a concurrent issue already took. Each movement stores exact signed deltas and the
 * resulting (quantity, value); the position row always equals the latest movement (enforced by a trigger).
 *
 * <ul>
 *   <li>Inbound (opening, receipt, adjustment-in): value is the exact supplied/sourced amount (capitalized).</li>
 *   <li>Outbound (issue, supplier return, adjustment-out): partial value = round(V*q/Q, 4, HALF_UP); taking the
 *       entire quantity takes exactly V, so depletion always ends at (0, 0).</li>
 *   <li>Return to stock against an issue: proportional share of that issue's exact value; the final return of an
 *       issue takes the exact remaining residual, so returns never create or destroy value.</li>
 *   <li>Reversal: the exact negated deltas of the original movement (perfect symmetry); blocked when later
 *       movements make that impossible without negative stock or a non-zero empty value.</li>
 * </ul>
 */
@Service
public class InventoryLedgerService {
    public static final String POLICY_VERSION = "f08-wac-v1";
    public static final int VALUE_SCALE = 4;
    public static final RoundingMode VALUE_ROUNDING = RoundingMode.HALF_UP;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Karachi");

    private final InventoryLedgerRepository repository;
    private final JdbcTemplate jdbc;
    private final AuditRecorder audit;

    public InventoryLedgerService(InventoryLedgerRepository repository, AuditRecorder audit) {
        this.repository = repository;
        this.jdbc = repository.jdbc();
        this.audit = audit;
    }

    /** Normalized movement intent; value is null for outbound movements valued by policy. */
    public record Spec(
            InventoryMovementType type,
            UUID itemId,
            BigDecimal quantity,
            BigDecimal value,
            LocalDate date,
            InventoryPurpose purpose,
            InventorySourceType sourceType,
            UUID sourceId,
            UUID sourceLineId,
            UUID projectId,
            UUID sampleId,
            UUID relatedMovementId,
            String notes
    ) { }

    public record Result(InventoryMovementRecord movement, boolean replayed) { }

    // ---------------------------------------------------------------- public operations

    @Transactional
    public Result opening(BusinessAccessContext ctx, String key, UUID itemId, BigDecimal quantity, BigDecimal value,
                          LocalDate date, String notes) {
        requireValue(value);
        return apply(ctx, key, new Spec(InventoryMovementType.OPENING, itemId, quantity, value, date(date), null,
                InventorySourceType.MANUAL, null, null, null, null, null, notes));
    }

    @Transactional
    public Result receipt(BusinessAccessContext ctx, String key, UUID itemId, BigDecimal quantity, BigDecimal value,
                          LocalDate date, UUID transactionId, String notes) {
        requireValue(value);
        InventorySourceType source = transactionId == null ? InventorySourceType.MANUAL : InventorySourceType.TRANSACTION;
        return apply(ctx, key, new Spec(InventoryMovementType.RECEIPT, itemId, quantity, value, date(date), null,
                source, transactionId, null, null, null, null, notes));
    }

    @Transactional
    public Result issue(BusinessAccessContext ctx, String key, UUID itemId, BigDecimal quantity, LocalDate date,
                        InventoryPurpose purpose, UUID projectId, UUID sampleId, String notes) {
        if (purpose == null) {
            throw DomainException.invalid("purpose", "required");
        }
        if (purpose == InventoryPurpose.PROJECT && projectId == null) {
            throw DomainException.invalid("projectId", "required");
        }
        if (purpose == InventoryPurpose.SAMPLE && sampleId == null) {
            throw DomainException.invalid("sampleId", "required");
        }
        if (purpose != InventoryPurpose.SAMPLE && sampleId != null) {
            throw DomainException.invalid("sampleId", "not_allowed");
        }
        if (purpose != InventoryPurpose.PROJECT && purpose != InventoryPurpose.SALE && projectId != null) {
            throw DomainException.invalid("projectId", "not_allowed");
        }
        InventorySourceType source = switch (purpose) {
            case PROJECT -> InventorySourceType.PROJECT;
            case SAMPLE -> InventorySourceType.SAMPLE;
            default -> InventorySourceType.MANUAL;
        };
        UUID sourceId = purpose == InventoryPurpose.PROJECT ? projectId : purpose == InventoryPurpose.SAMPLE ? sampleId : null;
        return apply(ctx, key, new Spec(InventoryMovementType.ISSUE, itemId, quantity, null, date(date), purpose,
                source, sourceId, null, projectId, sampleId, null, notes));
    }

    @Transactional
    public Result supplierReturn(BusinessAccessContext ctx, String key, UUID itemId, BigDecimal quantity,
                                 LocalDate date, UUID billId, String notes) {
        InventorySourceType source = billId == null ? InventorySourceType.MANUAL : InventorySourceType.SUPPLIER_BILL;
        return apply(ctx, key, new Spec(InventoryMovementType.SUPPLIER_RETURN, itemId, quantity, null, date(date), null,
                source, billId, null, null, null, null, notes));
    }

    @Transactional
    public Result returnToStock(BusinessAccessContext ctx, String key, UUID issueMovementId, BigDecimal quantity,
                                LocalDate date, String notes) {
        InventoryMovementRecord issue = repository.movement(ctx.businessId(), issueMovementId)
                .orElseThrow(() -> DomainException.notFound("Issue movement"));
        if (issue.movementType() != InventoryMovementType.ISSUE) {
            throw DomainException.conflict("return_requires_issue", "Stock can be returned only against an issue.");
        }
        return apply(ctx, key, new Spec(InventoryMovementType.CUSTOMER_RETURN, issue.itemId(), quantity, null, date(date),
                issue.purpose(), InventorySourceType.MOVEMENT, issue.id(), null, issue.projectId(), issue.sampleId(),
                issue.id(), notes));
    }

    @Transactional
    public Result adjust(BusinessAccessContext ctx, String key, UUID itemId, boolean increase, BigDecimal quantity,
                         BigDecimal value, LocalDate date, String notes) {
        if (notes == null || notes.isBlank()) {
            throw DomainException.invalid("notes", "required");
        }
        if (increase) {
            requireValue(value);
        } else if (value != null) {
            throw DomainException.invalid("value", "not_allowed");
        }
        return apply(ctx, key, new Spec(increase ? InventoryMovementType.ADJUSTMENT_IN : InventoryMovementType.ADJUSTMENT_OUT,
                itemId, quantity, increase ? value : null, date(date), null, InventorySourceType.MANUAL, null, null,
                null, null, null, notes));
    }

    @Transactional
    public Result reverse(BusinessAccessContext ctx, String key, UUID movementId, LocalDate date, String reason) {
        if (reason == null || reason.isBlank()) {
            throw DomainException.invalid("reason", "required");
        }
        InventoryMovementRecord original = repository.movement(ctx.businessId(), movementId)
                .orElseThrow(() -> DomainException.notFound("Inventory movement"));
        if (original.movementType() == InventoryMovementType.REVERSAL) {
            throw DomainException.conflict("reversal_not_reversible", "A reversal cannot itself be reversed.");
        }
        return apply(ctx, key, new Spec(InventoryMovementType.REVERSAL, original.itemId(), original.quantityDelta().abs(),
                original.valueDelta().abs(), date(date), original.purpose(), InventorySourceType.MOVEMENT, original.id(),
                null, original.projectId(), original.sampleId(), original.id(), reason));
    }

    /** Capitalizes every inventory-linked line of a finalized supplier bill into stock (one receipt per line). */
    @Transactional
    public List<Result> receiveBill(BusinessAccessContext ctx, String key, UUID billId, LocalDate date) {
        Map<String, Object> bill = lockDocument(ctx.businessId(), billId, "BILL");
        LocalDate effective = date != null ? date : (LocalDate) toLocalDate(bill.get("document_date"));
        List<Map<String, Object>> lines = itemLines(ctx.businessId(), billId);
        if (lines.isEmpty()) {
            throw DomainException.conflict("no_inventory_lines", "This bill has no lines linked to inventory items.");
        }
        List<Result> results = new ArrayList<>();
        for (Map<String, Object> line : lines) {
            UUID lineId = (UUID) line.get("id");
            String lineKey = key + ":" + lineId;
            Optional<InventoryMovementRecord> replay = repository.byIdempotencyKey(ctx.businessId(), lineKey);
            if (replay.isEmpty() && repository.hasEffectiveLineMovement(ctx.businessId(), InventorySourceType.SUPPLIER_BILL, lineId)) {
                continue; // already received under another key; never received twice
            }
            BigDecimal value = ((BigDecimal) line.get("line_amount")).setScale(VALUE_SCALE, RoundingMode.UNNECESSARY);
            results.add(apply(ctx, lineKey, new Spec(InventoryMovementType.RECEIPT, (UUID) line.get("inventory_item_id"),
                    (BigDecimal) line.get("quantity"), value, date(effective), null, InventorySourceType.SUPPLIER_BILL,
                    billId, lineId, null, null, null, "Received from " + bill.get("document_number"))));
        }
        if (results.isEmpty()) {
            throw DomainException.conflict("already_received", "Every inventory line of this bill is already in stock.");
        }
        return results;
    }

    /** Issues stock (purpose SALE, i.e. COGS) for every inventory-linked line of a finalized customer invoice. */
    @Transactional
    public List<Result> fulfilInvoice(BusinessAccessContext ctx, String key, UUID invoiceId, LocalDate date, UUID projectId) {
        Map<String, Object> invoice = lockDocument(ctx.businessId(), invoiceId, "INVOICE");
        LocalDate effective = date != null ? date : (LocalDate) toLocalDate(invoice.get("document_date"));
        List<Map<String, Object>> lines = itemLines(ctx.businessId(), invoiceId);
        if (lines.isEmpty()) {
            throw DomainException.conflict("no_inventory_lines", "This invoice has no lines linked to inventory items.");
        }
        if (projectId != null) {
            requireProject(ctx.businessId(), projectId);
        }
        List<Result> results = new ArrayList<>();
        for (Map<String, Object> line : lines) {
            UUID lineId = (UUID) line.get("id");
            String lineKey = key + ":" + lineId;
            Optional<InventoryMovementRecord> replay = repository.byIdempotencyKey(ctx.businessId(), lineKey);
            if (replay.isEmpty() && repository.hasEffectiveLineMovement(ctx.businessId(), InventorySourceType.CUSTOMER_INVOICE, lineId)) {
                continue;
            }
            results.add(apply(ctx, lineKey, new Spec(InventoryMovementType.ISSUE, (UUID) line.get("inventory_item_id"),
                    (BigDecimal) line.get("quantity"), null, date(effective), InventoryPurpose.SALE,
                    InventorySourceType.CUSTOMER_INVOICE, invoiceId, lineId, projectId, null, null,
                    "Fulfilled for " + invoice.get("document_number"))));
        }
        if (results.isEmpty()) {
            throw DomainException.conflict("already_fulfilled", "Every inventory line of this invoice is already fulfilled.");
        }
        return results;
    }

    // ---------------------------------------------------------------- core

    Result apply(BusinessAccessContext ctx, String key, Spec spec) {
        UUID businessId = ctx.businessId();
        if (key == null || key.isBlank() || key.length() > 120) {
            throw DomainException.invalid("Idempotency-Key", "required");
        }
        if (spec.itemId() == null) {
            throw DomainException.invalid("itemId", "required");
        }
        if (spec.quantity() == null || spec.quantity().signum() <= 0 || spec.quantity().scale() > 6
                && spec.quantity().stripTrailingZeros().scale() > 6) {
            throw DomainException.invalid("quantity", "quantity_invalid");
        }
        if (spec.notes() != null && spec.notes().length() > 500) {
            throw DomainException.invalid("notes", "too_long");
        }
        String hash = Hashing.sha256(businessId, spec.type(), spec.itemId(), spec.quantity().stripTrailingZeros().toPlainString(),
                spec.value() == null ? null : spec.value().stripTrailingZeros().toPlainString(), spec.date(), spec.purpose(),
                spec.sourceType(), spec.sourceId(), spec.sourceLineId(), spec.projectId(), spec.sampleId(),
                spec.relatedMovementId());

        Optional<InventoryMovementRecord> existing = repository.byIdempotencyKey(businessId, key);
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }

        if (!repository.lockItem(businessId, spec.itemId())) {
            throw DomainException.notFound("Inventory item");
        }
        // Re-check after acquiring the item lock: a concurrent request with the same key may have just committed.
        existing = repository.byIdempotencyKey(businessId, key);
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }

        String itemStatus = jdbc.queryForObject("SELECT status FROM inventory_items WHERE business_id = ? AND id = ?",
                String.class, businessId, spec.itemId());
        if (!"ACTIVE".equals(itemStatus) && spec.type() != InventoryMovementType.REVERSAL) {
            throw DomainException.conflict("item_archived", "Archived items cannot receive new stock movements.");
        }

        Optional<InventoryPositionRecord> position = repository.position(businessId, spec.itemId());
        BigDecimal q = position.map(InventoryPositionRecord::quantity).orElse(BigDecimal.ZERO.setScale(6));
        BigDecimal v = position.map(InventoryPositionRecord::value).orElse(BigDecimal.ZERO.setScale(VALUE_SCALE));
        long sequence = position.map(p -> p.lastSequenceNo() + 1).orElse(1L);

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        if (spec.date().isAfter(today)) {
            throw DomainException.invalid("movementDate", "future_date");
        }
        if (position.isPresent() && spec.date().isBefore(position.get().lastMovementDate())) {
            throw DomainException.conflict("backdated_movement",
                    "Movements apply in order; the date cannot be before the item's last movement ("
                            + position.get().lastMovementDate() + ").");
        }

        BigDecimal quantity = spec.quantity().setScale(6, RoundingMode.UNNECESSARY);
        BigDecimal qDelta;
        BigDecimal vDelta;
        switch (spec.type()) {
            case OPENING -> {
                if (position.isPresent()) {
                    throw DomainException.conflict("opening_after_movements", "Opening stock must be the item's first movement.");
                }
                qDelta = quantity;
                vDelta = scaled(spec.value());
            }
            case RECEIPT, ADJUSTMENT_IN -> {
                validateInboundSource(businessId, spec);
                qDelta = quantity;
                vDelta = scaled(spec.value());
            }
            case ISSUE, SUPPLIER_RETURN, ADJUSTMENT_OUT -> {
                validateOutboundTarget(businessId, spec);
                BigDecimal out = outboundValue(q, v, quantity);
                qDelta = quantity.negate();
                vDelta = out.negate();
            }
            case CUSTOMER_RETURN -> {
                InventoryMovementRecord issue = repository.movement(businessId, spec.relatedMovementId())
                        .orElseThrow(() -> DomainException.notFound("Issue movement"));
                if (repository.isReversed(businessId, issue.id())) {
                    throw DomainException.conflict("issue_reversed", "A reversed issue cannot receive returns.");
                }
                BigDecimal issuedQty = issue.quantityDelta().negate();
                BigDecimal issuedValue = issue.valueDelta().negate();
                BigDecimal[] returned = repository.effectiveReturns(businessId, issue.id());
                BigDecimal remainingQty = issuedQty.subtract(returned[0]);
                BigDecimal remainingValue = issuedValue.subtract(returned[1]);
                int cmp = quantity.compareTo(remainingQty);
                if (cmp > 0) {
                    throw DomainException.conflict("return_exceeds_issue",
                            "Only " + remainingQty.stripTrailingZeros().toPlainString() + " can still be returned against this issue.");
                }
                BigDecimal value = cmp == 0 ? remainingValue
                        : issuedValue.multiply(quantity).divide(issuedQty, VALUE_SCALE, VALUE_ROUNDING).min(remainingValue);
                qDelta = quantity;
                vDelta = scaled(value);
            }
            case REVERSAL -> {
                InventoryMovementRecord original = repository.movement(businessId, spec.relatedMovementId())
                        .orElseThrow(() -> DomainException.notFound("Inventory movement"));
                if (repository.isReversed(businessId, original.id())) {
                    throw DomainException.conflict("already_reversed", "This movement has already been reversed.");
                }
                if (original.movementType() == InventoryMovementType.ISSUE) {
                    BigDecimal[] returned = repository.effectiveReturns(businessId, original.id());
                    if (returned[0].signum() != 0) {
                        throw DomainException.conflict("issue_has_returns", "Reverse the returns recorded against this issue first.");
                    }
                }
                qDelta = original.quantityDelta().negate();
                vDelta = original.valueDelta().negate();
                if (q.add(qDelta).signum() < 0 || v.add(vDelta).signum() < 0
                        || (q.add(qDelta).signum() == 0 && v.add(vDelta).signum() != 0)) {
                    throw DomainException.conflict("reversal_blocked_by_later_movements",
                            "Later movements consumed this stock; record an adjustment instead of a reversal.");
                }
            }
            default -> throw new IllegalStateException("Unsupported movement type " + spec.type());
        }

        BigDecimal qAfter = q.add(qDelta);
        BigDecimal vAfter = v.add(vDelta);
        if (qAfter.signum() < 0) {
            throw DomainException.conflict("insufficient_stock", "Insufficient stock for this movement.");
        }
        if (qAfter.signum() == 0 && vAfter.signum() != 0 || vAfter.signum() < 0) {
            throw new IllegalStateException("Inventory conservation violated");
        }

        InventoryMovementRecord movement = new InventoryMovementRecord(UUID.randomUUID(), businessId, spec.itemId(),
                sequence, spec.type(), spec.purpose(), qDelta, vDelta, qAfter, vAfter, spec.date(), spec.sourceType(),
                spec.sourceId(), spec.sourceLineId(), spec.projectId(), spec.sampleId(), spec.relatedMovementId(), key,
                hash, POLICY_VERSION, blankToNull(spec.notes()), ctx.userId(), OffsetDateTime.now(ZoneOffset.UTC));
        try {
            repository.insertMovement(movement);
        } catch (DuplicateKeyException race) {
            throw DomainException.conflict("concurrent_movement", "A concurrent movement was recorded; retry the request.");
        }
        repository.upsertPosition(businessId, spec.itemId(), qAfter, vAfter, sequence, spec.date(), position.isPresent());
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("itemId", spec.itemId().toString());
        meta.put("movementType", spec.type().name());
        meta.put("quantityDelta", qDelta.stripTrailingZeros().toPlainString());
        meta.put("valueDelta", vDelta.toPlainString());
        meta.put("policyVersion", POLICY_VERSION);
        if (spec.relatedMovementId() != null) {
            meta.put("relatedMovementId", spec.relatedMovementId().toString());
        }
        audit.record(ctx, spec.type() == InventoryMovementType.REVERSAL ? AuditEventType.INVENTORY_MOVEMENT_REVERSED
                : AuditEventType.INVENTORY_MOVEMENT_RECORDED, "INVENTORY_MOVEMENT", movement.id(), meta);
        return new Result(movement, false);
    }

    /** Policy f08-wac-v1 outbound valuation. */
    public static BigDecimal outboundValue(BigDecimal quantityOnHand, BigDecimal valueOnHand, BigDecimal quantity) {
        int cmp = quantity.compareTo(quantityOnHand);
        if (cmp > 0) {
            throw DomainException.conflict("insufficient_stock", "Insufficient stock: only "
                    + quantityOnHand.stripTrailingZeros().toPlainString() + " on hand.");
        }
        if (cmp == 0) {
            return valueOnHand.setScale(VALUE_SCALE, RoundingMode.UNNECESSARY);
        }
        return valueOnHand.multiply(quantity).divide(quantityOnHand, VALUE_SCALE, VALUE_ROUNDING);
    }

    private Result replay(InventoryMovementRecord existing, String hash) {
        if (!existing.payloadHash().equals(hash)) {
            throw DomainException.conflict("idempotency_key_reused", "This Idempotency-Key was used for a different movement.");
        }
        return new Result(existing, true);
    }

    private void validateInboundSource(UUID businessId, Spec spec) {
        if (spec.sourceType() != InventorySourceType.TRANSACTION) {
            return;
        }
        // Cash purchase capitalization: the ledger payment must be an un-reversed operating payment with capacity.
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT t.event_type, t.base_amount_pkr,
                       EXISTS (SELECT 1 FROM financial_transactions r WHERE r.business_id = t.business_id
                               AND r.reversed_transaction_id = t.id) AS reversed,
                       t.reversed_transaction_id IS NOT NULL AS is_reversal
                FROM financial_transactions t WHERE t.business_id = ? AND t.id = ?
                """, businessId, spec.sourceId());
        if (rows.isEmpty()) {
            throw DomainException.notFound("Ledger transaction");
        }
        Map<String, Object> tx = rows.get(0);
        if (!"OPERATING_EXPENSE_PAYMENT".equals(tx.get("event_type")) || Boolean.TRUE.equals(tx.get("reversed"))
                || Boolean.TRUE.equals(tx.get("is_reversal"))) {
            throw DomainException.conflict("transaction_not_capitalizable",
                    "Only an un-reversed operating payment can be capitalized into inventory.");
        }
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))", "ledger-tx:" + spec.sourceId());
        BigDecimal otherClaims = jdbc.queryForObject("""
                SELECT COALESCE((SELECT sum(amount) FROM sample_actual_costs WHERE business_id = ? AND transaction_id = ?
                                 AND status = 'EFFECTIVE'), 0)
                     + COALESCE((SELECT sum(base_amount) FROM project_financial_attributions WHERE business_id = ?
                                 AND source_type = 'TRANSACTION' AND source_id = ? AND status = 'ACTIVE'), 0)
                """, BigDecimal.class, businessId, spec.sourceId(), businessId, spec.sourceId());
        BigDecimal capacity = ((BigDecimal) tx.get("base_amount_pkr"))
                .subtract(repository.effectiveCapitalizedFromTransaction(businessId, spec.sourceId()))
                .subtract(otherClaims == null ? BigDecimal.ZERO : otherClaims);
        if (spec.value().compareTo(capacity) > 0) {
            throw DomainException.conflict("capitalization_exceeds_payment",
                    "The receipt value exceeds the uncapitalized amount of the payment (" + capacity.stripTrailingZeros().toPlainString() + ").");
        }
    }

    private void validateOutboundTarget(UUID businessId, Spec spec) {
        if (spec.projectId() != null) {
            requireProject(businessId, spec.projectId());
        }
        if (spec.sampleId() != null) {
            String status = jdbc.query("SELECT status FROM sample_activities WHERE business_id = ? AND id = ?",
                    (rs, n) -> rs.getString(1), businessId, spec.sampleId()).stream().findFirst()
                    .orElseThrow(() -> DomainException.notFound("Sample"));
            if ("CANCELLED".equals(status) || "ARCHIVED".equals(status)) {
                throw DomainException.conflict("sample_closed", "Stock cannot be issued to a cancelled or archived sample.");
            }
        }
        if (spec.sourceType() == InventorySourceType.SUPPLIER_BILL && spec.sourceId() != null) {
            lockDocument(businessId, spec.sourceId(), "BILL");
        }
    }

    private void requireProject(UUID businessId, UUID projectId) {
        String status = jdbc.query("SELECT status FROM projects WHERE business_id = ? AND id = ?",
                (rs, n) -> rs.getString(1), businessId, projectId).stream().findFirst()
                .orElseThrow(() -> DomainException.notFound("Project"));
        if ("CANCELLED".equals(status) || "ARCHIVED".equals(status)) {
            throw DomainException.conflict("project_closed", "Stock cannot be issued to a cancelled or archived project.");
        }
    }

    private Map<String, Object> lockDocument(UUID businessId, UUID documentId, String kind) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, kind, status, document_number, document_date "
                + "FROM commercial_documents WHERE business_id = ? AND id = ? FOR UPDATE", businessId, documentId);
        if (rows.isEmpty() || !kind.equals(rows.get(0).get("kind"))) {
            throw DomainException.notFound(kind.equals("BILL") ? "Bill" : "Invoice");
        }
        if (!"FINALIZED".equals(rows.get(0).get("status"))) {
            throw DomainException.conflict("document_not_finalized", "Only finalized, non-void documents can move stock.");
        }
        return rows.get(0);
    }

    private List<Map<String, Object>> itemLines(UUID businessId, UUID documentId) {
        return jdbc.queryForList("SELECT id, inventory_item_id, quantity, line_amount FROM commercial_document_lines "
                + "WHERE business_id = ? AND document_id = ? AND inventory_item_id IS NOT NULL ORDER BY line_number",
                businessId, documentId);
    }

    private static Object toLocalDate(Object value) {
        if (value instanceof java.sql.Date sql) {
            return sql.toLocalDate();
        }
        return value;
    }

    private static void requireValue(BigDecimal value) {
        if (value == null) {
            throw DomainException.invalid("value", "required");
        }
        if (value.signum() < 0) {
            throw DomainException.invalid("value", "value_invalid");
        }
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(VALUE_SCALE, RoundingMode.UNNECESSARY);
    }

    private static LocalDate date(LocalDate date) {
        return date != null ? date : LocalDate.now(BUSINESS_ZONE);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
