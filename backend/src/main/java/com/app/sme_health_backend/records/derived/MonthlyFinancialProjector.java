package com.app.sme_health_backend.records.derived;

import com.app.sme_health_backend.intelligence.ProfitAndLossService;
import com.app.sme_health_backend.inventory.ledger.InventoryReadService;
import com.app.sme_health_backend.productsupport.Hashing;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * monthly-projection-v1: derives every {@code MonthlyRecord} field for one business and month from the authoritative
 * daily records. Pure read; deterministic for a given database state (no clock other than the explicit
 * {@code today} that decides whether the period is complete).
 *
 * <p>Source map (period = calendar month in the business's ledger dates):
 * <ul>
 *   <li>revenue, operating expenses, cost of sales: accrual P&amp;L ({@link ProfitAndLossService}, pnl-accrual-v1)</li>
 *   <li>cash inflow/outflow: posted movements on base-currency (PKR) accounts within the month; internal transfers
 *       and reversed/reversal transaction pairs excluded</li>
 *   <li>ending cash: PKR account opening balances + movements up to month end (reversal pairs excluded)</li>
 *   <li>receivables/payables: PKR obligations issued by month end, minus effective allocations dated by month end</li>
 *   <li>inventory value: F08 carrying value at month end (null when the business has never recorded stock)</li>
 *   <li>loan outstanding, interest expense, financing type: supplemental manual inputs (no ledger authority)</li>
 * </ul>
 */
@Component
public class MonthlyFinancialProjector {

    public static final String VERSION = "monthly-projection-v1";
    public static final String BASE_CURRENCY = "PKR";

    private static final String NOT_VOID_PAIR = " " + """
            t.reversed_transaction_id IS NULL
            AND NOT EXISTS (SELECT 1 FROM financial_transactions r
                            WHERE r.business_id = t.business_id AND r.reversed_transaction_id = t.id)
            """;

    private final JdbcTemplate jdbc;
    private final ProfitAndLossService profitAndLoss;
    private final InventoryReadService inventory;

    public MonthlyFinancialProjector(JdbcTemplate jdbc, ProfitAndLossService profitAndLoss, InventoryReadService inventory) {
        this.jdbc = jdbc;
        this.profitAndLoss = profitAndLoss;
        this.inventory = inventory;
    }

    /** Field-level provenance for the derived month (also the API's source map). */
    public record FieldSource(String field, BigDecimal value, String basis, String source) { }

    public record DerivedMonth(
            String month,
            BigDecimal cashInflow, BigDecimal cashOutflow, BigDecimal revenue, BigDecimal cogs,
            BigDecimal operatingExpenses, BigDecimal cashBalanceEom,
            BigDecimal receivablesOutstanding, BigDecimal payablesOutstanding, BigDecimal inventoryValue,
            BigDecimal loanOutstanding, BigDecimal interestExpense, String financingType,
            boolean periodComplete, List<String> notes, String fingerprint,
            ProfitAndLossService.Statement statement, List<FieldSource> sources
    ) { }

    /** First month of ledger coverage: the earliest base-currency cash account opening, if any. */
    public Optional<YearMonth> coverageStart(UUID businessId) {
        Date earliest = jdbc.queryForObject("""
                SELECT min(opening_date) FROM financial_accounts
                WHERE business_id = ? AND upper(currency) = 'PKR' AND opening_date IS NOT NULL
                """, Date.class, businessId);
        return Optional.ofNullable(earliest).map(d -> YearMonth.from(d.toLocalDate()));
    }

    public DerivedMonth project(UUID businessId, YearMonth month, LocalDate today) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        List<String> notes = new ArrayList<>();

        // ---------------------------------------------------------------- accrual performance (P&L authority)
        ProfitAndLossService.Statement pnl = profitAndLoss.statement(businessId, from, to);
        boolean inventoryTracked = inventoryTrackedBy(businessId, to);
        BigDecimal revenue = money(pnl.revenue());
        BigDecimal operatingExpenses = money(pnl.operatingExpenses());
        BigDecimal cogs = inventoryTracked ? money(pnl.costOfSales()) : null;
        if (!inventoryTracked) {
            notes.add("cogs_unknown_no_inventory_ledger");
        }
        if (pnl.realizedFx().signum() != 0) {
            notes.add("realized_fx_excluded_from_operating_figures");
        }

        // ---------------------------------------------------------------- cash (ledger authority, PKR accounts)
        Map<String, Object> flows = jdbc.queryForMap("""
                SELECT COALESCE(sum(m.amount) FILTER (WHERE m.movement_direction = 'INCREASE'), 0) AS inflow,
                       COALESCE(sum(m.amount) FILTER (WHERE m.movement_direction = 'DECREASE'), 0) AS outflow
                FROM account_movements m
                JOIN financial_accounts a ON a.id = m.account_id AND a.business_id = m.business_id
                JOIN financial_transactions t ON t.id = m.transaction_id AND t.business_id = m.business_id
                WHERE m.business_id = ? AND upper(a.currency) = 'PKR' AND m.effective_date BETWEEN ? AND ?
                  AND t.event_type <> 'INTERNAL_TRANSFER'
                  AND """ + NOT_VOID_PAIR, businessId, Date.valueOf(from), Date.valueOf(to));
        BigDecimal cashInflow = money((BigDecimal) flows.get("inflow"));
        BigDecimal cashOutflow = money((BigDecimal) flows.get("outflow"));

        BigDecimal openings = jdbc.queryForObject("""
                SELECT COALESCE(sum(opening_balance), 0) FROM financial_accounts
                WHERE business_id = ? AND upper(currency) = 'PKR' AND opening_date <= ?
                """, BigDecimal.class, businessId, Date.valueOf(to));
        BigDecimal netMovements = jdbc.queryForObject("""
                SELECT COALESCE(sum(CASE m.movement_direction WHEN 'INCREASE' THEN m.amount ELSE -m.amount END), 0)
                FROM account_movements m
                JOIN financial_accounts a ON a.id = m.account_id AND a.business_id = m.business_id
                JOIN financial_transactions t ON t.id = m.transaction_id AND t.business_id = m.business_id
                WHERE m.business_id = ? AND upper(a.currency) = 'PKR' AND a.opening_date <= ? AND m.effective_date <= ?
                  AND """ + NOT_VOID_PAIR, BigDecimal.class, businessId, Date.valueOf(to), Date.valueOf(to));
        BigDecimal cashBalanceEom = money(nz(openings).add(nz(netMovements)));

        Integer foreignAccounts = jdbc.queryForObject("""
                SELECT count(*) FROM financial_accounts
                WHERE business_id = ? AND upper(currency) <> 'PKR' AND opening_date <= ?
                """, Integer.class, businessId, Date.valueOf(to));
        if (foreignAccounts != null && foreignAccounts > 0) {
            notes.add("foreign_currency_accounts_excluded_from_cash");
        }

        // ---------------------------------------------------------------- AR / AP snapshots (obligation authority)
        BigDecimal receivables = BigDecimal.ZERO;
        BigDecimal payables = BigDecimal.ZERO;
        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT o.direction, COALESCE(sum(GREATEST(o.original_amount - COALESCE(al.allocated, 0), 0)), 0) AS outstanding
                FROM financial_obligations o
                LEFT JOIN (SELECT obligation_id, sum(amount) AS allocated FROM obligation_payment_allocations
                           WHERE business_id = ? AND status = 'EFFECTIVE' AND effective_date <= ?
                           GROUP BY obligation_id) al ON al.obligation_id = o.id
                WHERE o.business_id = ? AND o.status <> 'VOIDED' AND o.issue_date <= ? AND upper(o.currency) = 'PKR'
                GROUP BY o.direction
                """, businessId, Date.valueOf(to), businessId, Date.valueOf(to))) {
            BigDecimal outstanding = nz((BigDecimal) row.get("outstanding"));
            if ("RECEIVABLE".equals(row.get("direction"))) {
                receivables = receivables.add(outstanding);
            } else if ("PAYABLE".equals(row.get("direction"))) {
                payables = payables.add(outstanding);
            }
        }
        Integer foreignObligations = jdbc.queryForObject("""
                SELECT count(*) FROM financial_obligations
                WHERE business_id = ? AND status <> 'VOIDED' AND issue_date <= ? AND upper(currency) <> 'PKR'
                """, Integer.class, businessId, Date.valueOf(to));
        if (foreignObligations != null && foreignObligations > 0) {
            notes.add("foreign_currency_obligations_excluded_from_ar_ap");
        }

        // ---------------------------------------------------------------- inventory snapshot (F08 authority)
        BigDecimal inventoryValue = inventoryTracked ? money(inventory.valueAsOf(businessId, to)) : null;

        // ---------------------------------------------------------------- supplemental financing (manual)
        BigDecimal loanOutstanding = null;
        BigDecimal interestExpense = null;
        List<Map<String, Object>> financing = jdbc.queryForList("""
                SELECT loan_outstanding, interest_expense FROM monthly_financing_inputs WHERE business_id = ? AND month = ?
                """, businessId, month.toString());
        if (!financing.isEmpty()) {
            loanOutstanding = (BigDecimal) financing.get(0).get("loan_outstanding");
            interestExpense = (BigDecimal) financing.get(0).get("interest_expense");
        }
        List<String> types = jdbc.queryForList("""
                SELECT financing_type FROM monthly_financing_inputs WHERE business_id = ? AND month <= ?
                ORDER BY month DESC LIMIT 1
                """, String.class, businessId, month.toString());
        String financingType = types.isEmpty() ? "none" : types.get(0);

        boolean periodComplete = to.isBefore(today);
        if (!periodComplete) {
            notes.add("period_in_progress");
        }

        String fingerprint = Hashing.sha256(VERSION, month.toString(), s(cashInflow), s(cashOutflow), s(revenue), s(cogs),
                s(operatingExpenses), s(cashBalanceEom), s(receivables), s(payables), s(inventoryValue),
                s(loanOutstanding), s(interestExpense), financingType, String.valueOf(periodComplete), String.join(",", notes));

        List<FieldSource> sources = List.of(
                new FieldSource("revenue", revenue, "ACCRUAL",
                        "Cash sales + recognized customer invoices (issue date) - effective credit notes (note date)"),
                new FieldSource("cogs", cogs, "ACCRUAL",
                        inventoryTracked ? "F08 cost of sales: sale, project and sample consumption net of returns"
                                : "Unknown: no inventory ledger movements recorded"),
                new FieldSource("operatingExpenses", operatingExpenses, "ACCRUAL",
                        "Operating payments + recognized bills - capitalized stock - debit notes + recognized commission + stock losses"),
                new FieldSource("cashInflow", cashInflow, "CASH",
                        "Posted increases on PKR accounts in the month (transfers and reversed pairs excluded)"),
                new FieldSource("cashOutflow", cashOutflow, "CASH",
                        "Posted decreases on PKR accounts in the month (transfers and reversed pairs excluded)"),
                new FieldSource("cashBalanceEom", cashBalanceEom, "SNAPSHOT",
                        "PKR account opening balances + posted movements up to month end"),
                new FieldSource("receivablesOutstanding", money(receivables), "SNAPSHOT",
                        "Open PKR receivables issued by month end net of allocations dated by month end"),
                new FieldSource("payablesOutstanding", money(payables), "SNAPSHOT",
                        "Open PKR payables issued by month end net of allocations dated by month end"),
                new FieldSource("inventoryValue", inventoryValue, "SNAPSHOT",
                        inventoryTracked ? "F08 weighted-average carrying value at month end"
                                : "Unknown: no inventory ledger movements recorded"),
                new FieldSource("loanOutstanding", loanOutstanding, "SUPPLEMENTAL_MANUAL",
                        "Supplemental financing input (no ledger authority for loans yet)"),
                new FieldSource("interestExpense", interestExpense, "SUPPLEMENTAL_MANUAL",
                        "Supplemental financing input (no ledger authority for interest yet)"));

        return new DerivedMonth(month.toString(), cashInflow, cashOutflow, revenue, cogs, operatingExpenses, cashBalanceEom,
                money(receivables), money(payables), inventoryValue, loanOutstanding, interestExpense, financingType,
                periodComplete, List.copyOf(notes), fingerprint, pnl, sources);
    }

    private boolean inventoryTrackedBy(UUID businessId, LocalDate asOf) {
        Boolean tracked = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM inventory_movements WHERE business_id = ? AND movement_date <= ?)
                """, Boolean.class, businessId, Date.valueOf(asOf));
        return Boolean.TRUE.equals(tracked);
    }

    static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String s(BigDecimal value) {
        return value == null ? "null" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Ordered field map used by tests and the API. */
    public static Map<String, BigDecimal> fields(DerivedMonth m) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        out.put("cashInflow", m.cashInflow());
        out.put("cashOutflow", m.cashOutflow());
        out.put("revenue", m.revenue());
        out.put("cogs", m.cogs());
        out.put("operatingExpenses", m.operatingExpenses());
        out.put("cashBalanceEom", m.cashBalanceEom());
        out.put("receivablesOutstanding", m.receivablesOutstanding());
        out.put("payablesOutstanding", m.payablesOutstanding());
        out.put("inventoryValue", m.inventoryValue());
        out.put("loanOutstanding", m.loanOutstanding());
        out.put("interestExpense", m.interestExpense());
        return out;
    }
}
