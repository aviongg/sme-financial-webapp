package com.app.sme_health_backend.intelligence;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Profit &amp; loss, classifications, budgets, goals and break-even. Reads: FINANCIAL_DATA_READ. Plans (budgets,
 * goals): RECORD_CREATE_UPDATE. Classifications change financial analysis, so they need FINANCIAL_TRANSACTION_POST.
 */
@RestController
@RequestMapping("/api/planning")
public class PlanningController {
    private final BusinessAuthorizationService authorization;
    private final ProfitAndLossService pnl;
    private final ClassificationService classifications;
    private final PlanningService planning;
    private final BreakEvenService breakEven;

    public PlanningController(BusinessAuthorizationService authorization, ProfitAndLossService pnl,
                              ClassificationService classifications, PlanningService planning, BreakEvenService breakEven) {
        this.authorization = authorization;
        this.pnl = pnl;
        this.classifications = classifications;
        this.planning = planning;
        this.breakEven = breakEven;
    }

    public record ClassifyBody(String sourceKind, UUID sourceId, String category, String costBehavior) { }
    public record CategoryBehaviorBody(String category, String costBehavior) { }
    public record BudgetBody(String name, String periodStart, String periodEnd, UUID projectId,
                             List<PlanningService.LineInput> lines, String notes) { }
    public record TransitionBody(String targetStatus) { }
    public record GoalBody(String name, String metric, String targetValue, String periodStart, String periodEnd,
                           String notes, Long expectedVersion) { }

    // ------------------------------------------------------------- P&L

    @GetMapping("/profit-and-loss")
    public ResponseEntity<?> profitAndLoss(@RequestParam(value = "from", required = false) String from,
                                           @RequestParam(value = "to", required = false) String to, HttpServletRequest request) {
        BusinessAccessContext ctx = read(request);
        LocalDate[] range = range(from, to);
        return ok(statementBody(pnl.statement(ctx.businessId(), range[0], range[1])));
    }

    public static Map<String, Object> statementBody(ProfitAndLossService.Statement s) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("from", s.from().toString());
        b.put("to", s.to().toString());
        b.put("version", ProfitAndLossService.VERSION);
        b.put("cashSales", Money.str(s.cashSales()));
        b.put("invoiceRevenue", Money.str(s.invoiceRevenue()));
        b.put("creditNotes", Money.str(s.creditNotes()));
        b.put("revenue", Money.str(s.revenue()));
        b.put("costOfGoodsSold", Money.str(s.costOfGoodsSold()));
        b.put("projectMaterial", Money.str(s.projectMaterial()));
        b.put("sampleMaterial", Money.str(s.sampleMaterial()));
        b.put("costOfSales", Money.str(s.costOfSales()));
        b.put("grossProfit", Money.str(s.grossProfit()));
        b.put("grossMarginPct", s.grossMarginPct() == null ? null : s.grossMarginPct().toPlainString());
        b.put("cashOperatingExpenses", Money.str(s.cashOperatingExpenses()));
        b.put("billExpenses", Money.str(s.billExpenses()));
        b.put("debitNotes", Money.str(s.debitNotes()));
        b.put("supplierReturnValue", Money.str(s.supplierReturnValue()));
        b.put("commissionCost", Money.str(s.commissionCost()));
        b.put("inventoryLosses", Money.str(s.inventoryLosses()));
        b.put("operatingExpenses", Money.str(s.operatingExpenses()));
        b.put("operatingProfit", Money.str(s.operatingProfit()));
        b.put("operatingMarginPct", s.operatingMarginPct() == null ? null : s.operatingMarginPct().toPlainString());
        b.put("realizedFx", Money.str(s.realizedFx()));
        b.put("netProfit", Money.str(s.netProfit()));
        b.put("capitalizedPurchases", Money.str(s.capitalizedPurchases()));
        b.put("revenueByCategory", categories(ProfitAndLossService.byCategory(s.revenueFacts())));
        b.put("expensesByCategory", categories(ProfitAndLossService.byCategory(s.expenseFacts())));
        b.put("unclassifiedRevenue", Money.str(s.unclassifiedRevenue()));
        b.put("unclassifiedExpenses", Money.str(s.unclassifiedExpenses()));
        b.put("hasData", s.hasAnyData());
        b.put("completeness", !s.hasAnyData() ? "NO_DATA"
                : s.unclassifiedExpenses().signum() == 0 && s.unclassifiedRevenue().signum() == 0 ? "COMPLETE" : "PARTIAL_UNCLASSIFIED");
        return b;
    }

    private static List<Map<String, Object>> categories(Map<String, java.math.BigDecimal> totals) {
        return totals.entrySet().stream().map(e -> Map.<String, Object>of("category", e.getKey(), "amount", Money.str(e.getValue()))).toList();
    }

    // ------------------------------------------------------------- classifications

    @GetMapping("/classifications")
    public ResponseEntity<?> facts(@RequestParam(value = "from", required = false) String from,
                                   @RequestParam(value = "to", required = false) String to, HttpServletRequest request) {
        BusinessAccessContext ctx = read(request);
        LocalDate[] range = range(from, to);
        return ok(classifications.facts(ctx, range[0], range[1]));
    }

    @PutMapping("/classifications")
    public ResponseEntity<?> classify(@RequestBody ClassifyBody body, HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_TRANSACTION_POST);
        return ok(classifications.classify(ctx, body.sourceKind(), body.sourceId(), body.category(), body.costBehavior()));
    }

    @GetMapping("/category-behaviors")
    public ResponseEntity<?> categoryBehaviors(HttpServletRequest request) {
        return ok(classifications.categoryBehaviors(read(request)));
    }

    @PutMapping("/category-behaviors")
    public ResponseEntity<?> setCategoryBehavior(@RequestBody CategoryBehaviorBody body, HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_TRANSACTION_POST);
        return ok(classifications.setCategoryBehavior(ctx, body.category(), body.costBehavior()));
    }

    // ------------------------------------------------------------- budgets

    @GetMapping("/budgets")
    public ResponseEntity<?> budgets(HttpServletRequest request) {
        return ok(Map.of("budgets", planning.budgets(read(request))));
    }

    @GetMapping("/budgets/{id}")
    public ResponseEntity<?> budget(@PathVariable("id") UUID id, HttpServletRequest request) {
        return ok(planning.budget(read(request), id));
    }

    @PostMapping("/budgets")
    public ResponseEntity<?> createBudget(@RequestBody BudgetBody body, HttpServletRequest request) {
        BusinessAccessContext ctx = write(request);
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(planning.createBudget(ctx,
                body.name(), body.periodStart(), body.periodEnd(), body.projectId(), body.lines(), body.notes()));
    }

    @PutMapping("/budgets/{id}/draft")
    public ResponseEntity<?> reviseBudget(@PathVariable("id") UUID id, @RequestBody BudgetBody body, HttpServletRequest request) {
        return ok(planning.reviseBudget(write(request), id, body.lines(), body.notes()));
    }

    @PostMapping("/budgets/{id}/approve")
    public ResponseEntity<?> approveBudget(@PathVariable("id") UUID id, HttpServletRequest request) {
        return ok(planning.approveBudget(write(request), id));
    }

    @PostMapping("/budgets/{id}/transition")
    public ResponseEntity<?> transitionBudget(@PathVariable("id") UUID id, @RequestBody TransitionBody body,
                                              HttpServletRequest request) {
        return ok(planning.transitionBudget(write(request), id, body.targetStatus()));
    }

    // ------------------------------------------------------------- goals

    @GetMapping("/goals")
    public ResponseEntity<?> goals(HttpServletRequest request) {
        return ok(Map.of("goals", planning.goals(read(request))));
    }

    @PostMapping("/goals")
    public ResponseEntity<?> createGoal(@RequestBody GoalBody body, HttpServletRequest request) {
        BusinessAccessContext ctx = write(request);
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(planning.createGoal(ctx,
                body.name(), body.metric(), body.targetValue(), body.periodStart(), body.periodEnd(), body.notes()));
    }

    @PutMapping("/goals/{id}")
    public ResponseEntity<?> updateGoal(@PathVariable("id") UUID id, @RequestBody GoalBody body, HttpServletRequest request) {
        if (body.expectedVersion() == null) throw DomainException.invalid("expectedVersion", "required");
        return ok(planning.updateGoal(write(request), id, body.name(), body.targetValue(), body.notes(), body.expectedVersion()));
    }

    @PostMapping("/goals/{id}/transition")
    public ResponseEntity<?> transitionGoal(@PathVariable("id") UUID id, @RequestBody TransitionBody body, HttpServletRequest request) {
        return ok(planning.transitionGoal(write(request), id, body.targetStatus()));
    }

    // ------------------------------------------------------------- break-even

    @GetMapping("/break-even")
    public ResponseEntity<?> breakEven(@RequestParam(value = "from", required = false) String from,
                                       @RequestParam(value = "to", required = false) String to, HttpServletRequest request) {
        BusinessAccessContext ctx = read(request);
        LocalDate[] range = range(from, to);
        return ok(breakEven.analyse(ctx.businessId(), range[0], range[1]));
    }

    // ------------------------------------------------------------- helpers

    private BusinessAccessContext read(HttpServletRequest request) {
        return authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
    }

    private BusinessAccessContext write(HttpServletRequest request) {
        return authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
    }

    static LocalDate[] range(String from, String to) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Karachi"));
        LocalDate end = parse(to, "to", today);
        LocalDate start = parse(from, "from", end.withDayOfMonth(1));
        if (start.isAfter(end) || start.plusYears(5).isBefore(end)) {
            throw DomainException.invalid("from", "range_invalid");
        }
        return new LocalDate[] {start, end};
    }

    private static LocalDate parse(String raw, String field, LocalDate fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            throw DomainException.invalid(field, "date_invalid");
        }
    }

    private static ResponseEntity<?> ok(Object body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(body);
    }
}
