package com.app.sme_health_backend.reports.operations;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.obligation.PaymentPlanService;
import com.app.sme_health_backend.obligation.dto.PaymentCalendarItemResponse;
import com.app.sme_health_backend.obligation.dto.PaymentCalendarResponse;
import com.app.sme_health_backend.productsupport.DomainException;
import com.app.sme_health_backend.productsupport.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * F12B business operations: a unified financial calendar (reusing Developer A's payment calendar for every
 * obligation and payment-plan date) and the entity relationship graph across documents, ledger transactions,
 * obligations, projects, inventory, samples, estimates and OCR evidence. Read-only; every source keeps authority.
 */
@Service
public class BusinessOperationsService {
    private static final int MAX_EVENTS = 500;

    private final JdbcTemplate jdbc;
    private final PaymentPlanService paymentCalendar;

    public BusinessOperationsService(JdbcTemplate jdbc, PaymentPlanService paymentCalendar) {
        this.jdbc = jdbc;
        this.paymentCalendar = paymentCalendar;
    }

    // ================================================================ calendar

    @Transactional(readOnly = true)
    public Map<String, Object> calendar(BusinessAccessContext ctx, LocalDate from, LocalDate to) {
        List<Map<String, Object>> events = new ArrayList<>();
        UUID b = ctx.businessId();

        // 1. Obligations and payment-plan installments: Developer A's authoritative calendar.
        PaymentCalendarResponse payments = paymentCalendar.getPaymentCalendar(ctx, null, from.toString(), to.toString());
        for (PaymentCalendarItemResponse i : payments.items()) {
            events.add(event(i.date(), "RECEIVABLE".equals(i.direction()) ? "RECEIVABLE_DUE" : "PAYABLE_DUE",
                    i.referenceNumber() != null ? i.referenceNumber() : i.counterpartyName(), i.outstandingAmount(),
                    "OBLIGATION", i.obligationId(), i.isOverdue() ? "OVERDUE" : "SCHEDULED", "C04:" + i.itemType()));
        }
        // 2. Recurring transactions (scheduled or ready occurrences).
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT o.id, o.scheduled_date, o.amount, o.event_type, o.status, COALESCE(o.description, r.name) AS label
                FROM recurring_rule_occurrences o JOIN recurring_rules r ON r.business_id = o.business_id AND r.id = o.recurring_rule_id
                WHERE o.business_id = ? AND o.scheduled_date BETWEEN ? AND ? AND o.status IN ('SCHEDULED', 'DRAFT_READY')
                """, b, Date.valueOf(from), Date.valueOf(to))) {
            events.add(event(date(r.get("scheduled_date")), "RECURRING_" + r.get("event_type"), (String) r.get("label"),
                    Money.str((BigDecimal) r.get("amount")), "RECURRING_OCCURRENCE", (UUID) r.get("id"), (String) r.get("status"), "F12A:recurring"));
        }
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT id, name, next_due_date, amount, event_type FROM recurring_rules
                WHERE business_id = ? AND status = 'ACTIVE' AND next_due_date BETWEEN ? AND ?
                  AND NOT EXISTS (SELECT 1 FROM recurring_rule_occurrences o WHERE o.business_id = recurring_rules.business_id
                                  AND o.recurring_rule_id = recurring_rules.id AND o.scheduled_date = recurring_rules.next_due_date)
                """, b, Date.valueOf(from), Date.valueOf(to))) {
            events.add(event(date(r.get("next_due_date")), "RECURRING_" + r.get("event_type"), (String) r.get("name"),
                    Money.str((BigDecimal) r.get("amount")), "RECURRING_RULE", (UUID) r.get("id"), "SCHEDULED", "F12A:recurring"));
        }
        // 3. Finalized but not yet recognized invoices/bills (commercial due dates; recognized ones appear via C04).
        for (Map<String, Object> d : jdbc.queryForList("""
                SELECT d.id, d.kind, d.document_number, d.due_date, d.total FROM commercial_documents d
                WHERE d.business_id = ? AND d.status = 'FINALIZED' AND d.due_date BETWEEN ? AND ?
                  AND NOT EXISTS (SELECT 1 FROM financial_obligations o WHERE o.business_id = d.business_id
                                  AND o.source_id = d.id::text AND o.status = 'OPEN')
                """, b, Date.valueOf(from), Date.valueOf(to))) {
            events.add(event(date(d.get("due_date")), "INVOICE".equals(d.get("kind")) ? "INVOICE_DUE_UNRECOGNIZED" : "BILL_DUE_UNRECOGNIZED",
                    (String) d.get("document_number"), Money.str((BigDecimal) d.get("total")), "COMMERCIAL_DOCUMENT", (UUID) d.get("id"),
                    "NOT_RECOGNIZED", "F04"));
        }
        // 4. Project, sample, budget and goal dates.
        for (Map<String, Object> p : jdbc.queryForList("SELECT id, code, name, status, start_date, target_end_date FROM projects "
                + "WHERE business_id = ? AND status NOT IN ('CANCELLED', 'ARCHIVED') AND (start_date BETWEEN ? AND ? OR target_end_date BETWEEN ? AND ?)",
                b, Date.valueOf(from), Date.valueOf(to), Date.valueOf(from), Date.valueOf(to))) {
            String label = p.get("code") + " " + p.get("name");
            LocalDate start = date(p.get("start_date"));
            LocalDate end = date(p.get("target_end_date"));
            if (start != null && !start.isBefore(from) && !start.isAfter(to))
                events.add(event(start, "PROJECT_START", label, null, "PROJECT", (UUID) p.get("id"), (String) p.get("status"), "F05"));
            if (end != null && !end.isBefore(from) && !end.isAfter(to))
                events.add(event(end, "PROJECT_TARGET_END", label, null, "PROJECT", (UUID) p.get("id"), (String) p.get("status"), "F05"));
        }
        for (Map<String, Object> s : jdbc.queryForList("SELECT id, code, name, status, target_end_date FROM sample_activities "
                + "WHERE business_id = ? AND status IN ('DRAFT', 'ACTIVE') AND target_end_date BETWEEN ? AND ?", b, Date.valueOf(from), Date.valueOf(to))) {
            events.add(event(date(s.get("target_end_date")), "SAMPLE_TARGET_END", s.get("code") + " " + s.get("name"), null,
                    "SAMPLE", (UUID) s.get("id"), (String) s.get("status"), "F09"));
        }
        for (Map<String, Object> g : jdbc.queryForList("SELECT id, name, metric, target_value, period_end FROM financial_goals "
                + "WHERE business_id = ? AND status = 'ACTIVE' AND period_end BETWEEN ? AND ?", b, Date.valueOf(from), Date.valueOf(to))) {
            events.add(event(date(g.get("period_end")), "GOAL_DEADLINE", (String) g.get("name"), Money.str((BigDecimal) g.get("target_value")),
                    "GOAL", (UUID) g.get("id"), (String) g.get("metric"), "S-B01"));
        }
        for (Map<String, Object> g : jdbc.queryForList("SELECT id, name, period_end, status FROM business_budgets "
                + "WHERE business_id = ? AND status IN ('DRAFT', 'ACTIVE') AND period_end BETWEEN ? AND ?", b, Date.valueOf(from), Date.valueOf(to))) {
            events.add(event(date(g.get("period_end")), "BUDGET_PERIOD_END", (String) g.get("name"), null, "BUDGET",
                    (UUID) g.get("id"), (String) g.get("status"), "S-B01"));
        }
        events.sort(Comparator.comparing((Map<String, Object> e) -> (String) e.get("date")).thenComparing(e -> (String) e.get("type")));
        boolean truncated = events.size() > MAX_EVENTS;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from.toString());
        body.put("to", to.toString());
        body.put("events", truncated ? events.subList(0, MAX_EVENTS) : events);
        body.put("truncated", truncated);
        body.put("paymentSummary", payments.summary());
        return body;
    }

    // ================================================================ relationships

    @Transactional(readOnly = true)
    public Map<String, Object> relationships(BusinessAccessContext ctx, String kind, UUID id) {
        UUID b = ctx.businessId();
        List<Map<String, Object>> related = new ArrayList<>();
        String title;
        switch (kind) {
            case "DOCUMENT" -> {
                Map<String, Object> d = one("SELECT kind, document_number, status, total FROM commercial_documents WHERE business_id = ? AND id = ?", b, id);
                title = d.get("kind") + " " + (d.get("document_number") == null ? "DRAFT" : d.get("document_number"));
                for (Map<String, Object> o : jdbc.queryForList("SELECT id, status, original_amount FROM financial_obligations WHERE business_id = ? "
                        + "AND source_id = ? AND source_type IN ('CUSTOMER_INVOICE', 'SUPPLIER_BILL')", b, id.toString())) {
                    related.add(rel("OBLIGATION", (UUID) o.get("id"), "Recognized " + o.get("status"), amt(o.get("original_amount")), "RECOGNIZED_AS"));
                    for (Map<String, Object> a : jdbc.queryForList("SELECT id, allocation_type, amount, financial_transaction_id, status "
                            + "FROM obligation_payment_allocations WHERE business_id = ? AND obligation_id = ?", b, o.get("id"))) {
                        if (a.get("financial_transaction_id") != null)
                            related.add(rel("TRANSACTION", (UUID) a.get("financial_transaction_id"), "Settlement " + a.get("status"),
                                    amt(a.get("amount")), "SETTLED_BY"));
                    }
                }
                for (Map<String, Object> a : jdbc.queryForList("SELECT id, adjustment_number, amount, status FROM commercial_adjustments "
                        + "WHERE business_id = ? AND document_id = ?", b, id))
                    related.add(rel("ADJUSTMENT", (UUID) a.get("id"), a.get("adjustment_number") + " " + a.get("status"), amt(a.get("amount")), "CORRECTED_BY"));
                movements("source_id", b, id, related);
                for (Map<String, Object> p : jdbc.queryForList("SELECT l.project_id, p.code, l.allocated_amount FROM project_commercial_document_links l "
                        + "JOIN projects p ON p.business_id = l.business_id AND p.id = l.project_id WHERE l.business_id = ? AND l.document_id = ? "
                        + "AND l.link_status = 'ACTIVE'", b, id))
                    related.add(rel("PROJECT", (UUID) p.get("project_id"), (String) p.get("code"), amt(p.get("allocated_amount")), "LINKED_TO_PROJECT"));
                for (Map<String, Object> s : jdbc.queryForList("SELECT sample_id, amount FROM sample_recovery_links WHERE business_id = ? "
                        + "AND document_id = ? AND status = 'ACTIVE'", b, id))
                    related.add(rel("SAMPLE", (UUID) s.get("sample_id"), "Sample recovery", amt(s.get("amount")), "RECOVERS_SAMPLE"));
                for (Map<String, Object> e : jdbc.queryForList("SELECT estimate_id FROM estimate_commercial_links WHERE business_id = ? AND document_id = ?", b, id))
                    related.add(rel("ESTIMATE", (UUID) e.get("estimate_id"), "Quotation source", null, "QUOTED_FROM"));
            }
            case "TRANSACTION" -> {
                Map<String, Object> t = one("SELECT event_type, base_amount_pkr, effective_date, description FROM financial_transactions "
                        + "WHERE business_id = ? AND id = ?", b, id);
                title = t.get("event_type") + " " + t.get("effective_date");
                for (Map<String, Object> a : jdbc.queryForList("SELECT obligation_id, amount, status FROM obligation_payment_allocations "
                        + "WHERE business_id = ? AND financial_transaction_id = ?", b, id))
                    related.add(rel("OBLIGATION", (UUID) a.get("obligation_id"), "Settles " + a.get("status"), amt(a.get("amount")), "SETTLES"));
                for (Map<String, Object> a : jdbc.queryForList("SELECT project_id, base_amount, cost_category FROM project_financial_attributions "
                        + "WHERE business_id = ? AND source_type = 'TRANSACTION' AND source_id = ? AND status = 'ACTIVE'", b, id))
                    related.add(rel("PROJECT", (UUID) a.get("project_id"), "Attributed " + a.get("cost_category"), amt(a.get("base_amount")), "ATTRIBUTED_TO"));
                for (Map<String, Object> s : jdbc.queryForList("SELECT sample_id, amount FROM sample_actual_costs WHERE business_id = ? "
                        + "AND transaction_id = ? AND status = 'EFFECTIVE'", b, id))
                    related.add(rel("SAMPLE", (UUID) s.get("sample_id"), "Sample cost", amt(s.get("amount")), "ATTRIBUTED_TO"));
                movements("source_id", b, id, related);
                for (Map<String, Object> c : jdbc.queryForList("SELECT category, cost_behavior FROM financial_classifications WHERE business_id = ? "
                        + "AND source_kind = 'TRANSACTION' AND source_id = ?", b, id))
                    related.add(rel("CLASSIFICATION", id, c.get("category") + (c.get("cost_behavior") == null ? "" : " / " + c.get("cost_behavior")), null, "CLASSIFIED_AS"));
            }
            case "PROJECT" -> {
                Map<String, Object> p = one("SELECT code, name FROM projects WHERE business_id = ? AND id = ?", b, id);
                title = p.get("code") + " " + p.get("name");
                for (Map<String, Object> l : jdbc.queryForList("SELECT document_id, document_kind, document_number, allocated_amount "
                        + "FROM project_commercial_document_links WHERE business_id = ? AND project_id = ? AND link_status = 'ACTIVE'", b, id))
                    related.add(rel("DOCUMENT", (UUID) l.get("document_id"), l.get("document_kind") + " " + l.get("document_number"),
                            amt(l.get("allocated_amount")), "LINKED_DOCUMENT"));
                for (Map<String, Object> a : jdbc.queryForList("SELECT source_type, source_id, base_amount, cost_category FROM project_financial_attributions "
                        + "WHERE business_id = ? AND project_id = ? AND status = 'ACTIVE'", b, id))
                    related.add(rel("TRANSACTION".equals(a.get("source_type")) ? "TRANSACTION" : (String) a.get("source_type"),
                            (UUID) a.get("source_id"), "Attribution " + a.get("cost_category"), amt(a.get("base_amount")), "ATTRIBUTION"));
                movements("project_id", b, id, related);
                for (Map<String, Object> s : jdbc.queryForList("SELECT id, code FROM sample_activities WHERE business_id = ? AND project_id = ?", b, id))
                    related.add(rel("SAMPLE", (UUID) s.get("id"), (String) s.get("code"), null, "PROJECT_SAMPLE"));
                for (Map<String, Object> e : jdbc.queryForList("SELECT estimate_id FROM estimate_project_conversions WHERE business_id = ? AND project_id = ?", b, id))
                    related.add(rel("ESTIMATE", (UUID) e.get("estimate_id"), "Converted estimate", null, "CONVERTED_FROM"));
                for (Map<String, Object> o : jdbc.queryForList("SELECT id, evidence_status AS status FROM project_ocr_evidence_links WHERE business_id = ? AND project_id = ?", b, id))
                    related.add(rel("OCR_EVIDENCE", (UUID) o.get("id"), "OCR evidence " + o.get("status"), null, "EVIDENCE"));
            }
            case "ITEM" -> {
                Map<String, Object> i = one("SELECT sku, name FROM inventory_items WHERE business_id = ? AND id = ?", b, id);
                title = i.get("sku") + " " + i.get("name");
                for (Map<String, Object> l : jdbc.queryForList("SELECT DISTINCT l.document_id, d.kind, d.document_number FROM commercial_document_lines l "
                        + "JOIN commercial_documents d ON d.business_id = l.business_id AND d.id = l.document_id "
                        + "WHERE l.business_id = ? AND l.inventory_item_id = ?", b, id))
                    related.add(rel("DOCUMENT", (UUID) l.get("document_id"), l.get("kind") + " " + l.get("document_number"), null, "DOCUMENT_LINE"));
                movements("item_id", b, id, related);
            }
            case "SAMPLE" -> {
                Map<String, Object> s = one("SELECT code, name, project_id FROM sample_activities WHERE business_id = ? AND id = ?", b, id);
                title = s.get("code") + " " + s.get("name");
                if (s.get("project_id") != null) related.add(rel("PROJECT", (UUID) s.get("project_id"), "Parent project", null, "FOR_PROJECT"));
                for (Map<String, Object> c : jdbc.queryForList("SELECT transaction_id, amount, status FROM sample_actual_costs WHERE business_id = ? AND sample_id = ?", b, id))
                    related.add(rel("TRANSACTION", (UUID) c.get("transaction_id"), "Cost " + c.get("status"), amt(c.get("amount")), "COST_SOURCE"));
                for (Map<String, Object> l : jdbc.queryForList("SELECT document_id, amount, status FROM sample_recovery_links WHERE business_id = ? AND sample_id = ?", b, id))
                    related.add(rel("DOCUMENT", (UUID) l.get("document_id"), "Recovery " + l.get("status"), amt(l.get("amount")), "RECOVERED_BY"));
                movements("sample_id", b, id, related);
            }
            default -> throw DomainException.invalid("kind", "enum_invalid");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", kind);
        body.put("id", id);
        body.put("title", title);
        body.put("related", related);
        return body;
    }

    private void movements(String column, UUID businessId, UUID id, List<Map<String, Object>> related) {
        for (Map<String, Object> m : jdbc.queryForList("SELECT id, movement_type, purpose, value_delta, movement_date FROM inventory_movements "
                + "WHERE business_id = ? AND " + column + " = ? ORDER BY sequence_no LIMIT 100", businessId, id)) {
            related.add(rel("INVENTORY_MOVEMENT", (UUID) m.get("id"), m.get("movement_type") + (m.get("purpose") == null ? "" : " " + m.get("purpose"))
                    + " " + m.get("movement_date"), amt(m.get("value_delta")), "STOCK_MOVEMENT"));
        }
    }

    private Map<String, Object> one(String sql, UUID businessId, UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, businessId, id);
        if (rows.isEmpty()) throw DomainException.notFound("Entity");
        return rows.get(0);
    }

    private static Map<String, Object> rel(String kind, UUID id, String label, String amount, String relation) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        m.put("id", id);
        m.put("label", label);
        m.put("amount", amount);
        m.put("relation", relation);
        return m;
    }

    private static Map<String, Object> event(LocalDate date, String type, String label, String amount, String sourceKind,
                                             UUID sourceId, String status, String authority) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date == null ? null : date.toString());
        m.put("type", type);
        m.put("label", label);
        m.put("amount", amount);
        m.put("sourceKind", sourceKind);
        m.put("sourceId", sourceId);
        m.put("status", status);
        m.put("authority", authority);
        return m;
    }

    private static String amt(Object value) {
        return value == null ? null : Money.str((BigDecimal) value);
    }

    private static LocalDate date(Object value) {
        if (value instanceof Date d) return d.toLocalDate();
        if (value instanceof LocalDate d) return d;
        return null;
    }
}
