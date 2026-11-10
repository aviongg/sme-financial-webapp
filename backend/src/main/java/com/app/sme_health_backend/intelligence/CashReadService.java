package com.app.sme_health_backend.intelligence;

import com.app.sme_health_backend.productsupport.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only PKR cash and AR/AP snapshots straight from Developer A's ledger tables (opening balances + posted
 * account movements; obligations net of effective allocations). Used for goals, intelligence and dashboards.
 */
@Service
public class CashReadService {
    private final JdbcTemplate jdbc;

    public CashReadService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public BigDecimal cashBalancePkr(UUID businessId, LocalDate asOf) {
        BigDecimal opening = jdbc.queryForObject("""
                SELECT COALESCE(sum(opening_balance), 0) FROM financial_accounts
                WHERE business_id = ? AND currency = 'PKR' AND opening_date <= ?
                """, BigDecimal.class, businessId, Date.valueOf(asOf));
        BigDecimal movements = jdbc.queryForObject("""
                SELECT COALESCE(sum(CASE m.movement_direction WHEN 'INCREASE' THEN m.amount ELSE -m.amount END), 0)
                FROM account_movements m JOIN financial_accounts a ON a.id = m.account_id AND a.business_id = m.business_id
                WHERE m.business_id = ? AND a.currency = 'PKR' AND m.effective_date <= ?
                """, BigDecimal.class, businessId, Date.valueOf(asOf));
        return Money.nz(opening).add(Money.nz(movements));
    }

    @Transactional(readOnly = true)
    public boolean hasAccounts(UUID businessId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM financial_accounts WHERE business_id = ?", Integer.class, businessId);
        return n != null && n > 0;
    }

    /** Outstanding (original - effective allocations) of OPEN obligations in one direction. */
    @Transactional(readOnly = true)
    public BigDecimal outstanding(UUID businessId, String direction) {
        return Money.nz(jdbc.queryForObject("""
                SELECT COALESCE(sum(GREATEST(o.original_amount - COALESCE(
                    (SELECT sum(a.amount) FROM obligation_payment_allocations a
                     WHERE a.business_id = o.business_id AND a.obligation_id = o.id AND a.status = 'EFFECTIVE'), 0), 0)), 0)
                FROM financial_obligations o WHERE o.business_id = ? AND o.direction = ? AND o.status = 'OPEN'
                """, BigDecimal.class, businessId, direction));
    }

    /** Outstanding of OPEN obligations in one direction that are past due as of a date. */
    @Transactional(readOnly = true)
    public BigDecimal overdue(UUID businessId, String direction, LocalDate asOf) {
        return Money.nz(jdbc.queryForObject("""
                SELECT COALESCE(sum(GREATEST(o.original_amount - COALESCE(
                    (SELECT sum(a.amount) FROM obligation_payment_allocations a
                     WHERE a.business_id = o.business_id AND a.obligation_id = o.id AND a.status = 'EFFECTIVE'), 0), 0)), 0)
                FROM financial_obligations o WHERE o.business_id = ? AND o.direction = ? AND o.status = 'OPEN'
                  AND o.due_date IS NOT NULL AND o.due_date < ?
                """, BigDecimal.class, businessId, direction, Date.valueOf(asOf)));
    }
}
