package com.app.sme_health_backend.records.derived;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.productsupport.AuditRecorder;
import com.app.sme_health_backend.productsupport.DomainException;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Transaction-driven monthly model API: projection status, explicit recalculation, field source map, and the only
 * remaining manual monthly inputs (supplemental financing facts that have no ledger authority).
 */
@RestController
@RequestMapping("/api/records/monthly")
public class MonthlyProjectionController {

    private static final Set<String> FINANCING_TYPES = Set.of("none", "conventional", "islamic");

    private final BusinessAuthorizationService auth;
    private final MonthlyProjectionService projections;
    private final MonthlyRecordRepository records;
    private final JdbcTemplate jdbc;
    private final AuditRecorder audit;

    public MonthlyProjectionController(BusinessAuthorizationService auth, MonthlyProjectionService projections,
                                       MonthlyRecordRepository records, JdbcTemplate jdbc, AuditRecorder audit) {
        this.auth = auth;
        this.projections = projections;
        this.records = records;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @GetMapping("/projection-status")
    public Map<String, Object> status(HttpServletRequest request) {
        BusinessAccessContext ctx = auth.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        MonthlyProjectionService.Status status = projections.status(ctx.businessId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectionVersion", MonthlyFinancialProjector.VERSION);
        body.put("dirty", status.dirty());
        body.put("stale", status.stale());
        body.put("projectedThrough", status.projectedThrough());
        body.put("currentMonth", status.currentMonth());
        body.put("lastProjectedAt", status.lastProjectedAt());
        body.put("lastError", status.lastError());
        body.put("principle", "Monthly financial health is derived automatically from authoritative daily activity.");
        return body;
    }

    /** Forces a full deterministic rebuild (idempotent: unchanged months are not rewritten or rescored). */
    @PostMapping("/recalculate")
    public MonthlyProjectionService.RefreshResult recalculate(HttpServletRequest request) {
        BusinessAccessContext ctx = auth.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        MonthlyProjectionService.RefreshResult result = projections.refresh(ctx.businessId());
        audit.record(ctx, AuditEventType.MONTHLY_PROJECTION_RECALCULATED, "monthly_projection", ctx.businessId(),
                Map.of("changedMonths", String.join(",", result.changedMonths()),
                        "projectionVersion", MonthlyFinancialProjector.VERSION));
        return result;
    }

    /** Field-by-field source map for one month, with the stored projection alongside the live derivation. */
    @GetMapping("/{month:[0-9]{4}-[0-9]{2}}/sources")
    public Map<String, Object> sources(@PathVariable String month, HttpServletRequest request) {
        BusinessAccessContext ctx = auth.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        YearMonth ym = parseMonth(month);
        MonthlyFinancialProjector.DerivedMonth derived = projections.derive(ctx.businessId(), ym);
        MonthlyRecord stored = records.findByUserIdAndMonth(ctx.businessId(), month).orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("month", month);
        body.put("projectionVersion", MonthlyFinancialProjector.VERSION);
        body.put("periodComplete", derived.periodComplete());
        body.put("fields", derived.sources());
        body.put("financingType", derived.financingType());
        body.put("notes", derived.notes());
        body.put("storedSource", stored == null ? null : stored.getSource());
        body.put("storedMatchesDerivation", stored != null && stored.isDerived()
                && derived.fingerprint().equals(stored.getSourceFingerprint()));
        Map<String, Object> pnl = new LinkedHashMap<>();
        pnl.put("cashSales", derived.statement().cashSales());
        pnl.put("invoiceRevenue", derived.statement().invoiceRevenue());
        pnl.put("creditNotes", derived.statement().creditNotes());
        pnl.put("costOfSales", derived.statement().costOfSales());
        pnl.put("operatingExpenses", derived.statement().operatingExpenses());
        pnl.put("realizedFx", derived.statement().realizedFx());
        pnl.put("netProfit", derived.statement().netProfit());
        pnl.put("basis", "pnl-accrual-v1");
        body.put("profitAndLoss", pnl);
        return body;
    }

    @GetMapping("/financing")
    public List<Map<String, Object>> financing(HttpServletRequest request) {
        BusinessAccessContext ctx = auth.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        return jdbc.queryForList("""
                SELECT month, loan_outstanding AS "loanOutstanding", interest_expense AS "interestExpense",
                       financing_type AS "financingType", origin, version, updated_at AS "updatedAt"
                FROM monthly_financing_inputs WHERE business_id = ? ORDER BY month DESC
                """, ctx.businessId());
    }

    public record FinancingInput(String loanOutstanding, String interestExpense, String financingType, Long expectedVersion) { }

    /** Supplemental, clearly manual inputs: the ledger has no authority for loan balances or interest yet. */
    @PutMapping("/financing/{month:[0-9]{4}-[0-9]{2}}")
    @Transactional
    public Map<String, Object> putFinancing(@PathVariable String month, @RequestBody FinancingInput input,
                                            HttpServletRequest request) {
        BusinessAccessContext ctx = auth.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        parseMonth(month);
        if (input == null) throw DomainException.invalid("body", "required");
        BigDecimal loan = optionalAmount(input.loanOutstanding(), "loanOutstanding");
        BigDecimal interest = optionalAmount(input.interestExpense(), "interestExpense");
        String type = input.financingType() == null || input.financingType().isBlank() ? "none"
                : input.financingType().trim().toLowerCase(java.util.Locale.ROOT);
        if (!FINANCING_TYPES.contains(type)) throw DomainException.invalid("financingType", "enum_invalid");

        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class,
                "monthly-financing:" + ctx.businessId() + ":" + month);
        List<Long> versions = jdbc.queryForList(
                "SELECT version FROM monthly_financing_inputs WHERE business_id = ? AND month = ? FOR UPDATE",
                Long.class, ctx.businessId(), month);
        if (versions.isEmpty()) {
            if (input.expectedVersion() != null) {
                throw DomainException.conflict("stale_financing_input", "This month changed. Reload before saving.");
            }
            jdbc.update("""
                    INSERT INTO monthly_financing_inputs (business_id, month, loan_outstanding, interest_expense,
                                                          financing_type, origin, updated_by, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'USER', ?, now())
                    """, ctx.businessId(), month, loan, interest, type, ctx.userId());
        } else {
            if (input.expectedVersion() == null || !input.expectedVersion().equals(versions.get(0))) {
                throw DomainException.conflict("stale_financing_input", "This month changed. Reload before saving.");
            }
            jdbc.update("""
                    UPDATE monthly_financing_inputs
                       SET loan_outstanding = ?, interest_expense = ?, financing_type = ?, origin = 'USER',
                           updated_by = ?, updated_at = now(), version = version + 1
                     WHERE business_id = ? AND month = ?
                    """, loan, interest, type, ctx.userId(), ctx.businessId(), month);
        }
        UUID id = jdbc.queryForObject("SELECT id FROM monthly_financing_inputs WHERE business_id = ? AND month = ?",
                UUID.class, ctx.businessId(), month);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("month", month);
        meta.put("financingType", type);
        audit.record(ctx, AuditEventType.MONTHLY_FINANCING_INPUT_UPDATED, "monthly_financing_input", id, meta);
        return jdbc.queryForMap("""
                SELECT month, loan_outstanding AS "loanOutstanding", interest_expense AS "interestExpense",
                       financing_type AS "financingType", origin, version, updated_at AS "updatedAt"
                FROM monthly_financing_inputs WHERE business_id = ? AND month = ?
                """, ctx.businessId(), month);
    }

    private static BigDecimal optionalAmount(String raw, String field) {
        if (raw == null || raw.isBlank()) return null;
        BigDecimal value;
        try {
            value = new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw DomainException.invalid(field, "number_invalid");
        }
        if (value.signum() < 0) throw DomainException.invalid(field, "negative");
        if (value.scale() > 2) throw DomainException.invalid(field, "scale_invalid");
        if (value.compareTo(new BigDecimal("999999999999.99")) > 0) throw DomainException.invalid(field, "too_large");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw DomainException.invalid("month", "format_invalid");
        }
    }
}
