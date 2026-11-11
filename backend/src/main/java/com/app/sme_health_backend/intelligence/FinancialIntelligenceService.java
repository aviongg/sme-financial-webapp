package com.app.sme_health_backend.intelligence;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.inventory.ledger.InventoryReadService;
import com.app.sme_health_backend.productsupport.Money;
import com.app.sme_health_backend.projects.profitability.ProjectProfitabilityService;
import com.app.sme_health_backend.risk.dto.FinancialRiskInsight;
import com.app.sme_health_backend.risk.service.FinancialRiskAssessmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Final financial intelligence layer ({@value #VERSION}) over authoritative data only. Revenue is recognized
 * revenue (never cash receipt as a substitute), costs come from the accrual P&amp;L, profitability dimensions exist
 * only where real attribution exists (projects, samples, customers via recognized obligations), and the
 * action center unifies existing recommendations and Developer A's deterministic risk insights with new evidence
 * rules instead of creating a competing alerts engine. health-score-v1 is read, never recomputed.
 */
@Service
public class FinancialIntelligenceService {
    public static final String VERSION = "intelligence-v1";
    private static final Logger log = LoggerFactory.getLogger(FinancialIntelligenceService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Karachi");

    private final JdbcTemplate jdbc;
    private final ProfitAndLossService pnl;
    private final PlanningService planning;
    private final BreakEvenService breakEven;
    private final CashReadService cash;
    private final InventoryReadService inventory;
    private final ProjectProfitabilityService projects;
    private final FinancialRiskAssessmentService risk;

    public FinancialIntelligenceService(JdbcTemplate jdbc, ProfitAndLossService pnl, PlanningService planning,
                                        BreakEvenService breakEven, CashReadService cash, InventoryReadService inventory,
                                        ProjectProfitabilityService projects, FinancialRiskAssessmentService risk) {
        this.jdbc = jdbc;
        this.pnl = pnl;
        this.planning = planning;
        this.breakEven = breakEven;
        this.cash = cash;
        this.inventory = inventory;
        this.projects = projects;
        this.risk = risk;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview(BusinessAccessContext ctx, LocalDate from, LocalDate to) {
        UUID b = ctx.businessId();
        ProfitAndLossService.Statement s = pnl.statement(b, from, to);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", VERSION);
        body.put("from", from.toString());
        body.put("to", to.toString());

        // Revenue intelligence: recognized revenue, trend, categories and customer concentration.
        Map<String, Object> revenue = new LinkedHashMap<>();
        revenue.put("recognizedRevenue", Money.str(s.revenue()));
        revenue.put("cashSales", Money.str(s.cashSales()));
        revenue.put("invoiceRevenue", Money.str(s.invoiceRevenue()));
        revenue.put("creditNotes", Money.str(s.creditNotes()));
        revenue.put("byCategory", amounts(ProfitAndLossService.byCategory(s.revenueFacts())));
        Map<UUID, BigDecimal> byCustomer = new LinkedHashMap<>();
        BigDecimal unattributedRevenue = Money.ZERO;
        for (ProfitAndLossService.Fact f : s.revenueFacts()) {
            if (f.counterpartyId() == null) unattributedRevenue = unattributedRevenue.add(f.amount());
            else byCustomer.merge(f.counterpartyId(), f.amount(), BigDecimal::add);
        }
        List<Map<String, Object>> customers = new ArrayList<>();
        byCustomer.entrySet().stream().sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed()).limit(10).forEach(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("counterpartyId", e.getKey());
            m.put("name", jdbc.queryForList("SELECT display_name FROM counterparties WHERE business_id = ? AND id = ?", String.class, b, e.getKey())
                    .stream().findFirst().orElse(null));
            m.put("amount", Money.str(e.getValue()));
            m.put("sharePct", pctStr(e.getValue(), s.revenue()));
            customers.add(m);
        });
        revenue.put("topCustomers", customers);
        revenue.put("revenueWithoutCustomer", Money.str(unattributedRevenue));
        revenue.put("topCustomerSharePct", customers.isEmpty() ? null : customers.get(0).get("sharePct"));
        revenue.put("trend", trend(b, to, 6));
        body.put("revenue", revenue);

        Map<String, Object> expenses = new LinkedHashMap<>();
        expenses.put("costOfSales", Money.str(s.costOfSales()));
        expenses.put("operatingExpenses", Money.str(s.operatingExpenses()));
        expenses.put("byCategory", amounts(ProfitAndLossService.byCategory(s.expenseFacts())));
        expenses.put("commissionCost", Money.str(s.commissionCost()));
        expenses.put("inventoryLosses", Money.str(s.inventoryLosses()));
        expenses.put("unclassified", Money.str(s.unclassifiedExpenses()));
        expenses.put("unclassifiedSharePct", pctStr(s.unclassifiedExpenses(), s.operatingExpenses().subtract(s.commissionCost())
                .subtract(s.inventoryLosses()).subtract(s.supplierReturnValue()).add(s.debitNotes())));
        body.put("expenses", expenses);

        Map<String, Object> profitability = new LinkedHashMap<>();
        profitability.put("grossProfit", Money.str(s.grossProfit()));
        profitability.put("grossMarginPct", s.grossMarginPct() == null ? null : s.grossMarginPct().toPlainString());
        profitability.put("operatingProfit", Money.str(s.operatingProfit()));
        profitability.put("operatingMarginPct", s.operatingMarginPct() == null ? null : s.operatingMarginPct().toPlainString());
        profitability.put("netProfit", Money.str(s.netProfit()));
        profitability.put("projects", projectRanking(b));
        profitability.put("samples", sampleSummary(b));
        body.put("profitability", profitability);

        body.put("budgets", planning.budgets(ctx).stream().filter(x -> "ACTIVE".equals(x.get("status"))).map(x -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", x.get("id"));
            m.put("name", x.get("name"));
            m.put("periodStart", x.get("periodStart"));
            m.put("periodEnd", x.get("periodEnd"));
            m.put("variance", x.get("variance"));
            return m;
        }).toList());
        body.put("goals", planning.goals(ctx).stream().filter(g -> "ACTIVE".equals(g.get("status"))).toList());
        body.put("breakEven", breakEven.analyse(b, from, to));
        body.put("health", health(b));
        Map<String, Object> position = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(ZONE);
        position.put("cashBalance", cash.hasAccounts(b) ? Money.str(cash.cashBalancePkr(b, today)) : null);
        position.put("receivablesOutstanding", Money.str(cash.outstanding(b, "RECEIVABLE")));
        position.put("payablesOutstanding", Money.str(cash.outstanding(b, "PAYABLE")));
        position.put("overdueReceivables", Money.str(cash.overdue(b, "RECEIVABLE", today)));
        position.put("overduePayables", Money.str(cash.overdue(b, "PAYABLE", today)));
        position.put("inventoryValue", Money.str(inventory.register(b).totalValue()));
        body.put("position", position);
        body.put("completeness", s.unclassifiedExpenses().signum() == 0 && s.unclassifiedRevenue().signum() == 0 ? "COMPLETE" : "PARTIAL_UNCLASSIFIED");
        return body;
    }

    /** Lightweight dashboard snapshot (F13 widgets): position plus small project/sample/estimate summaries. */
    @Transactional(readOnly = true)
    public Map<String, Object> position(BusinessAccessContext ctx) {
        UUID b = ctx.businessId();
        LocalDate today = LocalDate.now(ZONE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("asOf", today.toString());
        body.put("cashBalance", cash.hasAccounts(b) ? Money.str(cash.cashBalancePkr(b, today)) : null);
        body.put("receivablesOutstanding", Money.str(cash.outstanding(b, "RECEIVABLE")));
        body.put("payablesOutstanding", Money.str(cash.outstanding(b, "PAYABLE")));
        body.put("overdueReceivables", Money.str(cash.overdue(b, "RECEIVABLE", today)));
        body.put("overduePayables", Money.str(cash.overdue(b, "PAYABLE", today)));
        InventoryReadService.Register register = inventory.register(b);
        body.put("inventoryValue", Money.str(register.totalValue()));
        body.put("itemsBelowReorder", register.itemsBelowReorder());
        List<Map<String, Object>> projectRows = new ArrayList<>();
        for (Map<String, Object> p : jdbc.queryForList("SELECT id FROM projects WHERE business_id = ? AND status = 'ACTIVE' "
                + "ORDER BY updated_at DESC LIMIT 5", b)) {
            try {
                Map<String, Object> prof = projects.profitability(b, (UUID) p.get("id"));
                @SuppressWarnings("unchecked") Map<String, Object> r = (Map<String, Object>) prof.get("profitability");
                projectRows.add(Map.of("projectId", p.get("id"), "code", String.valueOf(prof.get("code")),
                        "profit", String.valueOf(r.get("profit")), "status", String.valueOf(r.get("status"))));
            } catch (RuntimeException e) {
                log.warn("Dashboard project summary skipped: {}", e.getClass().getSimpleName());
            }
        }
        body.put("activeProjects", projectRows);
        body.put("samples", sampleSummary(b));
        Integer estimates = jdbc.queryForObject("SELECT count(*) FROM estimates WHERE business_id = ? AND status = 'ACTIVE'", Integer.class, b);
        body.put("activeEstimates", estimates == null ? 0 : estimates);
        return body;
    }

    /** Unified action center: one ranked list, every action carries its reason and evidence. */
    @Transactional(readOnly = true)
    public Map<String, Object> actions(BusinessAccessContext ctx) {
        UUID b = ctx.businessId();
        LocalDate today = LocalDate.now(ZONE);
        List<Map<String, Object>> actions = new ArrayList<>();

        // 1. Existing deterministic recommendations (status lifecycle kept by RecommendationService).
        for (Map<String, Object> r : jdbc.queryForList("SELECT id, month, recommendation_text AS text, category, priority, status FROM recommendations "
                + "WHERE user_id = ? AND status IN ('NEW', 'VIEWED') ORDER BY created_at DESC LIMIT 10", b)) {
            actions.add(action("RECOMMENDATION:" + r.get("id"), "RECOMMENDATION", priority((String) r.get("priority")),
                    (String) r.get("text"), "health-score-v1 recommendation for " + r.get("month"), null,
                    List.of(evidence("RECOMMENDATION", r.get("id"), (String) r.get("category"))), "/health/components", r.get("id")));
        }
        // 2. Developer A deterministic financial risk insights (no second risk engine).
        try {
            for (FinancialRiskInsight i : risk.getInsights(ctx, null, null, null, null, null)) {
                actions.add(action("RISK:" + i.riskCode(), "RISK", i.severity() == null ? "MEDIUM" : i.severity().name(),
                        i.title(), i.summary(), i.affectedAmount() == null ? null : Money.str(i.affectedAmount().setScale(4, RoundingMode.HALF_UP)),
                        List.of(evidence("RISK_INSIGHT", i.id(), i.riskCode())), "/finance/risks", null));
            }
        } catch (RuntimeException e) {
            log.warn("Risk insights unavailable for action center: {}", e.getClass().getSimpleName());
            actions.add(action("SYSTEM:RISK_UNAVAILABLE", "DATA_QUALITY", "LOW", "Risk insights unavailable",
                    "The risk assessment could not be evaluated for this request.", null, List.of(), "/finance/risks", null));
        }
        // 3. Overdue obligations.
        BigDecimal overdueAr = cash.overdue(b, "RECEIVABLE", today);
        if (overdueAr.signum() > 0) actions.add(action("AR:OVERDUE", "OVERDUE_RECEIVABLES", "HIGH", "Collect overdue receivables",
                "Customers owe overdue amounts past their due dates.", Money.str(overdueAr), List.of(evidence("OBLIGATIONS", null, "RECEIVABLE overdue")),
                "/records/receivables", null));
        BigDecimal overdueAp = cash.overdue(b, "PAYABLE", today);
        if (overdueAp.signum() > 0) actions.add(action("AP:OVERDUE", "OVERDUE_PAYABLES", "HIGH", "Pay or reschedule overdue bills",
                "Supplier obligations are past due.", Money.str(overdueAp), List.of(evidence("OBLIGATIONS", null, "PAYABLE overdue")),
                "/records/payables", null));
        // 4. Finalized documents not yet recognized.
        for (Map<String, Object> d : jdbc.queryForList("""
                SELECT d.id, d.kind, d.document_number, d.total FROM commercial_documents d
                WHERE d.business_id = ? AND d.status = 'FINALIZED'
                  AND NOT EXISTS (SELECT 1 FROM financial_obligations o WHERE o.business_id = d.business_id AND o.source_id = d.id::text)
                ORDER BY d.finalized_at LIMIT 10
                """, b)) {
            actions.add(action("DOC:UNRECOGNIZED:" + d.get("id"), "UNRECOGNIZED_DOCUMENT", "MEDIUM",
                    "Recognize " + d.get("document_number"), "A finalized " + ((String) d.get("kind")).toLowerCase()
                            + " is not in receivables/payables, so revenue/cost and balances exclude it.",
                    Money.str((BigDecimal) d.get("total")), List.of(evidence("COMMERCIAL_DOCUMENT", d.get("id"), (String) d.get("document_number"))),
                    "INVOICE".equals(d.get("kind")) ? "/invoices/" + d.get("id") : "/bills/" + d.get("id"), d.get("id")));
        }
        // 5. Inventory below reorder level.
        for (InventoryReadService.PositionView p : inventory.register(b).items()) {
            if (p.belowReorder()) actions.add(action("INV:REORDER:" + p.itemId(), "INVENTORY_REORDER", "MEDIUM", "Reorder " + p.sku(),
                    p.name() + " is at or below its reorder level.", Money.str(p.value()),
                    List.of(evidence("INVENTORY_POSITION", p.itemId(), "quantity " + p.quantity().stripTrailingZeros().toPlainString()
                            + " <= " + p.reorderLevel().stripTrailingZeros().toPlainString())), "/inventory", p.itemId()));
        }
        // 6. Projects: losses, over budget, pending recognition.
        for (Map<String, Object> p : jdbc.queryForList("SELECT id, code FROM projects WHERE business_id = ? AND status IN ('ACTIVE', 'DRAFT') "
                + "ORDER BY updated_at DESC LIMIT 20", b)) {
            try {
                Map<String, Object> prof = projects.profitability(b, (UUID) p.get("id"));
                @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>) prof.get("profitability");
                @SuppressWarnings("unchecked") Map<String, Object> plan = (Map<String, Object>) prof.get("plan");
                @SuppressWarnings("unchecked") Map<String, Object> rev = (Map<String, Object>) prof.get("revenue");
                if ("AVAILABLE".equals(result.get("status")) && new BigDecimal((String) result.get("profit")).signum() < 0) {
                    actions.add(action("PRJ:LOSS:" + p.get("id"), "PROJECT_LOSS", "HIGH", p.get("code") + " is loss-making",
                            "Recognized revenue does not cover recorded direct costs.", (String) result.get("profit"),
                            List.of(evidence("PROJECT_PROFITABILITY", p.get("id"), ProjectProfitabilityService.VERSION)), "/projects/" + p.get("id"), p.get("id")));
                }
                if (plan.get("costVariance") != null && new BigDecimal((String) plan.get("costVariance")).signum() > 0) {
                    actions.add(action("PRJ:OVERBUDGET:" + p.get("id"), "PROJECT_OVER_BUDGET", "MEDIUM", p.get("code") + " is over budget",
                            "Actual project cost exceeds the approved budget.", (String) plan.get("costVariance"),
                            List.of(evidence("PROJECT_BUDGET", p.get("id"), "revision " + plan.get("revision"))), "/projects/" + p.get("id"), p.get("id")));
                }
                if (new BigDecimal((String) rev.get("pendingRecognition")).signum() > 0) {
                    actions.add(action("PRJ:PENDING:" + p.get("id"), "PROJECT_PENDING_RECOGNITION", "LOW",
                            p.get("code") + " has unrecognized invoices", "Linked invoices are not yet recognized, so project revenue is partial.",
                            (String) rev.get("pendingRecognition"), List.of(evidence("PROJECT_LINKS", p.get("id"), "pending recognition")),
                            "/projects/" + p.get("id"), p.get("id")));
                }
            } catch (RuntimeException e) {
                log.warn("Project {} profitability unavailable for action center: {}", p.get("id"), e.getClass().getSimpleName());
            }
        }
        // 7. Budgets and goals.
        for (Map<String, Object> budget : planning.budgets(ctx)) {
            if (!"ACTIVE".equals(budget.get("status")) || budget.get("variance") == null) continue;
            @SuppressWarnings("unchecked") Map<String, Object> v = (Map<String, Object>) budget.get("variance");
            @SuppressWarnings("unchecked") List<Map<String, Object>> lines = (List<Map<String, Object>>) v.get("lines");
            for (Map<String, Object> l : lines) {
                if ("OVER_BUDGET".equals(l.get("status"))) {
                    actions.add(action("BUDGET:" + budget.get("id") + ":" + l.get("category"), "BUDGET_OVERSPEND", "MEDIUM",
                            budget.get("name") + ": " + l.get("category") + " over budget", "Actual spend exceeds the approved budget line.",
                            (String) l.get("variance"), List.of(evidence("BUDGET", budget.get("id"), (String) l.get("category"))),
                            "/planning/budgets", budget.get("id")));
                }
            }
        }
        for (Map<String, Object> g : planning.goals(ctx)) {
            if ("AT_RISK".equals(g.get("progressStatus")) || "MISSED".equals(g.get("progressStatus"))) {
                actions.add(action("GOAL:" + g.get("id"), "GOAL_" + g.get("progressStatus"), "MISSED".equals(g.get("progressStatus")) ? "HIGH" : "MEDIUM",
                        g.get("name") + " is " + ((String) g.get("progressStatus")).toLowerCase().replace('_', ' '),
                        "Progress " + g.get("progressPct") + "% vs " + g.get("periodElapsedPct") + "% of the period elapsed.",
                        (String) g.get("actualValue"), List.of(evidence("GOAL", g.get("id"), (String) g.get("evidenceSource"))), "/planning/goals", g.get("id")));
            }
        }
        // 8. Data quality that blocks intelligence.
        ProfitAndLossService.Statement month = pnl.statement(b, today.withDayOfMonth(1), today);
        if (month.unclassifiedExpenses().signum() > 0) {
            actions.add(action("DQ:UNCLASSIFIED", "DATA_QUALITY", "LOW", "Classify this month's expenses",
                    "Unclassified expenses block budget variance by category and break-even analysis.", Money.str(month.unclassifiedExpenses()),
                    List.of(evidence("CLASSIFICATIONS", null, ProfitAndLossService.VERSION)), "/planning/classify", null));
        }

        Map<String, Integer> rank = Map.of("CRITICAL", 0, "HIGH", 1, "MEDIUM", 2, "LOW", 3);
        actions.sort(Comparator.comparing((Map<String, Object> a) -> rank.getOrDefault((String) a.get("priority"), 4)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", VERSION);
        body.put("generatedFor", today.toString());
        body.put("actions", actions.size() > 100 ? actions.subList(0, 100) : actions);
        body.put("count", actions.size());
        return body;
    }

    private List<Map<String, Object>> trend(UUID b, LocalDate to, int months) {
        List<Map<String, Object>> out = new ArrayList<>();
        YearMonth end = YearMonth.from(to);
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = end.minusMonths(i);
            LocalDate start = ym.atDay(1);
            LocalDate stop = i == 0 ? to : ym.atEndOfMonth();
            ProfitAndLossService.Statement s = pnl.statement(b, start, stop);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", ym.toString());
            m.put("revenue", Money.str(s.revenue()));
            m.put("costOfSales", Money.str(s.costOfSales()));
            m.put("operatingExpenses", Money.str(s.operatingExpenses()));
            m.put("operatingProfit", Money.str(s.operatingProfit()));
            m.put("hasData", s.hasAnyData());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> projectRanking(UUID b) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> p : jdbc.queryForList("SELECT id FROM projects WHERE business_id = ? AND status NOT IN ('CANCELLED', 'ARCHIVED') "
                + "ORDER BY updated_at DESC LIMIT 25", b)) {
            try {
                Map<String, Object> prof = projects.profitability(b, (UUID) p.get("id"));
                @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>) prof.get("profitability");
                @SuppressWarnings("unchecked") Map<String, Object> rev = (Map<String, Object>) prof.get("revenue");
                @SuppressWarnings("unchecked") Map<String, Object> cost = (Map<String, Object>) prof.get("costs");
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("projectId", p.get("id"));
                m.put("code", prof.get("code"));
                m.put("name", prof.get("name"));
                m.put("revenue", rev.get("total"));
                m.put("cost", cost.get("total"));
                m.put("profit", result.get("profit"));
                m.put("marginPct", result.get("marginPct"));
                m.put("status", result.get("status"));
                out.add(m);
            } catch (RuntimeException e) {
                log.warn("Project ranking skipped {}: {}", p.get("id"), e.getClass().getSimpleName());
            }
        }
        out.sort(Comparator.comparing((Map<String, Object> m) -> new BigDecimal((String) m.get("profit"))).reversed());
        return out;
    }

    private Map<String, Object> sampleSummary(UUID b) {
        BigDecimal cost = Money.ZERO, recovered = Money.ZERO;
        int count = 0;
        for (Map<String, Object> s : jdbc.queryForList("SELECT id FROM sample_activities WHERE business_id = ? AND status <> 'ARCHIVED'", b)) {
            UUID id = (UUID) s.get("id");
            cost = cost.add(inventory.sampleMaterialCost(b, id)).add(Money.nz(jdbc.queryForObject(
                    "SELECT COALESCE(sum(amount), 0) FROM sample_actual_costs c WHERE c.business_id = ? AND c.sample_id = ? AND c.status = 'EFFECTIVE' "
                            + "AND NOT EXISTS (SELECT 1 FROM financial_transactions x WHERE x.business_id = c.business_id AND x.reversed_transaction_id = c.transaction_id)",
                    BigDecimal.class, b, id)));
            recovered = recovered.add(Money.nz(jdbc.queryForObject("SELECT COALESCE(sum(amount), 0) FROM sample_recovery_links l "
                    + "WHERE l.business_id = ? AND l.sample_id = ? AND l.status = 'ACTIVE' AND EXISTS (SELECT 1 FROM financial_obligations o "
                    + "WHERE o.business_id = l.business_id AND o.source_id = l.document_id::text AND o.status = 'OPEN')", BigDecimal.class, b, id)));
            count++;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", count);
        m.put("actualCost", Money.str(cost));
        m.put("linkedRecovery", Money.str(recovered));
        m.put("netCost", Money.str(cost.subtract(recovered)));
        return m;
    }

    private Map<String, Object> health(UUID b) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT month, composite_score, band, weakest_component, methodology_version "
                + "FROM score_results WHERE user_id = ? ORDER BY month DESC LIMIT 1", b);
        Map<String, Object> m = new LinkedHashMap<>();
        if (rows.isEmpty()) {
            m.put("status", "NO_SCORE");
            return m;
        }
        Map<String, Object> r = rows.get(0);
        m.put("status", "AVAILABLE");
        m.put("month", r.get("month"));
        m.put("compositeScore", r.get("composite_score") == null ? null : r.get("composite_score").toString());
        m.put("band", r.get("band"));
        m.put("weakestComponent", r.get("weakest_component"));
        m.put("methodologyVersion", r.get("methodology_version"));
        return m;
    }

    private static List<Map<String, Object>> amounts(Map<String, BigDecimal> totals) {
        return totals.entrySet().stream().map(e -> Map.<String, Object>of("category", e.getKey(), "amount", Money.str(e.getValue()))).toList();
    }

    private static String pctStr(BigDecimal n, BigDecimal d) {
        BigDecimal p = Money.pct(n, d);
        return p == null ? null : p.toPlainString();
    }

    private static String priority(String raw) {
        if (raw == null) return "MEDIUM";
        return switch (raw.toUpperCase()) {
            case "HIGH", "CRITICAL" -> "HIGH";
            case "LOW" -> "LOW";
            default -> "MEDIUM";
        };
    }

    private static Map<String, Object> action(String key, String type, String priority, String title, String reason, String amount,
                                              List<Map<String, Object>> evidence, String link, Object entityId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("type", type);
        m.put("priority", priority);
        m.put("title", title);
        m.put("reason", reason);
        m.put("amount", amount);
        m.put("evidence", evidence);
        m.put("link", link);
        m.put("entityId", entityId);
        return m;
    }

    private static Map<String, Object> evidence(String source, Object id, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", source);
        m.put("id", id);
        m.put("detail", detail);
        return m;
    }
}
