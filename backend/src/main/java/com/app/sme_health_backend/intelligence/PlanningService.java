package com.app.sme_health_backend.intelligence;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.productsupport.AuditRecorder;
import com.app.sme_health_backend.productsupport.DomainException;
import com.app.sme_health_backend.productsupport.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Business budgets (versioned revisions, actual comparison and variance) and financial goals (progress derived
 * only from authoritative data). Actuals come from {@link ProfitAndLossService}; plans never post anything.
 */
@Service
public class PlanningService {
    /** Budget-only aggregate categories for system-generated cost lines. */
    public static final Set<String> SYSTEM_EXPENSE_CATEGORIES = Set.of("COST_OF_SALES", "COMMISSIONS", "INVENTORY_LOSSES");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Karachi");
    private static final int MAX_LINES = 60;

    private final JdbcTemplate jdbc;
    private final ProfitAndLossService pnl;
    private final CashReadService cash;
    private final AuditRecorder audit;

    public PlanningService(JdbcTemplate jdbc, ProfitAndLossService pnl, CashReadService cash, AuditRecorder audit) {
        this.jdbc = jdbc;
        this.pnl = pnl;
        this.cash = cash;
        this.audit = audit;
    }

    public record LineInput(String lineKind, String category, String amount) { }

    // =============================================================== budgets

    @Transactional
    public Map<String, Object> createBudget(BusinessAccessContext ctx, String name, String startMonth, String endMonth,
                                            UUID projectId, List<LineInput> lines, String notes) {
        String cleanName = text(name, "name", 120);
        YearMonth start = month(startMonth, "periodStart");
        YearMonth end = month(endMonth, "periodEnd");
        if (end.isBefore(start) || ChronoUnit.MONTHS.between(start, end) >= 24) {
            throw DomainException.invalid("periodEnd", "period_invalid");
        }
        if (projectId != null) {
            Integer p = jdbc.queryForObject("SELECT count(*) FROM projects WHERE business_id = ? AND id = ?", Integer.class,
                    ctx.businessId(), projectId);
            if (p == null || p == 0) throw DomainException.notFound("Project");
        }
        List<Object[]> parsed = parseLines(lines);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO business_budgets(id, business_id, name, period_start, period_end, project_id, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, ctx.businessId(), cleanName, Date.valueOf(start.atDay(1)), Date.valueOf(end.atEndOfMonth()), projectId,
                ctx.userId(), ctx.userId());
        UUID revision = UUID.randomUUID();
        jdbc.update("INSERT INTO business_budget_revisions(id, business_id, budget_id, revision_no, notes, created_by) "
                + "VALUES (?, ?, ?, 1, ?, ?)", revision, ctx.businessId(), id, blank(notes), ctx.userId());
        insertLines(ctx.businessId(), revision, parsed);
        audit.record(ctx, AuditEventType.BUDGET_CHANGED, "BUDGET", id, Map.of("action", "CREATED"));
        return budget(ctx, id);
    }

    /** Replaces the draft revision's lines, creating a new draft revision from an approved budget if needed. */
    @Transactional
    public Map<String, Object> reviseBudget(BusinessAccessContext ctx, UUID budgetId, List<LineInput> lines, String notes) {
        Map<String, Object> budget = lockBudget(ctx.businessId(), budgetId);
        if ("CLOSED".equals(budget.get("status")) || "ARCHIVED".equals(budget.get("status"))) {
            throw DomainException.conflict("budget_closed", "Closed or archived budgets cannot be revised.");
        }
        List<Object[]> parsed = parseLines(lines);
        List<Map<String, Object>> drafts = jdbc.queryForList("SELECT id FROM business_budget_revisions WHERE business_id = ? "
                + "AND budget_id = ? AND status = 'DRAFT'", ctx.businessId(), budgetId);
        UUID revision;
        if (drafts.isEmpty()) {
            Integer max = jdbc.queryForObject("SELECT max(revision_no) FROM business_budget_revisions WHERE business_id = ? "
                    + "AND budget_id = ?", Integer.class, ctx.businessId(), budgetId);
            revision = UUID.randomUUID();
            jdbc.update("INSERT INTO business_budget_revisions(id, business_id, budget_id, revision_no, notes, created_by) "
                    + "VALUES (?, ?, ?, ?, ?, ?)", revision, ctx.businessId(), budgetId, (max == null ? 0 : max) + 1,
                    blank(notes), ctx.userId());
        } else {
            revision = (UUID) drafts.get(0).get("id");
            jdbc.update("DELETE FROM business_budget_lines WHERE business_id = ? AND revision_id = ?", ctx.businessId(), revision);
            jdbc.update("UPDATE business_budget_revisions SET notes = ?, version = version + 1 WHERE business_id = ? AND id = ?",
                    blank(notes), ctx.businessId(), revision);
        }
        insertLines(ctx.businessId(), revision, parsed);
        touch(ctx, budgetId);
        audit.record(ctx, AuditEventType.BUDGET_CHANGED, "BUDGET", budgetId, Map.of("action", "REVISED"));
        return budget(ctx, budgetId);
    }

    @Transactional
    public Map<String, Object> approveBudget(BusinessAccessContext ctx, UUID budgetId) {
        Map<String, Object> budget = lockBudget(ctx.businessId(), budgetId);
        if ("CLOSED".equals(budget.get("status")) || "ARCHIVED".equals(budget.get("status"))) {
            throw DomainException.conflict("budget_closed", "Closed or archived budgets cannot be approved.");
        }
        List<Map<String, Object>> drafts = jdbc.queryForList("SELECT id FROM business_budget_revisions WHERE business_id = ? "
                + "AND budget_id = ? AND status = 'DRAFT'", ctx.businessId(), budgetId);
        if (drafts.isEmpty()) {
            throw DomainException.conflict("no_draft_revision", "There is no draft revision to approve.");
        }
        UUID draft = (UUID) drafts.get(0).get("id");
        Integer lines = jdbc.queryForObject("SELECT count(*) FROM business_budget_lines WHERE business_id = ? AND revision_id = ?",
                Integer.class, ctx.businessId(), draft);
        if (lines == null || lines == 0) {
            throw DomainException.conflict("empty_revision", "A budget revision needs at least one line before approval.");
        }
        jdbc.update("UPDATE business_budget_revisions SET status = 'SUPERSEDED', version = version + 1 "
                + "WHERE business_id = ? AND budget_id = ? AND status = 'APPROVED'", ctx.businessId(), budgetId);
        jdbc.update("UPDATE business_budget_revisions SET status = 'APPROVED', approved_by = ?, approved_at = now(), "
                + "version = version + 1 WHERE business_id = ? AND id = ?", ctx.userId(), ctx.businessId(), draft);
        jdbc.update("UPDATE business_budgets SET status = 'ACTIVE', updated_by = ?, updated_at = now(), version = version + 1 "
                + "WHERE business_id = ? AND id = ?", ctx.userId(), ctx.businessId(), budgetId);
        audit.record(ctx, AuditEventType.BUDGET_CHANGED, "BUDGET", budgetId, Map.of("action", "APPROVED"));
        return budget(ctx, budgetId);
    }

    @Transactional
    public Map<String, Object> transitionBudget(BusinessAccessContext ctx, UUID budgetId, String target) {
        Map<String, Object> budget = lockBudget(ctx.businessId(), budgetId);
        String current = (String) budget.get("status");
        boolean allowed = ("CLOSED".equals(target) && "ACTIVE".equals(current))
                || ("ARCHIVED".equals(target) && !"ARCHIVED".equals(current));
        if (!allowed) {
            throw DomainException.conflict("invalid_transition", "A budget cannot move from " + current + " to " + target + ".");
        }
        jdbc.update("UPDATE business_budgets SET status = ?, updated_by = ?, updated_at = now(), version = version + 1 "
                + "WHERE business_id = ? AND id = ?", target, ctx.userId(), ctx.businessId(), budgetId);
        audit.record(ctx, AuditEventType.BUDGET_CHANGED, "BUDGET", budgetId, Map.of("action", target));
        return budget(ctx, budgetId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> budgets(BusinessAccessContext ctx) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT id FROM business_budgets WHERE business_id = ? "
                + "ORDER BY period_start DESC, created_at DESC LIMIT 100", ctx.businessId())) {
            out.add(budget(ctx, (UUID) row.get("id")));
        }
        return out;
    }

    /** Budget with revisions and actual-vs-budget variance for the approved (else draft) revision. */
    @Transactional(readOnly = true)
    public Map<String, Object> budget(BusinessAccessContext ctx, UUID budgetId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM business_budgets WHERE business_id = ? AND id = ?",
                ctx.businessId(), budgetId);
        if (rows.isEmpty()) throw DomainException.notFound("Budget");
        Map<String, Object> b = rows.get(0);
        LocalDate start = ((Date) b.get("period_start")).toLocalDate();
        LocalDate end = ((Date) b.get("period_end")).toLocalDate();
        List<Map<String, Object>> revisions = jdbc.queryForList("SELECT id, revision_no, status, notes, approved_at, created_at "
                + "FROM business_budget_revisions WHERE business_id = ? AND budget_id = ? ORDER BY revision_no DESC",
                ctx.businessId(), budgetId);
        Map<String, Object> basis = revisions.stream().filter(r -> "APPROVED".equals(r.get("status"))).findFirst()
                .orElse(revisions.stream().filter(r -> "DRAFT".equals(r.get("status"))).findFirst().orElse(null));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", budgetId);
        body.put("name", b.get("name"));
        body.put("periodStart", start.toString());
        body.put("periodEnd", end.toString());
        body.put("projectId", b.get("project_id"));
        body.put("status", b.get("status"));
        body.put("version", b.get("version"));
        body.put("revisions", revisions.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("revisionNo", r.get("revision_no"));
            m.put("status", r.get("status"));
            m.put("notes", r.get("notes"));
            m.put("approvedAt", r.get("approved_at") == null ? null : r.get("approved_at").toString());
            m.put("lines", lines(ctx.businessId(), (UUID) r.get("id")));
            return m;
        }).toList());
        body.put("basisRevisionNo", basis == null ? null : basis.get("revision_no"));
        body.put("basisStatus", basis == null ? null : basis.get("status"));

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate actualEnd = end.isAfter(today) ? today : end;
        body.put("periodElapsedPct", elapsedPct(start, end, today));
        if (basis == null || start.isAfter(today)) {
            body.put("variance", null);
            body.put("actualsStatus", start.isAfter(today) ? "PERIOD_NOT_STARTED" : "NO_REVISION");
            return body;
        }
        ProfitAndLossService.Statement s = pnl.statement(ctx.businessId(), start, actualEnd);
        Map<String, BigDecimal> actualRevenue = ProfitAndLossService.byCategory(s.revenueFacts());
        Map<String, BigDecimal> actualExpense = ProfitAndLossService.byCategory(s.expenseFacts());
        actualExpense.merge("COST_OF_SALES", s.costOfSales(), BigDecimal::add);
        actualExpense.merge("COMMISSIONS", s.commissionCost(), BigDecimal::add);
        actualExpense.merge("INVENTORY_LOSSES", s.inventoryLosses().add(s.supplierReturnValue()), BigDecimal::add);

        List<Map<String, Object>> varianceLines = new ArrayList<>();
        Set<String> budgetedRevenue = new LinkedHashSet<>();
        Set<String> budgetedExpense = new LinkedHashSet<>();
        BigDecimal budgetRevenue = Money.ZERO, budgetExpense = Money.ZERO, actualRevenueTotal = Money.ZERO, actualExpenseTotal = Money.ZERO;
        for (Map<String, Object> line : lines(ctx.businessId(), (UUID) basis.get("id"))) {
            String kind = (String) line.get("lineKind");
            String category = (String) line.get("category");
            BigDecimal planned = new BigDecimal((String) line.get("amount"));
            boolean revenue = "REVENUE".equals(kind);
            BigDecimal actual = Money.nz((revenue ? actualRevenue : actualExpense).get(category));
            (revenue ? budgetedRevenue : budgetedExpense).add(category);
            if (revenue) { budgetRevenue = budgetRevenue.add(planned); actualRevenueTotal = actualRevenueTotal.add(actual); }
            else { budgetExpense = budgetExpense.add(planned); actualExpenseTotal = actualExpenseTotal.add(actual); }
            varianceLines.add(varianceLine(kind, category, planned, actual, revenue));
        }
        List<Map<String, Object>> unbudgeted = new ArrayList<>();
        actualRevenue.forEach((c, v) -> { if (!budgetedRevenue.contains(c) && v.signum() != 0) unbudgeted.add(Map.of("lineKind", "REVENUE", "category", c, "actual", Money.str(v))); });
        actualExpense.forEach((c, v) -> { if (!budgetedExpense.contains(c) && v.signum() != 0) unbudgeted.add(Map.of("lineKind", "EXPENSE", "category", c, "actual", Money.str(v))); });
        Map<String, Object> variance = new LinkedHashMap<>();
        variance.put("actualThrough", actualEnd.toString());
        variance.put("lines", varianceLines);
        variance.put("unbudgetedActuals", unbudgeted);
        variance.put("budgetRevenue", Money.str(budgetRevenue));
        variance.put("actualRevenue", Money.str(actualRevenueTotal));
        variance.put("budgetExpense", Money.str(budgetExpense));
        variance.put("actualExpense", Money.str(actualExpenseTotal));
        variance.put("unclassifiedRevenue", Money.str(s.unclassifiedRevenue()));
        variance.put("unclassifiedExpenses", Money.str(s.unclassifiedExpenses()));
        variance.put("completeness", s.unclassifiedExpenses().signum() == 0 && s.unclassifiedRevenue().signum() == 0
                ? "COMPLETE" : "PARTIAL_UNCLASSIFIED");
        variance.put("source", ProfitAndLossService.VERSION);
        body.put("variance", variance);
        body.put("actualsStatus", "AVAILABLE");
        return body;
    }

    private static Map<String, Object> varianceLine(String kind, String category, BigDecimal planned, BigDecimal actual, boolean revenue) {
        BigDecimal variance = actual.subtract(planned);
        String status;
        if (planned.signum() == 0) status = actual.signum() == 0 ? "ON_PLAN" : revenue ? "FAVOURABLE" : "OVER_BUDGET";
        else if (revenue) status = variance.signum() >= 0 ? "FAVOURABLE" : "BELOW_PLAN";
        else status = variance.signum() > 0 ? "OVER_BUDGET" : "WITHIN_BUDGET";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lineKind", kind);
        m.put("category", category);
        m.put("budget", Money.str(planned));
        m.put("actual", Money.str(actual));
        m.put("variance", Money.str(variance));
        m.put("variancePct", Money.pct(variance, planned) == null ? null : Money.pct(variance, planned).toPlainString());
        m.put("utilizationPct", Money.pct(actual, planned) == null ? null : Money.pct(actual, planned).toPlainString());
        m.put("status", status);
        return m;
    }

    // =============================================================== goals

    public static final Set<String> GOAL_METRICS = Set.of("REVENUE", "GROSS_PROFIT", "OPERATING_PROFIT", "CASH_BALANCE",
            "EXPENSE_CEILING", "RECEIVABLES_CEILING");

    @Transactional
    public Map<String, Object> createGoal(BusinessAccessContext ctx, String name, String metric, String target,
                                          String periodStart, String periodEnd, String notes) {
        String cleanName = text(name, "name", 120);
        if (metric == null || !GOAL_METRICS.contains(metric)) throw DomainException.invalid("metric", "enum_invalid");
        BigDecimal value = Money.amount(target, "targetValue", true);
        LocalDate start = date(periodStart, "periodStart");
        LocalDate end = date(periodEnd, "periodEnd");
        if (end.isBefore(start) || end.isAfter(start.plusYears(5))) throw DomainException.invalid("periodEnd", "period_invalid");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO financial_goals(id, business_id, name, metric, target_value, period_start, period_end, notes,
                    created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, ctx.businessId(), cleanName, metric, value, Date.valueOf(start), Date.valueOf(end), blank(notes),
                ctx.userId(), ctx.userId());
        audit.record(ctx, AuditEventType.GOAL_CHANGED, "GOAL", id, Map.of("action", "CREATED", "metric", metric));
        return goal(ctx, id);
    }

    @Transactional
    public Map<String, Object> updateGoal(BusinessAccessContext ctx, UUID goalId, String name, String target, String notes,
                                          long expectedVersion) {
        Map<String, Object> g = lockGoal(ctx.businessId(), goalId);
        if (!"ACTIVE".equals(g.get("status"))) throw DomainException.conflict("goal_not_active", "Only active goals can be edited.");
        if (((Number) g.get("version")).longValue() != expectedVersion) throw DomainException.conflict("stale_goal", "The goal changed; reload.");
        jdbc.update("UPDATE financial_goals SET name = ?, target_value = ?, notes = ?, updated_by = ?, updated_at = now(), "
                + "version = version + 1 WHERE business_id = ? AND id = ?", text(name, "name", 120),
                Money.amount(target, "targetValue", true), blank(notes), ctx.userId(), ctx.businessId(), goalId);
        audit.record(ctx, AuditEventType.GOAL_CHANGED, "GOAL", goalId, Map.of("action", "UPDATED"));
        return goal(ctx, goalId);
    }

    @Transactional
    public Map<String, Object> transitionGoal(BusinessAccessContext ctx, UUID goalId, String target) {
        Map<String, Object> g = lockGoal(ctx.businessId(), goalId);
        String current = (String) g.get("status");
        boolean ok = ("CANCELLED".equals(target) && "ACTIVE".equals(current))
                || ("ARCHIVED".equals(target) && !"ARCHIVED".equals(current));
        if (!ok) throw DomainException.conflict("invalid_transition", "A goal cannot move from " + current + " to " + target + ".");
        jdbc.update("UPDATE financial_goals SET status = ?, updated_by = ?, updated_at = now(), version = version + 1 "
                + "WHERE business_id = ? AND id = ?", target, ctx.userId(), ctx.businessId(), goalId);
        audit.record(ctx, AuditEventType.GOAL_CHANGED, "GOAL", goalId, Map.of("action", target));
        return goal(ctx, goalId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> goals(BusinessAccessContext ctx) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT id FROM financial_goals WHERE business_id = ? "
                + "ORDER BY (status = 'ACTIVE') DESC, period_end ASC LIMIT 100", ctx.businessId())) {
            out.add(goal(ctx, (UUID) row.get("id")));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> goal(BusinessAccessContext ctx, UUID goalId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM financial_goals WHERE business_id = ? AND id = ?",
                ctx.businessId(), goalId);
        if (rows.isEmpty()) throw DomainException.notFound("Goal");
        Map<String, Object> g = rows.get(0);
        String metric = (String) g.get("metric");
        BigDecimal target = Money.nz((BigDecimal) g.get("target_value"));
        LocalDate start = ((Date) g.get("period_start")).toLocalDate();
        LocalDate end = ((Date) g.get("period_end")).toLocalDate();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate through = end.isAfter(today) ? today : end;
        boolean started = !start.isAfter(today);
        boolean ended = end.isBefore(today);

        BigDecimal actual = null;
        String evidence;
        boolean ceiling = metric.endsWith("_CEILING");
        if (!started) {
            evidence = "PERIOD_NOT_STARTED";
        } else {
            switch (metric) {
                case "REVENUE" -> { actual = pnl.statement(ctx.businessId(), start, through).revenue(); evidence = ProfitAndLossService.VERSION + ":revenue"; }
                case "GROSS_PROFIT" -> { actual = pnl.statement(ctx.businessId(), start, through).grossProfit(); evidence = ProfitAndLossService.VERSION + ":grossProfit"; }
                case "OPERATING_PROFIT" -> { actual = pnl.statement(ctx.businessId(), start, through).operatingProfit(); evidence = ProfitAndLossService.VERSION + ":operatingProfit"; }
                case "EXPENSE_CEILING" -> {
                    var s = pnl.statement(ctx.businessId(), start, through);
                    actual = s.costOfSales().add(s.operatingExpenses());
                    evidence = ProfitAndLossService.VERSION + ":costOfSales+operatingExpenses";
                }
                case "CASH_BALANCE" -> {
                    actual = cash.hasAccounts(ctx.businessId()) ? cash.cashBalancePkr(ctx.businessId(), through) : null;
                    evidence = actual == null ? "NO_ACCOUNTS" : "ledger:account_movements(PKR)";
                }
                case "RECEIVABLES_CEILING" -> { actual = cash.outstanding(ctx.businessId(), "RECEIVABLE"); evidence = "obligations:receivable_outstanding"; }
                default -> throw new IllegalStateException(metric);
            }
        }
        String progressStatus;
        BigDecimal progressPct = null;
        String status = (String) g.get("status");
        if (!"ACTIVE".equals(status)) {
            progressStatus = status;
        } else if (actual == null) {
            progressStatus = started ? "UNAVAILABLE" : "NOT_STARTED";
        } else if (ceiling) {
            progressPct = Money.pct(actual, target);
            boolean within = actual.compareTo(target) <= 0;
            progressStatus = ended ? (within ? "ACHIEVED" : "MISSED") : (within ? "ON_TRACK" : "AT_RISK");
        } else {
            progressPct = Money.pct(actual, target);
            boolean met = actual.compareTo(target) >= 0;
            if (met) progressStatus = "ACHIEVED";
            else if (ended) progressStatus = "MISSED";
            else {
                BigDecimal elapsed = elapsedPct(start, end, today);
                progressStatus = progressPct != null && elapsed != null && progressPct.compareTo(elapsed) >= 0 ? "ON_TRACK" : "AT_RISK";
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", goalId);
        body.put("name", g.get("name"));
        body.put("metric", metric);
        body.put("ceiling", ceiling);
        body.put("targetValue", Money.str(target));
        body.put("periodStart", start.toString());
        body.put("periodEnd", end.toString());
        body.put("status", status);
        body.put("notes", g.get("notes"));
        body.put("version", g.get("version"));
        body.put("actualValue", actual == null ? null : Money.str(actual));
        body.put("actualThrough", started ? through.toString() : null);
        body.put("progressPct", progressPct == null ? null : progressPct.toPlainString());
        body.put("periodElapsedPct", elapsedPct(start, end, today) == null ? null : elapsedPct(start, end, today).toPlainString());
        body.put("progressStatus", progressStatus);
        body.put("evidenceSource", evidence);
        return body;
    }

    // =============================================================== helpers

    private List<Object[]> parseLines(List<LineInput> lines) {
        if (lines == null || lines.isEmpty() || lines.size() > MAX_LINES) {
            throw DomainException.invalid("lines", "line_count_invalid");
        }
        List<Object[]> parsed = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            LineInput l = lines.get(i);
            String path = "lines[" + i + "]";
            if (l == null) throw DomainException.invalid(path, "required");
            String kind = l.lineKind();
            if (!"REVENUE".equals(kind) && !"EXPENSE".equals(kind)) throw DomainException.invalid(path + ".lineKind", "enum_invalid");
            String category = l.category();
            boolean valid;
            if ("REVENUE".equals(kind)) {
                valid = category != null && isCategory(category) && FinancialCategory.valueOf(category).revenue();
            } else {
                valid = category != null && (SYSTEM_EXPENSE_CATEGORIES.contains(category)
                        || (isCategory(category) && !FinancialCategory.valueOf(category).revenue()));
            }
            if (!valid) throw DomainException.invalid(path + ".category", "category_invalid");
            if (!seen.add(kind + ":" + category)) throw DomainException.invalid(path + ".category", "duplicate_category");
            parsed.add(new Object[] {kind, category, Money.amount(l.amount(), path + ".amount", true)});
        }
        return parsed;
    }

    private static boolean isCategory(String raw) {
        for (FinancialCategory c : FinancialCategory.values()) if (c.name().equals(raw)) return true;
        return false;
    }

    private void insertLines(UUID businessId, UUID revisionId, List<Object[]> lines) {
        for (Object[] l : lines) {
            jdbc.update("INSERT INTO business_budget_lines(business_id, revision_id, line_kind, category, amount) VALUES (?, ?, ?, ?, ?)",
                    businessId, revisionId, l[0], l[1], l[2]);
        }
    }

    private List<Map<String, Object>> lines(UUID businessId, UUID revisionId) {
        return jdbc.queryForList("SELECT line_kind, category, amount FROM business_budget_lines WHERE business_id = ? "
                + "AND revision_id = ? ORDER BY line_kind DESC, category", businessId, revisionId).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineKind", r.get("line_kind"));
            m.put("category", r.get("category"));
            m.put("amount", Money.str((BigDecimal) r.get("amount")));
            return m;
        }).toList();
    }

    private Map<String, Object> lockBudget(UUID businessId, UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM business_budgets WHERE business_id = ? AND id = ? FOR UPDATE",
                businessId, id);
        if (rows.isEmpty()) throw DomainException.notFound("Budget");
        return rows.get(0);
    }

    private Map<String, Object> lockGoal(UUID businessId, UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM financial_goals WHERE business_id = ? AND id = ? FOR UPDATE",
                businessId, id);
        if (rows.isEmpty()) throw DomainException.notFound("Goal");
        return rows.get(0);
    }

    private void touch(BusinessAccessContext ctx, UUID budgetId) {
        jdbc.update("UPDATE business_budgets SET updated_by = ?, updated_at = now(), version = version + 1 WHERE business_id = ? AND id = ?",
                ctx.userId(), ctx.businessId(), budgetId);
    }

    static BigDecimal elapsedPct(LocalDate start, LocalDate end, LocalDate today) {
        long total = ChronoUnit.DAYS.between(start, end) + 1;
        if (total <= 0) return null;
        long elapsed = today.isBefore(start) ? 0 : today.isAfter(end) ? total : ChronoUnit.DAYS.between(start, today) + 1;
        return BigDecimal.valueOf(elapsed * 100).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private static YearMonth month(String raw, String field) {
        try {
            return YearMonth.parse(raw.trim());
        } catch (Exception e) {
            throw DomainException.invalid(field, "month_invalid");
        }
    }

    private static LocalDate date(String raw, String field) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            throw DomainException.invalid(field, "date_invalid");
        }
    }

    private static String text(String raw, String field, int max) {
        if (raw == null || raw.isBlank()) throw DomainException.invalid(field, "required");
        if (raw.trim().length() > max) throw DomainException.invalid(field, "too_long");
        return raw.trim();
    }

    private static String blank(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.trim().length() > 500) throw DomainException.invalid("notes", "too_long");
        return raw.trim();
    }
}
