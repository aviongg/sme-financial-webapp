package com.app.sme_health_backend.ai.copilot;

import com.app.sme_health_backend.ai.copilot.CopilotTypes.Evidence;
import com.app.sme_health_backend.ai.copilot.CopilotTypes.Param;
import com.app.sme_health_backend.ai.copilot.CopilotTypes.ToolCall;
import com.app.sme_health_backend.ai.copilot.CopilotTypes.ToolResult;
import com.app.sme_health_backend.ai.copilot.CopilotTypes.ToolSpec;
import com.app.sme_health_backend.financialquery.FinancialQueryFacade;
import com.app.sme_health_backend.financialquery.dto.CashPositionQuery;
import com.app.sme_health_backend.financialquery.dto.FinancialQueryEnvelope;
import com.app.sme_health_backend.financialquery.dto.OverdueReceivablesQuery;
import com.app.sme_health_backend.financialquery.dto.UpcomingObligationsQuery;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.intelligence.FinancialIntelligenceService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Provider-neutral, READ-ONLY tool registry. Every tool: is resolved by exact name; runs only for the
 * server-resolved active business (no tool accepts a business, user or tenant argument); re-checks its own role
 * permission; validates arguments against a closed schema; returns bounded evidence through the
 * FinancialQueryFacade (or the intelligence action center). No tool writes, posts or deletes anything.
 */
@Component
public class CopilotToolRegistry {
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final String MONTH = "^[0-9]{4}-(0[1-9]|1[0-2])$";

    private record Tool(ToolSpec spec, BusinessPermission permission,
                        BiFunction<BusinessAccessContext, Map<String, Object>, Object> executor) { }

    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final BusinessAuthorizationService authorization;
    private final JdbcTemplate jdbc;

    public CopilotToolRegistry(FinancialQueryFacade facade, FinancialIntelligenceService intelligence,
                               BusinessAuthorizationService authorization, JdbcTemplate jdbc) {
        this.authorization = authorization;
        this.jdbc = jdbc;
        Param month = new Param("month", "string", "Calendar month YYYY-MM; omit for the current month", null, MONTH, 7, null, null, false);
        register("cash_position", "Current cash and bank balances across the business's accounts", List.of(),
                (ctx, a) -> facade.getCashPosition(ctx, new CashPositionQuery(null, null)));
        register("overdue_receivables", "Customer receivables past their due date with aging", List.of(),
                (ctx, a) -> facade.getOverdueReceivables(ctx, new OverdueReceivablesQuery(null, 20)));
        register("upcoming_obligations", "Receivables and payables due in the next N days",
                List.of(new Param("days", "integer", "Window in days (1-90)", null, null, null, 1, 90, false)),
                (ctx, a) -> {
                    LocalDate today = LocalDate.now(ZoneId.of("Asia/Karachi"));
                    int days = a.get("days") == null ? 30 : ((Number) a.get("days")).intValue();
                    return facade.getUpcomingObligations(ctx, new UpcomingObligationsQuery(null, today.toString(), today.plusDays(days).toString()));
                });
        register("profit_and_loss", "Accrual profit and loss: revenue, cost of sales, expenses and profit for a month",
                List.of(month), (ctx, a) -> facade.getProfitAndLoss(ctx, (String) a.get("month")));
        register("margin_drivers", "Gross margin drivers: revenue, inventory cost of goods sold and capitalized purchases",
                List.of(month), (ctx, a) -> facade.getMarginDrivers(ctx, (String) a.getOrDefault("month", "")));
        register("inventory_position", "Stock on hand, valuation and items below reorder level", List.of(),
                (ctx, a) -> facade.getInventoryPosition(ctx));
        register("budget_goal_status", "Active budget variance and financial goal progress", List.of(),
                (ctx, a) -> facade.getBudgetAndGoalStatus(ctx));
        register("break_even", "Break-even revenue, contribution margin and what classification is missing",
                List.of(month), (ctx, a) -> facade.getBreakEven(ctx, (String) a.get("month")));
        register("project_profitability", "Actual revenue, cost and profit of one project/job/order by its code",
                List.of(new Param("projectCode", "string", "Project code, e.g. PRJ-001", null, "^[A-Za-z0-9][A-Za-z0-9._/-]{0,39}$", 40, null, null, true)),
                (ctx, a) -> {
                    List<UUID> ids = jdbc.queryForList("SELECT id FROM projects WHERE business_id = ? AND upper(code) = upper(?)",
                            UUID.class, ctx.businessId(), a.get("projectCode"));
                    if (ids.isEmpty()) throw new ToolFailure("not_found", "No project with that code exists in the active business.");
                    return facade.getProjectProfitability(ctx, ids.get(0));
                });
        register("fx_impact", "Realized foreign-exchange gains/losses on settled foreign obligations", List.of(),
                (ctx, a) -> facade.getFxSettlementImpact(ctx, ""));
        register("health_evidence", "Financial health score evidence from posted ledger data for a month", List.of(month),
                (ctx, a) -> facade.getAvailableFinancialHealthEvidence(ctx, (String) a.get("month")));
        register("action_center", "Prioritized actions with reasons: overdue items, risks, budgets, goals, inventory", List.of(),
                (ctx, a) -> {
                    Map<String, Object> actions = intelligence.actions(ctx);
                    @SuppressWarnings("unchecked") List<Map<String, Object>> list = (List<Map<String, Object>>) actions.get("actions");
                    return Map.of("actions", list.size() > 8 ? list.subList(0, 8) : list, "count", actions.get("count"));
                });
    }

    private void register(String name, String description, List<Param> params,
                          BiFunction<BusinessAccessContext, Map<String, Object>, Object> executor) {
        tools.put(name, new Tool(new ToolSpec(name, description, List.copyOf(params)), BusinessPermission.FINANCIAL_DATA_READ, executor));
    }

    /** Tools visible to a role (same permission check used at execution). */
    public List<ToolSpec> specs(BusinessAccessContext ctx) {
        List<ToolSpec> out = new ArrayList<>();
        for (Tool t : tools.values()) {
            if (authorization.hasRolePermission(ctx.role(), t.permission())) out.add(t.spec());
        }
        return out;
    }

    public Set<String> names() {
        return tools.keySet();
    }

    /** Validates and executes one call. Never throws for model-caused problems; returns a typed failure instead. */
    public ToolResult execute(BusinessAccessContext ctx, ToolCall call) {
        Tool tool = call == null || call.name() == null ? null : tools.get(call.name());
        String callId = call == null ? null : call.callId();
        if (tool == null) {
            return new ToolResult(callId, call == null ? null : call.name(), "REJECTED", "unknown_tool", List.of());
        }
        if (!authorization.hasRolePermission(ctx.role(), tool.permission())) {
            return new ToolResult(callId, call.name(), "REJECTED", "permission_denied", List.of());
        }
        Map<String, Object> args;
        try {
            args = validate(tool.spec(), call.arguments());
        } catch (ToolFailure f) {
            return new ToolResult(callId, call.name(), "REJECTED", f.code, List.of());
        }
        try {
            Object result = tool.executor().apply(ctx, args);
            List<Evidence> evidence = evidence(call.name(), result);
            String status = "OK";
            String message = null;
            if (result instanceof FinancialQueryEnvelope<?> envelope && envelope.unavailableReason() != null) {
                status = "UNAVAILABLE";
                message = "unavailable";
            }
            return new ToolResult(callId, call.name(), status, message, evidence);
        } catch (ToolFailure f) {
            return new ToolResult(callId, call.name(), "FAILED", f.code, List.of());
        } catch (RuntimeException e) {
            return new ToolResult(callId, call.name(), "FAILED", "tool_error", List.of());
        }
    }

    static Map<String, Object> validate(ToolSpec spec, Map<String, Object> raw) {
        Map<String, Object> args = raw == null ? Map.of() : raw;
        if (args.size() > spec.params().size()) throw new ToolFailure("invalid_arguments", "Too many arguments");
        Map<String, Object> clean = new LinkedHashMap<>();
        for (String key : args.keySet()) {
            if (spec.params().stream().noneMatch(p -> p.name().equals(key))) throw new ToolFailure("invalid_arguments", "Unknown argument " + key);
        }
        for (Param p : spec.params()) {
            Object v = args.get(p.name());
            if (v == null) {
                if (p.required()) throw new ToolFailure("invalid_arguments", "Missing " + p.name());
                continue;
            }
            if ("integer".equals(p.type())) {
                long n;
                if (v instanceof Number num && (num instanceof Integer || num instanceof Long
                        || (num instanceof BigDecimal bd && bd.stripTrailingZeros().scale() <= 0))) {
                    n = num.longValue();
                } else {
                    throw new ToolFailure("invalid_arguments", p.name() + " must be an integer");
                }
                if ((p.min() != null && n < p.min()) || (p.max() != null && n > p.max())) {
                    throw new ToolFailure("invalid_arguments", p.name() + " out of range");
                }
                clean.put(p.name(), (int) n);
            } else {
                if (!(v instanceof String s)) throw new ToolFailure("invalid_arguments", p.name() + " must be a string");
                if (p.maxLength() != null && s.length() > p.maxLength()) throw new ToolFailure("invalid_arguments", p.name() + " too long");
                if (p.pattern() != null && !s.matches(p.pattern())) throw new ToolFailure("invalid_arguments", p.name() + " invalid");
                if (p.enumValues() != null && !p.enumValues().contains(s)) throw new ToolFailure("invalid_arguments", p.name() + " invalid");
                clean.put(p.name(), s);
            }
        }
        return clean;
    }

    /** Flattens a tool payload into bounded evidence entries with stable ids (tool.path). */
    static List<Evidence> evidence(String tool, Object result) {
        Object data = result instanceof FinancialQueryEnvelope<?> e ? e.data() : result;
        List<Evidence> out = new ArrayList<>();
        if (result instanceof FinancialQueryEnvelope<?> e) {
            out.add(new Evidence(tool + ".completeness", tool, "completeness", e.completeness() == null ? "UNKNOWN" : e.completeness().name()));
            if (e.unavailableReason() != null) out.add(new Evidence(tool + ".unavailable", tool, "unavailable", truncate(e.unavailableReason())));
        }
        if (data == null) return out;
        Object tree = JSON.convertValue(data, Object.class);
        flatten(tool, tool, "", tree, out, 0);
        return out.size() > CopilotTypes.MAX_EVIDENCE_PER_TOOL ? out.subList(0, CopilotTypes.MAX_EVIDENCE_PER_TOOL) : out;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String tool, String prefix, String label, Object node, List<Evidence> out, int depth) {
        if (out.size() >= CopilotTypes.MAX_EVIDENCE_PER_TOOL * 2 || depth > 3) return;
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : ((Map<Object, Object>) map).entrySet()) {
                String key = String.valueOf(e.getKey());
                if (key.endsWith("Id") || key.equals("id") || key.equals("businessId") || key.equals("evidence")) continue;
                flatten(tool, prefix + "." + key, label.isEmpty() ? key : label + " " + key, e.getValue(), out, depth + 1);
            }
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < Math.min(list.size(), 8); i++) {
                flatten(tool, prefix + "[" + i + "]", label + " #" + (i + 1), list.get(i), out, depth + 1);
            }
        } else if (node != null) {
            out.add(new Evidence(prefix, tool, label, truncate(String.valueOf(node))));
        }
    }

    private static String truncate(String value) {
        return value.length() > 160 ? value.substring(0, 160) : value;
    }

    static final class ToolFailure extends RuntimeException {
        final String code;

        ToolFailure(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
