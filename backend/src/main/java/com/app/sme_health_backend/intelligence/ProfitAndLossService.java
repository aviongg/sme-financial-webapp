package com.app.sme_health_backend.intelligence;

import com.app.sme_health_backend.inventory.ledger.InventoryReadService;
import com.app.sme_health_backend.productsupport.Money;
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
 * Accrual profit-and-loss read model ({@value #VERSION}) composed ONLY from existing authorities; it posts nothing
 * and never replaces Developer A's cash-basis monthly bridge (c02-bridge-v1).
 *
 * <pre>
 * Revenue          = cash sales (DIRECT_SALE_RECEIPT)            [ledger]
 *                  + recognized customer invoices (issue date)   [C04 obligations, source CUSTOMER_INVOICE, OPEN]
 *                  - effective credit notes (note date)          [F04 commercial_adjustments]
 * Cost of sales    = inventory SALE + PROJECT + SAMPLE consumption (net of returns/reversals)  [F08]
 * Operating costs  = operating payments (OPERATING_EXPENSE_PAYMENT, un-reversed)
 *                      - amounts capitalized into inventory from those payments            [F08]
 *                      - commission payouts/advances (replaced by recognized commission)   [C08]
 *                  + recognized supplier bills - amounts capitalized into stock             [C04 + F08]
 *                  - effective debit notes + value of stock returned to suppliers           [F04 + F08]
 *                  + recognized commission cost (earnings not void/reversed)               [C08]
 *                  + internal consumption, wastage and net count adjustments               [F08]
 * Other            = realized FX gain/loss on settled foreign obligations                   [C09]
 * </pre>
 * Collections (RECEIVABLE_COLLECTION), bill settlements (PAYABLE_SETTLEMENT) and internal transfers are cash
 * timing only and never appear here, so nothing is counted twice.
 */
@Service
public class ProfitAndLossService {
    public static final String VERSION = "pnl-accrual-v1";

    private final JdbcTemplate jdbc;
    private final InventoryReadService inventory;

    public ProfitAndLossService(JdbcTemplate jdbc, InventoryReadService inventory) {
        this.jdbc = jdbc;
        this.inventory = inventory;
    }

    /** One classified cost or revenue fact. */
    public record Fact(String sourceKind, UUID sourceId, LocalDate date, BigDecimal amount, String category,
                       CostBehavior behavior, String label, UUID counterpartyId) { }

    public record Statement(
            LocalDate from, LocalDate to,
            BigDecimal cashSales, BigDecimal invoiceRevenue, BigDecimal creditNotes, BigDecimal revenue,
            BigDecimal costOfGoodsSold, BigDecimal projectMaterial, BigDecimal sampleMaterial, BigDecimal costOfSales,
            BigDecimal grossProfit,
            BigDecimal cashOperatingExpenses, BigDecimal billExpenses, BigDecimal debitNotes,
            BigDecimal supplierReturnValue, BigDecimal commissionCost, BigDecimal inventoryLosses,
            BigDecimal operatingExpenses, BigDecimal operatingProfit,
            BigDecimal realizedFx, BigDecimal netProfit,
            BigDecimal capitalizedPurchases,
            List<Fact> revenueFacts, List<Fact> expenseFacts,
            BigDecimal unclassifiedRevenue, BigDecimal unclassifiedExpenses,
            boolean hasAnyData
    ) {
        public BigDecimal grossMarginPct() {
            return Money.pct(grossProfit, revenue);
        }

        public BigDecimal operatingMarginPct() {
            return Money.pct(operatingProfit, revenue);
        }
    }

    @Transactional(readOnly = true)
    public Statement statement(UUID businessId, LocalDate from, LocalDate to) {
        Map<String, String[]> classifications = classifications(businessId);
        Map<String, CostBehavior> categoryBehavior = categoryBehaviors(businessId);
        Map<UUID, BigDecimal> capitalizedTx = inventory.capitalizedTransactions(businessId);

        // ------------------------------------------------------------ revenue
        List<Fact> revenueFacts = new ArrayList<>();
        BigDecimal cashSales = Money.ZERO;
        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT t.id, t.effective_date, t.base_amount_pkr, t.counterparty_id, t.description FROM financial_transactions t
                WHERE t.business_id = ? AND t.event_type = 'DIRECT_SALE_RECEIPT' AND t.effective_date BETWEEN ? AND ?
                  AND t.reversed_transaction_id IS NULL
                  AND NOT EXISTS (SELECT 1 FROM financial_transactions r WHERE r.business_id = t.business_id
                                  AND r.reversed_transaction_id = t.id)
                """, businessId, Date.valueOf(from), Date.valueOf(to))) {
            BigDecimal amount = Money.nz((BigDecimal) row.get("base_amount_pkr"));
            cashSales = cashSales.add(amount);
            UUID id = (UUID) row.get("id");
            revenueFacts.add(fact("TRANSACTION", id, date(row.get("effective_date")), amount, classifications, categoryBehavior,
                    (String) row.get("description"), (UUID) row.get("counterparty_id")));
        }
        BigDecimal invoiceRevenue = Money.ZERO;
        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT o.source_id, o.issue_date, o.original_amount, o.counterparty_id, o.reference_number
                FROM financial_obligations o
                WHERE o.business_id = ? AND o.source_type = 'CUSTOMER_INVOICE' AND o.status = 'OPEN'
                  AND o.issue_date BETWEEN ? AND ?
                """, businessId, Date.valueOf(from), Date.valueOf(to))) {
            BigDecimal amount = Money.nz((BigDecimal) row.get("original_amount"));
            invoiceRevenue = invoiceRevenue.add(amount);
            revenueFacts.add(fact("COMMERCIAL_DOCUMENT", UUID.fromString((String) row.get("source_id")), date(row.get("issue_date")),
                    amount, classifications, categoryBehavior, (String) row.get("reference_number"), (UUID) row.get("counterparty_id")));
        }
        BigDecimal creditNotes = Money.ZERO;
        for (Map<String, Object> row : adjustments(businessId, "CREDIT_NOTE", from, to)) {
            BigDecimal amount = Money.nz((BigDecimal) row.get("amount"));
            creditNotes = creditNotes.add(amount);
            revenueFacts.add(fact("COMMERCIAL_DOCUMENT", (UUID) row.get("document_id"), date(row.get("adjustment_date")),
                    amount.negate(), classifications, categoryBehavior, (String) row.get("adjustment_number"),
                    (UUID) row.get("counterparty_id")));
        }
        BigDecimal revenue = cashSales.add(invoiceRevenue).subtract(creditNotes);

        // ------------------------------------------------------------ cost of sales (F08)
        InventoryReadService.ConsumptionSummary consumption = inventory.consumption(businessId, from, to);
        BigDecimal costOfSales = consumption.costOfGoodsSold().add(consumption.projectMaterial()).add(consumption.sampleMaterial());
        BigDecimal grossProfit = revenue.subtract(costOfSales);

        // ------------------------------------------------------------ operating costs
        List<Fact> expenseFacts = new ArrayList<>();
        BigDecimal cashExpenses = Money.ZERO;
        BigDecimal capitalized = Money.ZERO;
        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT t.id, t.effective_date, t.base_amount_pkr, t.counterparty_id, t.description FROM financial_transactions t
                WHERE t.business_id = ? AND t.event_type = 'OPERATING_EXPENSE_PAYMENT' AND t.effective_date BETWEEN ? AND ?
                  AND t.reversed_transaction_id IS NULL
                  AND NOT EXISTS (SELECT 1 FROM financial_transactions r WHERE r.business_id = t.business_id
                                  AND r.reversed_transaction_id = t.id)
                  AND NOT EXISTS (SELECT 1 FROM commission_payouts p WHERE p.business_id = t.business_id
                                  AND p.financial_transaction_id = t.id)
                  AND NOT EXISTS (SELECT 1 FROM commission_advances a WHERE a.business_id = t.business_id
                                  AND a.financial_transaction_id = t.id)
                """, businessId, Date.valueOf(from), Date.valueOf(to))) {
            UUID id = (UUID) row.get("id");
            BigDecimal gross = Money.nz((BigDecimal) row.get("base_amount_pkr"));
            BigDecimal cap = Money.nz(capitalizedTx.get(id)).min(gross);
            capitalized = capitalized.add(cap);
            BigDecimal amount = gross.subtract(cap);
            if (amount.signum() == 0) {
                continue;
            }
            cashExpenses = cashExpenses.add(amount);
            expenseFacts.add(fact("TRANSACTION", id, date(row.get("effective_date")), amount, classifications, categoryBehavior,
                    (String) row.get("description"), (UUID) row.get("counterparty_id")));
        }
        BigDecimal billExpenses = Money.ZERO;
        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT o.source_id, o.issue_date, o.original_amount, o.counterparty_id, o.reference_number
                FROM financial_obligations o
                WHERE o.business_id = ? AND o.source_type = 'SUPPLIER_BILL' AND o.status = 'OPEN'
                  AND o.issue_date BETWEEN ? AND ?
                """, businessId, Date.valueOf(from), Date.valueOf(to))) {
            UUID docId = UUID.fromString((String) row.get("source_id"));
            BigDecimal original = Money.nz((BigDecimal) row.get("original_amount"));
            BigDecimal cap = inventory.capitalizedFromBill(businessId, docId).min(original);
            capitalized = capitalized.add(cap);
            BigDecimal amount = original.subtract(cap);
            if (amount.signum() == 0) {
                continue;
            }
            billExpenses = billExpenses.add(amount);
            expenseFacts.add(fact("COMMERCIAL_DOCUMENT", docId, date(row.get("issue_date")), amount, classifications,
                    categoryBehavior, (String) row.get("reference_number"), (UUID) row.get("counterparty_id")));
        }
        BigDecimal debitNotes = Money.ZERO;
        for (Map<String, Object> row : adjustments(businessId, "DEBIT_NOTE", from, to)) {
            BigDecimal amount = Money.nz((BigDecimal) row.get("amount"));
            debitNotes = debitNotes.add(amount);
            expenseFacts.add(fact("COMMERCIAL_DOCUMENT", (UUID) row.get("document_id"), date(row.get("adjustment_date")),
                    amount.negate(), classifications, categoryBehavior, (String) row.get("adjustment_number"),
                    (UUID) row.get("counterparty_id")));
        }
        BigDecimal supplierReturns = Money.nz(jdbc.queryForObject("""
                SELECT -COALESCE(sum(m.value_delta), 0) FROM inventory_movements m
                LEFT JOIN inventory_movements o ON o.business_id = m.business_id AND o.id = m.related_movement_id
                WHERE m.business_id = ? AND m.movement_date BETWEEN ? AND ?
                  AND (m.movement_type = 'SUPPLIER_RETURN' OR (m.movement_type = 'REVERSAL' AND o.movement_type = 'SUPPLIER_RETURN'))
                """, BigDecimal.class, businessId, Date.valueOf(from), Date.valueOf(to)));
        BigDecimal commission = Money.nz(jdbc.queryForObject("""
                SELECT COALESCE(sum(COALESCE(approved_amount, commission_amount)), 0) FROM commission_earnings
                WHERE business_id = ? AND status IN ('EARNED', 'APPROVED', 'PARTIALLY_SETTLED', 'SETTLED', 'PAID')
                  AND (created_at AT TIME ZONE 'Asia/Karachi')::date BETWEEN ? AND ?
                """, BigDecimal.class, businessId, Date.valueOf(from), Date.valueOf(to)));
        BigDecimal adjustmentIn = Money.nz(jdbc.queryForObject("""
                SELECT COALESCE(sum(m.value_delta), 0) FROM inventory_movements m
                LEFT JOIN inventory_movements o ON o.business_id = m.business_id AND o.id = m.related_movement_id
                WHERE m.business_id = ? AND m.movement_date BETWEEN ? AND ?
                  AND (m.movement_type = 'ADJUSTMENT_IN' OR (m.movement_type = 'REVERSAL' AND o.movement_type = 'ADJUSTMENT_IN'))
                """, BigDecimal.class, businessId, Date.valueOf(from), Date.valueOf(to)));
        BigDecimal inventoryLosses = consumption.internalConsumption().add(consumption.wastage())
                .add(consumption.adjustmentLoss()).subtract(adjustmentIn);
        BigDecimal operatingExpenses = cashExpenses.add(billExpenses).subtract(debitNotes).add(supplierReturns)
                .add(commission).add(inventoryLosses);
        BigDecimal operatingProfit = grossProfit.subtract(operatingExpenses);

        BigDecimal realizedFx = Money.nz(jdbc.queryForObject("""
                SELECT COALESCE(sum(CASE realized_fx_impact WHEN 'GAIN' THEN abs(realized_fx_difference)
                                                            WHEN 'LOSS' THEN -abs(realized_fx_difference) ELSE 0 END), 0)
                FROM fx_obligation_settlements
                WHERE business_id = ? AND status = 'SETTLED' AND settlement_date BETWEEN ? AND ?
                """, BigDecimal.class, businessId, Date.valueOf(from), Date.valueOf(to)));
        BigDecimal netProfit = operatingProfit.add(realizedFx);

        BigDecimal unclassifiedRevenue = revenueFacts.stream().filter(f -> f.category() == null)
                .map(Fact::amount).reduce(Money.ZERO, BigDecimal::add);
        BigDecimal unclassifiedExpenses = expenseFacts.stream().filter(f -> f.category() == null)
                .map(Fact::amount).reduce(Money.ZERO, BigDecimal::add);
        boolean hasData = !revenueFacts.isEmpty() || !expenseFacts.isEmpty() || costOfSales.signum() != 0
                || commission.signum() != 0 || inventoryLosses.signum() != 0 || supplierReturns.signum() != 0;

        return new Statement(from, to, cashSales, invoiceRevenue, creditNotes, revenue,
                consumption.costOfGoodsSold(), consumption.projectMaterial(), consumption.sampleMaterial(), costOfSales,
                grossProfit, cashExpenses, billExpenses, debitNotes, supplierReturns, commission, inventoryLosses,
                operatingExpenses, operatingProfit, realizedFx, netProfit, capitalized,
                List.copyOf(revenueFacts), List.copyOf(expenseFacts), unclassifiedRevenue, unclassifiedExpenses, hasData);
    }

    /** Category totals for a fact list; null category is reported under "UNCLASSIFIED". */
    public static Map<String, BigDecimal> byCategory(List<Fact> facts) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (Fact f : facts) {
            totals.merge(f.category() == null ? "UNCLASSIFIED" : f.category(), f.amount(), BigDecimal::add);
        }
        return totals;
    }

    private List<Map<String, Object>> adjustments(UUID businessId, String kind, LocalDate from, LocalDate to) {
        return jdbc.queryForList("""
                SELECT a.document_id, a.adjustment_date, a.amount, a.adjustment_number, o.counterparty_id
                FROM commercial_adjustments a
                JOIN financial_obligations o ON o.business_id = a.business_id AND o.id = a.obligation_id
                WHERE a.business_id = ? AND a.adjustment_kind = ? AND a.status = 'EFFECTIVE' AND o.status = 'OPEN'
                  AND a.adjustment_date BETWEEN ? AND ?
                """, businessId, kind, Date.valueOf(from), Date.valueOf(to));
    }

    private Map<String, String[]> classifications(UUID businessId) {
        Map<String, String[]> map = new LinkedHashMap<>();
        jdbc.query("SELECT source_kind, source_id, category, cost_behavior FROM financial_classifications WHERE business_id = ?",
                rs -> {
                    map.put(rs.getString(1) + ":" + rs.getObject(2, UUID.class), new String[] {rs.getString(3), rs.getString(4)});
                }, businessId);
        return map;
    }

    private Map<String, CostBehavior> categoryBehaviors(UUID businessId) {
        Map<String, CostBehavior> map = new LinkedHashMap<>();
        jdbc.query("SELECT category, cost_behavior FROM category_cost_behaviors WHERE business_id = ?",
                rs -> {
                    map.put(rs.getString(1), CostBehavior.valueOf(rs.getString(2)));
                }, businessId);
        return map;
    }

    private static Fact fact(String kind, UUID id, LocalDate date, BigDecimal amount, Map<String, String[]> classifications,
                             Map<String, CostBehavior> categoryBehavior, String label, UUID counterpartyId) {
        String[] c = classifications.get(kind + ":" + id);
        String category = c == null ? null : c[0];
        CostBehavior behavior = c != null && c[1] != null ? CostBehavior.valueOf(c[1])
                : category == null ? null : categoryBehavior.get(category);
        return new Fact(kind, id, date, amount, category, behavior, label, counterpartyId);
    }

    private static LocalDate date(Object value) {
        if (value instanceof Date d) return d.toLocalDate();
        if (value instanceof LocalDate d) return d;
        return value == null ? null : LocalDate.parse(value.toString());
    }
}
