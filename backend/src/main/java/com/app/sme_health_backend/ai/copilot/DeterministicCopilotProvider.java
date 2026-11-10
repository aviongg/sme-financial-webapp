package com.app.sme_health_backend.ai.copilot;

import com.app.sme_health_backend.ai.copilot.CopilotTypes.Evidence;
import com.app.sme_health_backend.ai.copilot.CopilotTypes.Step;
import com.app.sme_health_backend.ai.copilot.CopilotTypes.ToolCall;
import com.app.sme_health_backend.ai.copilot.CopilotTypes.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline, deterministic Copilot provider (EN/UR). It routes by keywords to read-only tools and then renders an
 * answer ONLY from returned evidence values, citing every evidence id it uses. It is the disabled-provider fallback
 * and the default for development/demo; it never invents figures.
 */
@Component
public class DeterministicCopilotProvider implements CopilotProvider {
    private static final Pattern PROJECT_CODE = Pattern.compile("\\b([A-Z]{2,6}-[A-Z0-9-]{1,30})\\b");

    /** keyword -> tool. Order matters: the first two matches are used. */
    private static final Map<String, String> ROUTES = new LinkedHashMap<>();

    static {
        String[][] table = {
                {"break-even", "break_even"}, {"breakeven", "break_even"}, {"break even", "break_even"}, {"بریک", "break_even"},
                {"margin", "margin_drivers"}, {"cogs", "margin_drivers"}, {"cost of goods", "margin_drivers"}, {"مارجن", "margin_drivers"},
                {"inventory", "inventory_position"}, {"stock", "inventory_position"}, {"اسٹاک", "inventory_position"}, {"انوینٹری", "inventory_position"},
                {"budget", "budget_goal_status"}, {"goal", "budget_goal_status"}, {"target", "budget_goal_status"},
                {"بجٹ", "budget_goal_status"}, {"ہدف", "budget_goal_status"},
                {"overdue", "overdue_receivables"}, {"receivable", "overdue_receivables"}, {"owe me", "overdue_receivables"},
                {"واجب الوصول", "overdue_receivables"}, {"وصولی", "overdue_receivables"},
                {"upcoming", "upcoming_obligations"}, {"due", "upcoming_obligations"}, {"payable", "upcoming_obligations"},
                {"ادائیگی", "upcoming_obligations"},
                {"cash", "cash_position"}, {"bank", "cash_position"}, {"balance", "cash_position"}, {"نقد", "cash_position"}, {"کیش", "cash_position"},
                {"profit", "profit_and_loss"}, {"loss", "profit_and_loss"}, {"revenue", "profit_and_loss"}, {"sales", "profit_and_loss"},
                {"expense", "profit_and_loss"}, {"منافع", "profit_and_loss"}, {"آمدنی", "profit_and_loss"}, {"اخراجات", "profit_and_loss"},
                {"exchange", "fx_impact"}, {"currency", "fx_impact"}, {"fx", "fx_impact"}, {"زرمبادلہ", "fx_impact"},
                {"health", "health_evidence"}, {"score", "health_evidence"}, {"صحت", "health_evidence"},
                {"what should", "action_center"}, {"priorit", "action_center"}, {"action", "action_center"}, {"اقدامات", "action_center"},
                {"کیا کروں", "action_center"},
        };
        for (String[] r : table) ROUTES.put(r[0], r[1]);
    }

    @Override
    public String mode() {
        return "deterministic";
    }

    @Override
    public String model() {
        return null;
    }

    @Override
    public Step next(CopilotTypes.Conversation c) {
        if (c.round() == 0) {
            List<ToolCall> calls = route(c.question());
            if (!calls.isEmpty()) return Step.calls(calls);
            return Step.answer(unsupported(c.language()), List.of());
        }
        return render(c.language(), c.priorResults());
    }

    static List<ToolCall> route(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        List<ToolCall> calls = new ArrayList<>();
        Matcher project = PROJECT_CODE.matcher(question == null ? "" : question);
        if (project.find() && (q.contains("project") || q.contains("job") || q.contains("order") || q.contains("منصوبہ"))) {
            calls.add(new ToolCall("call-1", "project_profitability", Map.of("projectCode", project.group(1))));
        }
        for (Map.Entry<String, String> route : ROUTES.entrySet()) {
            if (calls.size() >= 2) break;
            if (q.contains(route.getKey()) && calls.stream().noneMatch(call -> call.name().equals(route.getValue()))) {
                calls.add(new ToolCall("call-" + (calls.size() + 1), route.getValue(), Map.of()));
            }
        }
        return calls;
    }

    static Step render(String language, List<ToolResult> results) {
        boolean ur = "ur".equals(language);
        StringBuilder text = new StringBuilder();
        List<String> cited = new ArrayList<>();
        for (ToolResult r : results) {
            if (!"OK".equals(r.status())) {
                text.append(ur ? "یہ معلومات ابھی دستیاب نہیں (" : "This information is not available right now (")
                        .append(r.name()).append("). ");
                continue;
            }
            Map<String, Evidence> byId = new LinkedHashMap<>();
            for (Evidence e : r.evidence()) byId.put(e.id(), e);
            List<String[]> picks = switch (r.name()) {
                case "profit_and_loss" -> List.of(
                        new String[] {"profit_and_loss.revenue", "Recognized revenue", "تسلیم شدہ آمدنی"},
                        new String[] {"profit_and_loss.costOfSales", "Cost of sales", "فروخت کی لاگت"},
                        new String[] {"profit_and_loss.operatingExpenses", "Operating expenses", "آپریٹنگ اخراجات"},
                        new String[] {"profit_and_loss.operatingProfit", "Operating profit", "آپریٹنگ منافع"});
                case "margin_drivers" -> List.of(
                        new String[] {"margin_drivers.revenue", "Revenue", "آمدنی"},
                        new String[] {"margin_drivers.costOfGoodsSold", "Cost of goods sold", "فروخت شدہ مال کی لاگت"},
                        new String[] {"margin_drivers.grossProfit", "Gross profit", "مجموعی منافع"},
                        new String[] {"margin_drivers.grossMarginPct", "Gross margin %", "مجموعی مارجن %"});
                case "inventory_position" -> List.of(
                        new String[] {"inventory_position.totalValue", "Inventory value", "اسٹاک کی مالیت"},
                        new String[] {"inventory_position.itemsBelowReorder", "Items below reorder level", "دوبارہ آرڈر سطح سے نیچے اشیاء"});
                case "break_even" -> List.of(
                        new String[] {"break_even.status", "Status", "حیثیت"},
                        new String[] {"break_even.breakEvenRevenue", "Break-even revenue", "بریک ایون آمدنی"},
                        new String[] {"break_even.contributionMarginPct", "Contribution margin %", "کنٹریبیوشن مارجن %"},
                        new String[] {"break_even.reason", "Reason", "وجہ"});
                case "cash_position" -> firstMatching(byId, "cash_position.", List.of("totalBalance", "totalCash", "total"),
                        "Cash position", "نقد پوزیشن");
                case "overdue_receivables" -> firstMatching(byId, "overdue_receivables.", List.of("totalOverdue", "totalOutstanding", "overdue"),
                        "Overdue receivables", "واجب الوصول رقم");
                case "project_profitability" -> List.of(
                        new String[] {"project_profitability.revenue.total", "Project revenue", "منصوبے کی آمدنی"},
                        new String[] {"project_profitability.costs.total", "Project cost", "منصوبے کی لاگت"},
                        new String[] {"project_profitability.profitability.profit", "Project profit", "منصوبے کا منافع"},
                        new String[] {"project_profitability.profitability.status", "Status", "حیثیت"});
                case "action_center" -> List.of(
                        new String[] {"action_center.actions[0].title", "Top action", "اولین اقدام"},
                        new String[] {"action_center.actions[0].reason", "Why", "وجہ"},
                        new String[] {"action_center.actions[1].title", "Next action", "اگلا اقدام"},
                        new String[] {"action_center.count", "Open actions", "کھلے اقدامات"});
                default -> List.of();
            };
            int used = 0;
            for (String[] pick : picks) {
                Evidence e = byId.get(pick[0]);
                if (e == null || e.value() == null || "null".equals(e.value())) continue;
                text.append(ur ? pick[2] : pick[1]).append(": ").append(e.value()).append(". ");
                cited.add(e.id());
                used++;
            }
            if (used == 0) {
                // Generic, still grounded: the first few evidence values of this tool.
                for (Evidence e : r.evidence().stream().filter(x -> !x.id().endsWith(".completeness")).limit(4).toList()) {
                    text.append(e.label()).append(": ").append(e.value()).append(". ");
                    cited.add(e.id());
                }
            }
            Evidence completeness = byId.get(r.name() + ".completeness");
            if (completeness != null && !"COMPLETE".equals(completeness.value())) {
                text.append(ur ? "نوٹ: ڈیٹا نامکمل ہے. " : "Note: the underlying data is incomplete. ");
                cited.add(completeness.id());
            }
        }
        if (text.isEmpty()) return Step.answer(unsupported(language), List.of());
        return Step.answer(text.toString().trim(), cited);
    }

    private static List<String[]> firstMatching(Map<String, Evidence> byId, String prefix, List<String> keys, String en, String ur) {
        for (String k : keys) {
            if (byId.containsKey(prefix + k)) return List.<String[]>of(new String[] {prefix + k, en, ur});
        }
        return List.of();
    }

    static String unsupported(String language) {
        return "ur".equals(language)
                ? "میں نقد، واجب الوصول رقم، آنے والی ادائیگیاں، منافع و نقصان، مارجن، اسٹاک، بجٹ و اہداف، بریک ایون، منصوبے، زرمبادلہ اور ترجیحی اقدامات کے بارے میں بتا سکتا ہوں۔"
                : "I can answer about cash, overdue receivables, upcoming obligations, profit and loss, margins, inventory, budgets and goals, break-even, projects, FX and prioritized actions.";
    }
}
