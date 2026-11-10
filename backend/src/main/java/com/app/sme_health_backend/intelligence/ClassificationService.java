package com.app.sme_health_backend.intelligence;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.productsupport.AuditRecorder;
import com.app.sme_health_backend.productsupport.DomainException;
import com.app.sme_health_backend.productsupport.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Explicit, user-confirmed analysis metadata (category + optional FIXED/VARIABLE behaviour) over authoritative
 * facts. Changing a classification never changes an amount; every change is audited.
 */
@Service
public class ClassificationService {
    private final JdbcTemplate jdbc;
    private final ProfitAndLossService pnl;
    private final AuditRecorder audit;

    public ClassificationService(JdbcTemplate jdbc, ProfitAndLossService pnl, AuditRecorder audit) {
        this.jdbc = jdbc;
        this.pnl = pnl;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> classify(BusinessAccessContext ctx, String sourceKind, UUID sourceId, String categoryRaw,
                                        String behaviorRaw) {
        if (!"TRANSACTION".equals(sourceKind) && !"COMMERCIAL_DOCUMENT".equals(sourceKind)) {
            throw DomainException.invalid("sourceKind", "enum_invalid");
        }
        if (sourceId == null) {
            throw DomainException.invalid("sourceId", "required");
        }
        FinancialCategory category = parse(FinancialCategory.class, categoryRaw, "category", true);
        CostBehavior behavior = parse(CostBehavior.class, behaviorRaw, "costBehavior", false);
        if (category.revenue() && behavior != null) {
            throw DomainException.invalid("costBehavior", "not_allowed_for_revenue");
        }
        // The source must exist in this business (tenant boundary; 404 otherwise).
        String sql = "TRANSACTION".equals(sourceKind)
                ? "SELECT count(*) FROM financial_transactions WHERE business_id = ? AND id = ? AND event_type IN "
                + "('DIRECT_SALE_RECEIPT', 'OPERATING_EXPENSE_PAYMENT') AND reversed_transaction_id IS NULL"
                : "SELECT count(*) FROM commercial_documents WHERE business_id = ? AND id = ? AND status <> 'DRAFT'";
        Integer found = jdbc.queryForObject(sql, Integer.class, ctx.businessId(), sourceId);
        if (found == null || found == 0) {
            throw DomainException.notFound("Classifiable source");
        }
        jdbc.update("""
                INSERT INTO financial_classifications(business_id, source_kind, source_id, category, cost_behavior, updated_by)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (business_id, source_kind, source_id) DO UPDATE
                SET category = EXCLUDED.category, cost_behavior = EXCLUDED.cost_behavior, updated_by = EXCLUDED.updated_by,
                    updated_at = now(), version = financial_classifications.version + 1
                """, ctx.businessId(), sourceKind, sourceId, category.name(), behavior == null ? null : behavior.name(), ctx.userId());
        audit.record(ctx, AuditEventType.FINANCIAL_CLASSIFICATION_CHANGED, sourceKind, sourceId,
                Map.of("category", category.name(), "costBehavior", behavior == null ? "" : behavior.name()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceKind", sourceKind);
        body.put("sourceId", sourceId);
        body.put("category", category.name());
        body.put("costBehavior", behavior == null ? null : behavior.name());
        return body;
    }

    @Transactional
    public Map<String, Object> setCategoryBehavior(BusinessAccessContext ctx, String categoryRaw, String behaviorRaw) {
        FinancialCategory category = parse(FinancialCategory.class, categoryRaw, "category", true);
        if (category.revenue()) {
            throw DomainException.invalid("category", "revenue_category_has_no_cost_behavior");
        }
        CostBehavior behavior = parse(CostBehavior.class, behaviorRaw, "costBehavior", false);
        if (behavior == null) {
            jdbc.update("DELETE FROM category_cost_behaviors WHERE business_id = ? AND category = ?", ctx.businessId(), category.name());
        } else {
            jdbc.update("""
                    INSERT INTO category_cost_behaviors(business_id, category, cost_behavior, updated_by) VALUES (?, ?, ?, ?)
                    ON CONFLICT (business_id, category) DO UPDATE SET cost_behavior = EXCLUDED.cost_behavior,
                        updated_by = EXCLUDED.updated_by, updated_at = now()
                    """, ctx.businessId(), category.name(), behavior.name(), ctx.userId());
        }
        audit.record(ctx, AuditEventType.FINANCIAL_CLASSIFICATION_CHANGED, "CATEGORY_BEHAVIOR", null,
                Map.of("category", category.name(), "costBehavior", behavior == null ? "" : behavior.name()));
        return categoryBehaviors(ctx);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> categoryBehaviors(BusinessAccessContext ctx) {
        Map<String, String> configured = new LinkedHashMap<>();
        jdbc.query("SELECT category, cost_behavior FROM category_cost_behaviors WHERE business_id = ?",
                rs -> {
                    configured.put(rs.getString(1), rs.getString(2));
                }, ctx.businessId());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FinancialCategory c : FinancialCategory.values()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("category", c.name());
            r.put("revenue", c.revenue());
            r.put("costBehavior", configured.get(c.name()));
            rows.add(r);
        }
        return Map.of("categories", rows);
    }

    /** Facts in a period with their current classification, unclassified first. */
    @Transactional(readOnly = true)
    public Map<String, Object> facts(BusinessAccessContext ctx, LocalDate from, LocalDate to) {
        ProfitAndLossService.Statement s = pnl.statement(ctx.businessId(), from, to);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProfitAndLossService.Fact f : s.revenueFacts()) rows.add(factBody(f, "REVENUE"));
        for (ProfitAndLossService.Fact f : s.expenseFacts()) rows.add(factBody(f, "EXPENSE"));
        rows.sort((a, b) -> Boolean.compare(a.get("category") != null, b.get("category") != null));
        return Map.of("from", from.toString(), "to", to.toString(), "facts", rows,
                "unclassifiedRevenue", Money.str(s.unclassifiedRevenue()),
                "unclassifiedExpenses", Money.str(s.unclassifiedExpenses()));
    }

    private static Map<String, Object> factBody(ProfitAndLossService.Fact f, String side) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("side", side);
        r.put("sourceKind", f.sourceKind());
        r.put("sourceId", f.sourceId());
        r.put("date", f.date() == null ? null : f.date().toString());
        r.put("amount", Money.str(f.amount()));
        r.put("label", f.label());
        r.put("category", f.category());
        r.put("costBehavior", f.behavior() == null ? null : f.behavior().name());
        return r;
    }

    static <E extends Enum<E>> E parse(Class<E> type, String raw, String field, boolean required) {
        if (raw == null || raw.isBlank()) {
            if (required) throw DomainException.invalid(field, "required");
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim());
        } catch (IllegalArgumentException e) {
            throw DomainException.invalid(field, "enum_invalid");
        }
    }
}
