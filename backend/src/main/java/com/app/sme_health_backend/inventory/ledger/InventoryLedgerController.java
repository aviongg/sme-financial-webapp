package com.app.sme_health_backend.inventory.ledger;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.productsupport.DomainException;
import com.app.sme_health_backend.productsupport.Money;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * F08 authoritative perpetual inventory API. Stock writes move cost (capitalization, COGS, project/sample
 * material) so they require FINANCIAL_TRANSACTION_POST (OWNER, ACCOUNTANT); reads require FINANCIAL_DATA_READ.
 * Every write requires an Idempotency-Key header.
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryLedgerController {
    private final BusinessAuthorizationService authorization;
    private final InventoryLedgerService ledger;
    private final InventoryReadService reads;
    private final InventoryLedgerRepository repository;

    public InventoryLedgerController(BusinessAuthorizationService authorization, InventoryLedgerService ledger,
                                     InventoryReadService reads, InventoryLedgerRepository repository) {
        this.authorization = authorization;
        this.ledger = ledger;
        this.reads = reads;
        this.repository = repository;
    }

    public record MovementBody(
            UUID itemId, String quantity, String value, String movementDate, String purpose, UUID projectId,
            UUID sampleId, UUID transactionId, UUID billId, UUID issueMovementId, String direction, String notes,
            String reason
    ) { }

    public record DocumentStockBody(String movementDate, UUID projectId) { }

    @GetMapping("/register")
    public ResponseEntity<?> register(HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        InventoryReadService.Register register = reads.register(ctx.businessId());
        return ok(Map.of(
                "items", register.items().stream().map(InventoryLedgerController::position).toList(),
                "totalValue", Money.str(register.totalValue()),
                "itemsBelowReorder", register.itemsBelowReorder(),
                "itemsWithStock", register.itemsWithStock(),
                "policyVersion", register.policyVersion(),
                "authoritative", true));
    }

    @GetMapping("/items/{id}/movements")
    public ResponseEntity<?> history(@PathVariable("id") UUID itemId,
                                     @RequestParam(value = "limit", defaultValue = "200") int limit,
                                     HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        List<InventoryMovementRecord> rows = repository.history(ctx.businessId(), itemId, Math.max(1, limit));
        return ok(Map.of("movements", rows.stream().map(m -> movement(m, repository.isReversed(ctx.businessId(), m.id()))).toList()));
    }

    @GetMapping("/movements")
    public ResponseEntity<?> movements(@RequestParam(value = "from", required = false) String from,
                                       @RequestParam(value = "to", required = false) String to,
                                       HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        LocalDate end = to == null ? today() : parseDate(to, "to");
        LocalDate start = from == null ? end.minusDays(90) : parseDate(from, "from");
        rangeCheck(start, end);
        List<InventoryMovementRecord> rows = repository.movements(ctx.businessId(), start, end, 500);
        return ok(Map.of("from", start.toString(), "to", end.toString(),
                "movements", rows.stream().map(m -> movement(m, repository.isReversed(ctx.businessId(), m.id()))).toList()));
    }

    @GetMapping("/valuation")
    public ResponseEntity<?> valuation(@RequestParam(value = "asOf", required = false) String asOf, HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        LocalDate date = asOf == null ? today() : parseDate(asOf, "asOf");
        return ok(Map.of("asOf", date.toString(), "carryingValue", Money.str(reads.valueAsOf(ctx.businessId(), date)),
                "policyVersion", InventoryLedgerService.POLICY_VERSION));
    }

    @GetMapping("/cogs")
    public ResponseEntity<?> cogs(@RequestParam(value = "from", required = false) String from,
                                  @RequestParam(value = "to", required = false) String to, HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        LocalDate end = to == null ? today() : parseDate(to, "to");
        LocalDate start = from == null ? end.withDayOfMonth(1) : parseDate(from, "from");
        rangeCheck(start, end);
        InventoryReadService.ConsumptionSummary s = reads.consumption(ctx.businessId(), start, end);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", s.from().toString());
        body.put("to", s.to().toString());
        body.put("costOfGoodsSold", Money.str(s.costOfGoodsSold()));
        body.put("projectMaterial", Money.str(s.projectMaterial()));
        body.put("sampleMaterial", Money.str(s.sampleMaterial()));
        body.put("internalConsumption", Money.str(s.internalConsumption()));
        body.put("wastage", Money.str(s.wastage()));
        body.put("adjustmentLoss", Money.str(s.adjustmentLoss()));
        body.put("soldQuantity", s.soldQuantity().stripTrailingZeros().toPlainString());
        body.put("policyVersion", s.policyVersion());
        return ok(body);
    }

    @PostMapping("/movements/opening")
    public ResponseEntity<?> opening(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                     @RequestBody MovementBody body, HttpServletRequest request) {
        BusinessAccessContext ctx = write(request);
        return created(ledger.opening(ctx, key, body.itemId(), Money.quantity(body.quantity(), "quantity"),
                Money.amount(body.value(), "value", true), date(body.movementDate()), body.notes()));
    }

    @PostMapping("/movements/receipts")
    public ResponseEntity<?> receipt(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                     @RequestBody MovementBody body, HttpServletRequest request) {
        BusinessAccessContext ctx = write(request);
        return created(ledger.receipt(ctx, key, body.itemId(), Money.quantity(body.quantity(), "quantity"),
                Money.amount(body.value(), "value", true), date(body.movementDate()), body.transactionId(), body.notes()));
    }

    @PostMapping("/movements/issues")
    public ResponseEntity<?> issue(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                   @RequestBody MovementBody body, HttpServletRequest request) {
        BusinessAccessContext ctx = write(request);
        rejectValue(body);
        return created(ledger.issue(ctx, key, body.itemId(), Money.quantity(body.quantity(), "quantity"),
                date(body.movementDate()), purpose(body.purpose()), body.projectId(), body.sampleId(), body.notes()));
    }

    @PostMapping("/movements/supplier-returns")
    public ResponseEntity<?> supplierReturn(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                            @RequestBody MovementBody body, HttpServletRequest request) {
        BusinessAccessContext ctx = write(request);
        rejectValue(body);
        return created(ledger.supplierReturn(ctx, key, body.itemId(), Money.quantity(body.quantity(), "quantity"),
                date(body.movementDate()), body.billId(), body.notes()));
    }

    @PostMapping("/movements/returns")
    public ResponseEntity<?> returnToStock(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                           @RequestBody MovementBody body, HttpServletRequest request) {
        BusinessAccessContext ctx = write(request);
        rejectValue(body);
        if (body.issueMovementId() == null) {
            throw DomainException.invalid("issueMovementId", "required");
        }
        return created(ledger.returnToStock(ctx, key, body.issueMovementId(), Money.quantity(body.quantity(), "quantity"),
                date(body.movementDate()), body.notes()));
    }

    @PostMapping("/movements/adjustments")
    public ResponseEntity<?> adjustment(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                        @RequestBody MovementBody body, HttpServletRequest request) {
        BusinessAccessContext ctx = write(request);
        boolean increase;
        if ("INCREASE".equals(body.direction())) {
            increase = true;
        } else if ("DECREASE".equals(body.direction())) {
            increase = false;
        } else {
            throw DomainException.invalid("direction", "enum_invalid");
        }
        BigDecimal value = increase ? Money.amount(body.value(), "value", true) : null;
        if (!increase && body.value() != null) {
            throw DomainException.invalid("value", "not_allowed");
        }
        return created(ledger.adjust(ctx, key, body.itemId(), increase, Money.quantity(body.quantity(), "quantity"),
                value, date(body.movementDate()), body.notes()));
    }

    @PostMapping("/movements/{id}/reverse")
    public ResponseEntity<?> reverse(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                     @PathVariable("id") UUID movementId, @RequestBody MovementBody body,
                                     HttpServletRequest request) {
        BusinessAccessContext ctx = write(request);
        return created(ledger.reverse(ctx, key, movementId, date(body.movementDate()), body.reason()));
    }

    @PostMapping("/documents/bills/{id}/receive")
    public ResponseEntity<?> receiveBill(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                         @PathVariable("id") UUID billId, @RequestBody(required = false) DocumentStockBody body,
                                         HttpServletRequest request) {
        BusinessAccessContext ctx = write(request);
        requireKey(key);
        List<InventoryLedgerService.Result> results = ledger.receiveBill(ctx, key, billId,
                body == null ? null : date(body.movementDate()));
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store")
                .body(Map.of("movements", results.stream().map(r -> movement(r.movement(), false)).toList()));
    }

    @PostMapping("/documents/invoices/{id}/fulfil")
    public ResponseEntity<?> fulfilInvoice(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                           @PathVariable("id") UUID invoiceId, @RequestBody(required = false) DocumentStockBody body,
                                           HttpServletRequest request) {
        BusinessAccessContext ctx = write(request);
        requireKey(key);
        List<InventoryLedgerService.Result> results = ledger.fulfilInvoice(ctx, key, invoiceId,
                body == null ? null : date(body.movementDate()), body == null ? null : body.projectId());
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store")
                .body(Map.of("movements", results.stream().map(r -> movement(r.movement(), false)).toList()));
    }

    @GetMapping("/documents/{id}/movements")
    public ResponseEntity<?> documentMovements(@PathVariable("id") UUID documentId, HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        List<InventoryMovementRecord> rows = new java.util.ArrayList<>();
        rows.addAll(repository.bySource(ctx.businessId(), InventorySourceType.SUPPLIER_BILL, documentId));
        rows.addAll(repository.bySource(ctx.businessId(), InventorySourceType.CUSTOMER_INVOICE, documentId));
        return ok(Map.of("movements", rows.stream().map(m -> movement(m, repository.isReversed(ctx.businessId(), m.id()))).toList()));
    }

    // ---------------------------------------------------------------- mapping

    static Map<String, Object> movement(InventoryMovementRecord m, boolean reversed) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", m.id());
        body.put("itemId", m.itemId());
        body.put("sequenceNo", m.sequenceNo());
        body.put("movementType", m.movementType().name());
        body.put("purpose", m.purpose() == null ? null : m.purpose().name());
        body.put("quantityDelta", m.quantityDelta().stripTrailingZeros().toPlainString());
        body.put("valueDelta", Money.str(m.valueDelta()));
        body.put("quantityAfter", m.quantityAfter().stripTrailingZeros().toPlainString());
        body.put("valueAfter", Money.str(m.valueAfter()));
        body.put("movementDate", m.movementDate().toString());
        body.put("sourceType", m.sourceType().name());
        body.put("sourceId", m.sourceId());
        body.put("sourceLineId", m.sourceLineId());
        body.put("projectId", m.projectId());
        body.put("sampleId", m.sampleId());
        body.put("relatedMovementId", m.relatedMovementId());
        body.put("reversed", reversed);
        body.put("policyVersion", m.policyVersion());
        body.put("notes", m.notes());
        body.put("createdBy", m.createdBy());
        body.put("createdAt", m.createdAt() == null ? null : m.createdAt().toString());
        return body;
    }

    private static Map<String, Object> position(InventoryReadService.PositionView p) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("itemId", p.itemId());
        body.put("sku", p.sku());
        body.put("name", p.name());
        body.put("unit", p.unit());
        body.put("itemKind", p.itemKind());
        body.put("status", p.status());
        body.put("quantity", p.quantity().stripTrailingZeros().toPlainString());
        body.put("value", Money.str(p.value()));
        body.put("averageUnitCost", p.averageUnitCost() == null ? null : p.averageUnitCost().stripTrailingZeros().toPlainString());
        body.put("reorderLevel", p.reorderLevel() == null ? null : p.reorderLevel().stripTrailingZeros().toPlainString());
        body.put("belowReorder", p.belowReorder());
        body.put("lastMovementDate", p.lastMovementDate() == null ? null : p.lastMovementDate().toString());
        body.put("hasMovements", p.hasMovements());
        return body;
    }

    private BusinessAccessContext write(HttpServletRequest request) {
        return authorization.requirePermission(request, BusinessPermission.FINANCIAL_TRANSACTION_POST);
    }

    private static ResponseEntity<?> created(InventoryLedgerService.Result result) {
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Cache-Control", "no-store")
                .body(movement(result.movement(), false));
    }

    private static ResponseEntity<?> ok(Object body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(body);
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw DomainException.invalid("Idempotency-Key", "required");
        }
    }

    private static void rejectValue(MovementBody body) {
        if (body.value() != null) {
            throw DomainException.invalid("value", "not_allowed");
        }
    }

    private static InventoryPurpose purpose(String raw) {
        if (raw == null) {
            throw DomainException.invalid("purpose", "required");
        }
        try {
            return InventoryPurpose.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw DomainException.invalid("purpose", "enum_invalid");
        }
    }

    private static LocalDate date(String raw) {
        return raw == null || raw.isBlank() ? null : parseDate(raw, "movementDate");
    }

    static LocalDate parseDate(String raw, String field) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw DomainException.invalid(field, "date_invalid");
        }
    }

    private static void rangeCheck(LocalDate start, LocalDate end) {
        if (start.isAfter(end) || start.plusYears(5).isBefore(end)) {
            throw DomainException.invalid("from", "range_invalid");
        }
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Karachi"));
    }
}
