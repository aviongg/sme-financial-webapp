package com.app.sme_health_backend.intelligence;

import com.app.sme_health_backend.productsupport.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic break-even analysis ({@value #VERSION}) over the accrual P&amp;L. Cost behaviour is NEVER inferred:
 * <ul>
 *   <li>Policy-variable by definition: inventory cost of sales (sale/project/sample consumption) and recognized
 *       commission.</li>
 *   <li>Policy-fixed period costs: internal consumption, wastage and count adjustments.</li>
 *   <li>Every other expense needs an explicit FIXED/VARIABLE classification (per fact or per category). If any
 *       expense is unclassified the result is UNAVAILABLE and lists exactly what is missing.</li>
 * </ul>
 * Realized FX is a financial item and excluded. Unit break-even is offered only for a single-product period
 * (exactly one item sold); multi-product contribution is never allocated.
 */
@Service
public class BreakEvenService {
    public static final String VERSION = "break-even-v1";

    private final ProfitAndLossService pnl;
    private final JdbcTemplate jdbc;

    public BreakEvenService(ProfitAndLossService pnl, JdbcTemplate jdbc) {
        this.pnl = pnl;
        this.jdbc = jdbc;
    }

    /** Pure calculation, separately unit-tested. */
    public record Result(String status, String reason, BigDecimal revenue, BigDecimal variableCosts,
                         BigDecimal fixedCosts, BigDecimal contribution, BigDecimal contributionMarginPct,
                         BigDecimal breakEvenRevenue, BigDecimal marginOfSafety, BigDecimal marginOfSafetyPct) { }

    public static Result calculate(BigDecimal revenue, BigDecimal variable, BigDecimal fixed) {
        BigDecimal contribution = revenue.subtract(variable);
        if (revenue.signum() <= 0) {
            return new Result("UNAVAILABLE", "NO_REVENUE", revenue, variable, fixed, contribution, null, null, null, null);
        }
        BigDecimal cmPct = Money.pct(contribution, revenue);
        if (contribution.signum() <= 0) {
            return new Result("NOT_ACHIEVABLE", contribution.signum() == 0 ? "ZERO_CONTRIBUTION" : "NEGATIVE_CONTRIBUTION",
                    revenue, variable, fixed, contribution, cmPct, null, null, null);
        }
        // BER = F / (C / R) = F * R / C, computed in one exact division then rounded once (HALF_UP, scale 2).
        BigDecimal ber = fixed.multiply(revenue).divide(contribution, 2, RoundingMode.HALF_UP);
        BigDecimal mos = revenue.subtract(ber);
        return new Result("AVAILABLE", null, revenue, variable, fixed, contribution, cmPct, ber, mos, Money.pct(mos, revenue));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> analyse(UUID businessId, LocalDate from, LocalDate to) {
        ProfitAndLossService.Statement s = pnl.statement(businessId, from, to);
        BigDecimal variable = s.costOfSales().add(s.commissionCost());
        BigDecimal fixed = s.inventoryLosses().add(s.supplierReturnValue());
        List<Map<String, Object>> missing = new ArrayList<>();
        BigDecimal unclassifiedAmount = Money.ZERO;
        for (ProfitAndLossService.Fact f : s.expenseFacts()) {
            if (f.behavior() == CostBehavior.VARIABLE) {
                variable = variable.add(f.amount());
            } else if (f.behavior() == CostBehavior.FIXED) {
                fixed = fixed.add(f.amount());
            } else {
                unclassifiedAmount = unclassifiedAmount.add(f.amount());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("sourceKind", f.sourceKind());
                m.put("sourceId", f.sourceId());
                m.put("date", f.date() == null ? null : f.date().toString());
                m.put("amount", Money.str(f.amount()));
                m.put("label", f.label());
                m.put("category", f.category());
                m.put("missing", f.category() == null ? "CATEGORY_AND_COST_BEHAVIOR" : "COST_BEHAVIOR");
                missing.add(m);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from.toString());
        body.put("to", to.toString());
        body.put("version", VERSION);
        body.put("source", ProfitAndLossService.VERSION);
        if (!missing.isEmpty()) {
            body.put("status", "UNAVAILABLE");
            body.put("reason", "UNCLASSIFIED_COST_BEHAVIOR");
            body.put("unclassifiedExpenseAmount", Money.str(unclassifiedAmount));
            body.put("missingClassifications", missing.size() > 100 ? missing.subList(0, 100) : missing);
            body.put("missingCount", missing.size());
            body.put("revenue", Money.str(s.revenue()));
            body.put("classifiedVariableCosts", Money.str(variable));
            body.put("classifiedFixedCosts", Money.str(fixed));
            return body;
        }
        Result r = calculate(s.revenue(), variable, fixed);
        body.put("status", r.status());
        body.put("reason", r.reason());
        body.put("revenue", Money.str(r.revenue()));
        body.put("variableCosts", Money.str(r.variableCosts()));
        body.put("fixedCosts", Money.str(r.fixedCosts()));
        body.put("contribution", Money.str(r.contribution()));
        body.put("contributionMarginPct", r.contributionMarginPct() == null ? null : r.contributionMarginPct().toPlainString());
        body.put("breakEvenRevenue", Money.str(r.breakEvenRevenue()));
        body.put("marginOfSafety", Money.str(r.marginOfSafety()));
        body.put("marginOfSafetyPct", r.marginOfSafetyPct() == null ? null : r.marginOfSafetyPct().toPlainString());
        body.put("unitEconomics", unitEconomics(businessId, from, to, r, s.revenueFacts().stream()
                .allMatch(f -> "PRODUCT_SALES".equals(f.category()))));
        body.put("policyNotes", List.of("COST_OF_SALES_AND_COMMISSION_VARIABLE", "INVENTORY_LOSSES_FIXED",
                "REALIZED_FX_EXCLUDED", "NO_MULTI_PRODUCT_ALLOCATION"));
        return body;
    }

    private Map<String, Object> unitEconomics(UUID businessId, LocalDate from, LocalDate to, Result r, boolean allProductSales) {
        List<Map<String, Object>> sold = jdbc.queryForList("""
                SELECT m.item_id, i.name, i.unit, -sum(m.quantity_delta) AS qty
                FROM inventory_movements m JOIN inventory_items i ON i.business_id = m.business_id AND i.id = m.item_id
                WHERE m.business_id = ? AND m.purpose = 'SALE' AND m.movement_date BETWEEN ? AND ?
                GROUP BY m.item_id, i.name, i.unit HAVING -sum(m.quantity_delta) > 0
                """, businessId, Date.valueOf(from), Date.valueOf(to));
        Map<String, Object> body = new LinkedHashMap<>();
        if (sold.isEmpty()) {
            body.put("status", "UNAVAILABLE");
            body.put("reason", "NO_TRACKED_UNIT_SALES");
            return body;
        }
        if (sold.size() > 1) {
            body.put("status", "UNAVAILABLE");
            body.put("reason", "MULTI_PRODUCT_NO_ALLOCATION");
            body.put("itemsSold", sold.size());
            return body;
        }
        if (!allProductSales) {
            // Unit economics would silently attribute service/other revenue to the item; refuse instead.
            body.put("status", "UNAVAILABLE");
            body.put("reason", "REVENUE_NOT_ALL_CLASSIFIED_PRODUCT_SALES");
            return body;
        }
        if (!"AVAILABLE".equals(r.status())) {
            body.put("status", "UNAVAILABLE");
            body.put("reason", r.reason());
            return body;
        }
        BigDecimal units = (BigDecimal) sold.get(0).get("qty");
        BigDecimal unitPrice = r.revenue().divide(units, 4, RoundingMode.HALF_UP);
        BigDecimal unitVariable = r.variableCosts().divide(units, 4, RoundingMode.HALF_UP);
        BigDecimal unitContribution = r.contribution().divide(units, 4, RoundingMode.HALF_UP);
        BigDecimal breakEvenUnits = r.fixedCosts().multiply(units).divide(r.contribution(), 0, RoundingMode.CEILING);
        body.put("status", "AVAILABLE");
        body.put("itemId", sold.get(0).get("item_id"));
        body.put("itemName", sold.get(0).get("name"));
        body.put("unit", sold.get(0).get("unit"));
        body.put("unitsSold", units.stripTrailingZeros().toPlainString());
        body.put("averageUnitRevenue", Money.str(unitPrice));
        body.put("averageUnitVariableCost", Money.str(unitVariable));
        body.put("unitContribution", Money.str(unitContribution));
        body.put("breakEvenUnits", breakEvenUnits.toPlainString());
        return body;
    }
}
