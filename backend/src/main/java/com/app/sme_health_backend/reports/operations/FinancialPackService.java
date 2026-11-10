package com.app.sme_health_backend.reports.operations;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.intelligence.CashReadService;
import com.app.sme_health_backend.intelligence.PlanningController;
import com.app.sme_health_backend.intelligence.PlanningService;
import com.app.sme_health_backend.intelligence.ProfitAndLossService;
import com.app.sme_health_backend.inventory.ledger.InventoryReadService;
import com.app.sme_health_backend.obligation.FinancialObligationService;
import com.app.sme_health_backend.obligation.aging.DirectionAgingSummary;
import com.app.sme_health_backend.obligation.aging.ObligationAgingResult;
import com.app.sme_health_backend.productsupport.DomainException;
import com.app.sme_health_backend.productsupport.Money;
import com.app.sme_health_backend.samples.actuals.SampleActualsService;
import com.app.sme_health_backend.projects.profitability.ProjectProfitabilityService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * F12B final reporting pack ({@value #VERSION}): one read-only, period-bound report assembled from the final
 * authorities (ledger cash, accrual P&amp;L, C04 aging, F05 projects, budgets/goals, F08 inventory/COGS, C08
 * commissions, C09 FX, F09 samples, health score). CSV export is per section; print/PDF is the browser print view.
 * No figure is recalculated outside its authority.
 */
@Service
public class FinancialPackService {
    public static final String VERSION = "f12b-pack-v1";
    public static final List<String> SECTIONS = List.of("profit_and_loss", "cash", "aging", "projects", "budgets", "goals",
            "inventory", "commissions", "fx", "samples", "health");

    private final JdbcTemplate jdbc;
    private final ProfitAndLossService pnl;
    private final CashReadService cash;
    private final FinancialObligationService obligations;
    private final InventoryReadService inventory;
    private final PlanningService planning;
    private final ProjectProfitabilityService projects;
    private final SampleActualsService samples;

    public FinancialPackService(JdbcTemplate jdbc, ProfitAndLossService pnl, CashReadService cash,
                                FinancialObligationService obligations, InventoryReadService inventory, PlanningService planning,
                                ProjectProfitabilityService projects, SampleActualsService samples) {
        this.jdbc = jdbc;
        this.pnl = pnl;
        this.cash = cash;
        this.obligations = obligations;
        this.inventory = inventory;
        this.planning = planning;
        this.projects = projects;
        this.samples = samples;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pack(BusinessAccessContext ctx, LocalDate from, LocalDate to) {
        UUID b = ctx.businessId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", VERSION);
        body.put("from", from.toString());
        body.put("to", to.toString());
        body.put("currency", "PKR");

        body.put("profit_and_loss", PlanningController.statementBody(pnl.statement(b, from, to)));

        Map<String, Object> cashSection = new LinkedHashMap<>();
        boolean hasAccounts = cash.hasAccounts(b);
        cashSection.put("status", hasAccounts ? "AVAILABLE" : "NO_ACCOUNTS");
        if (hasAccounts) {
            BigDecimal opening = cash.cashBalancePkr(b, from.minusDays(1));
            BigDecimal closing = cash.cashBalancePkr(b, to);
            cashSection.put("openingBalance", Money.str(opening));
            cashSection.put("closingBalance", Money.str(closing));
            cashSection.put("netChange", Money.str(closing.subtract(opening)));
            List<Map<String, Object>> flows = new ArrayList<>();
            for (Map<String, Object> r : jdbc.queryForList("""
                    SELECT t.event_type, COALESCE(sum(t.base_amount_pkr), 0) AS total, count(*) AS n FROM financial_transactions t
                    WHERE t.business_id = ? AND t.effective_date BETWEEN ? AND ? AND t.reversed_transaction_id IS NULL
                      AND NOT EXISTS (SELECT 1 FROM financial_transactions r WHERE r.business_id = t.business_id AND r.reversed_transaction_id = t.id)
                    GROUP BY t.event_type ORDER BY t.event_type
                    """, b, Date.valueOf(from), Date.valueOf(to))) {
                flows.add(Map.of("eventType", r.get("event_type"), "amount", Money.str((BigDecimal) r.get("total")), "count", r.get("n")));
            }
            cashSection.put("flowsByEventType", flows);
        }
        body.put("cash", cashSection);

        ObligationAgingResult aging = obligations.getAging(ctx, to.toString());
        Map<String, Object> agingSection = new LinkedHashMap<>();
        agingSection.put("asOf", aging.asOfDate().toString());
        agingSection.put("receivables", agingSummary(aging.receivables()));
        agingSection.put("payables", agingSummary(aging.payables()));
        body.put("aging", agingSection);

        List<Map<String, Object>> projectRows = new ArrayList<>();
        for (Map<String, Object> p : jdbc.queryForList("SELECT id FROM projects WHERE business_id = ? AND status NOT IN ('ARCHIVED') "
                + "ORDER BY code LIMIT 100", b)) {
            Map<String, Object> prof = projects.profitability(b, (UUID) p.get("id"));
            @SuppressWarnings("unchecked") Map<String, Object> rev = (Map<String, Object>) prof.get("revenue");
            @SuppressWarnings("unchecked") Map<String, Object> cost = (Map<String, Object>) prof.get("costs");
            @SuppressWarnings("unchecked") Map<String, Object> res = (Map<String, Object>) prof.get("profitability");
            @SuppressWarnings("unchecked") Map<String, Object> plan = (Map<String, Object>) prof.get("plan");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", prof.get("code"));
            row.put("name", prof.get("name"));
            row.put("status", prof.get("status"));
            row.put("revenue", rev.get("total"));
            row.put("cost", cost.get("total"));
            row.put("profit", res.get("profit"));
            row.put("marginPct", res.get("marginPct"));
            row.put("plannedCost", plan.get("plannedCost"));
            row.put("costVariance", plan.get("costVariance"));
            row.put("profitabilityStatus", res.get("status"));
            projectRows.add(row);
        }
        body.put("projects", projectRows);

        body.put("budgets", planning.budgets(ctx).stream().map(x -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", x.get("name"));
            m.put("periodStart", x.get("periodStart"));
            m.put("periodEnd", x.get("periodEnd"));
            m.put("status", x.get("status"));
            @SuppressWarnings("unchecked") Map<String, Object> v = (Map<String, Object>) x.get("variance");
            m.put("budgetRevenue", v == null ? null : v.get("budgetRevenue"));
            m.put("actualRevenue", v == null ? null : v.get("actualRevenue"));
            m.put("budgetExpense", v == null ? null : v.get("budgetExpense"));
            m.put("actualExpense", v == null ? null : v.get("actualExpense"));
            m.put("lines", v == null ? List.of() : v.get("lines"));
            return m;
        }).toList());
        body.put("goals", planning.goals(ctx));

        InventoryReadService.Register register = inventory.register(b);
        InventoryReadService.ConsumptionSummary consumption = inventory.consumption(b, from, to);
        Map<String, Object> inv = new LinkedHashMap<>();
        inv.put("carryingValue", Money.str(register.totalValue()));
        inv.put("valueAtPeriodEnd", Money.str(inventory.valueAsOf(b, to)));
        inv.put("costOfGoodsSold", Money.str(consumption.costOfGoodsSold()));
        inv.put("projectMaterial", Money.str(consumption.projectMaterial()));
        inv.put("sampleMaterial", Money.str(consumption.sampleMaterial()));
        inv.put("wastageAndAdjustments", Money.str(consumption.wastage().add(consumption.adjustmentLoss())));
        inv.put("itemsBelowReorder", register.itemsBelowReorder());
        inv.put("policyVersion", register.policyVersion());
        inv.put("items", register.items().stream().filter(InventoryReadService.PositionView::hasMovements).map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sku", p.sku());
            m.put("name", p.name());
            m.put("quantity", p.quantity().stripTrailingZeros().toPlainString());
            m.put("value", Money.str(p.value()));
            m.put("averageUnitCost", p.averageUnitCost() == null ? null : p.averageUnitCost().stripTrailingZeros().toPlainString());
            return m;
        }).toList());
        body.put("inventory", inv);

        Map<String, Object> commissions = new LinkedHashMap<>();
        commissions.put("recognizedCost", Money.str(Money.nz(jdbc.queryForObject("""
                SELECT COALESCE(sum(COALESCE(approved_amount, commission_amount)), 0) FROM commission_earnings
                WHERE business_id = ? AND status IN ('EARNED', 'APPROVED', 'PARTIALLY_SETTLED', 'SETTLED', 'PAID')
                  AND (created_at AT TIME ZONE 'Asia/Karachi')::date BETWEEN ? AND ?
                """, BigDecimal.class, b, Date.valueOf(from), Date.valueOf(to)))));
        commissions.put("paidInPeriod", Money.str(Money.nz(jdbc.queryForObject("""
                SELECT COALESCE(sum(amount), 0) FROM commission_payouts
                WHERE business_id = ? AND payout_type = 'PAYOUT' AND payment_date BETWEEN ? AND ? AND reversal_transaction_id IS NULL
                """, BigDecimal.class, b, Date.valueOf(from), Date.valueOf(to)))));
        body.put("commissions", commissions);

        Map<String, Object> fx = new LinkedHashMap<>();
        fx.put("realizedGainLoss", PlanningController.statementBody(pnl.statement(b, from, to)).get("realizedFx"));
        fx.put("settlements", jdbc.queryForObject("SELECT count(*) FROM fx_obligation_settlements WHERE business_id = ? "
                + "AND status = 'SETTLED' AND settlement_date BETWEEN ? AND ?", Integer.class, b, Date.valueOf(from), Date.valueOf(to)));
        body.put("fx", fx);

        List<Map<String, Object>> sampleRows = new ArrayList<>();
        for (Map<String, Object> s : jdbc.queryForList("SELECT id FROM sample_activities WHERE business_id = ? AND status <> 'ARCHIVED' "
                + "ORDER BY code LIMIT 100", b)) {
            Map<String, Object> a = samples.actuals(b, (UUID) s.get("id"));
            @SuppressWarnings("unchecked") Map<String, Object> c = (Map<String, Object>) a.get("cost");
            @SuppressWarnings("unchecked") Map<String, Object> r = (Map<String, Object>) a.get("recovery");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", a.get("code"));
            row.put("name", a.get("name"));
            row.put("recoveryIntent", a.get("recoveryIntent"));
            row.put("actualCost", c.get("total"));
            row.put("recognizedRecovery", r.get("recognized"));
            row.put("outstandingRecovery", r.get("outstanding"));
            row.put("contribution", a.get("contribution"));
            row.put("recoveryStatus", r.get("status"));
            sampleRows.add(row);
        }
        body.put("samples", sampleRows);

        List<Map<String, Object>> scores = jdbc.queryForList("SELECT month, composite_score, band FROM score_results "
                + "WHERE user_id = ? ORDER BY month DESC LIMIT 6", b);
        body.put("health", scores.stream().map(r -> Map.of("month", r.get("month"),
                "compositeScore", r.get("composite_score") == null ? "" : r.get("composite_score").toString(),
                "band", r.get("band") == null ? "" : r.get("band"))).toList());
        return body;
    }

    /** RFC 4180 CSV for one section, with a provenance header row. */
    @Transactional(readOnly = true)
    public String csv(BusinessAccessContext ctx, LocalDate from, LocalDate to, String section) {
        if (!SECTIONS.contains(section)) throw DomainException.invalid("section", "enum_invalid");
        Map<String, Object> pack = pack(ctx, from, to);
        Object data = pack.get(section);
        StringBuilder out = new StringBuilder();
        out.append(row(List.of("FinSight " + VERSION, section, from.toString(), to.toString(), "PKR")));
        if (data instanceof List<?> list) {
            if (list.isEmpty()) {
                out.append(row(List.of("no_rows")));
            } else {
                @SuppressWarnings("unchecked") Map<String, Object> first = (Map<String, Object>) list.get(0);
                List<String> headers = new ArrayList<>(first.keySet());
                headers.removeIf(h -> first.get(h) instanceof List || first.get(h) instanceof Map);
                out.append(row(headers));
                for (Object o : list) {
                    @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
                    List<String> values = new ArrayList<>();
                    for (String h : headers) values.add(m.get(h) == null ? "" : m.get(h).toString());
                    out.append(row(values));
                }
            }
        } else if (data instanceof Map<?, ?> map) {
            out.append(row(List.of("metric", "value")));
            flatten("", map, out);
        }
        return out.toString();
    }

    private static void flatten(String prefix, Map<?, ?> map, StringBuilder out) {
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey().toString() : prefix + "." + e.getKey();
            Object v = e.getValue();
            if (v instanceof Map<?, ?> nested) {
                flatten(key, nested, out);
            } else if (v instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) instanceof Map<?, ?> m) flatten(key + "[" + i + "]", m, out);
                    else out.append(row(List.of(key + "[" + i + "]", String.valueOf(list.get(i)))));
                }
            } else {
                out.append(row(List.of(key, v == null ? "" : v.toString())));
            }
        }
    }

    static String row(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            String v = values.get(i) == null ? "" : values.get(i);
            // Neutralize spreadsheet formula injection, then quote.
            if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0 && !v.matches("-?[0-9]+(\\.[0-9]+)?")) v = "'" + v;
            sb.append('"').append(v.replace("\"", "\"\"")).append('"');
        }
        return sb.append("\r\n").toString();
    }

    private static Map<String, Object> agingSummary(DirectionAgingSummary s) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (s == null) return m;
        m.put("totalOutstanding", Money.str(s.totalOutstanding()));
        m.put("overdueOutstanding", Money.str(s.overdueOutstanding()));
        m.put("activeCount", s.totalActiveCount());
        m.put("overdueCount", s.overdueCount());
        Map<String, Object> buckets = new LinkedHashMap<>();
        s.buckets().forEach((k, v) -> buckets.put(k.name(), v));
        m.put("buckets", buckets);
        return m;
    }
}
