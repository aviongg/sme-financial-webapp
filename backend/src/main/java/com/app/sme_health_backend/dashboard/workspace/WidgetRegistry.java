package com.app.sme_health_backend.dashboard.workspace;

import com.app.sme_health_backend.identity.model.BusinessPermission;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.app.sme_health_backend.identity.model.BusinessPermission.*;

@Component
public class WidgetRegistry {
    public static final String VERSION = "f13-v1";
    public record Definition(String key, String module, boolean implemented, boolean required,
            List<String> sizes, BusinessPermission permission) { }
    private static Definition core(String key) {
        return new Definition(key, "core_finance", true, false, List.of("compact", "wide"), FINANCIAL_DATA_READ);
    }
    private static Definition future(String key, String module) {
        return new Definition(key, module, false, false, List.of("compact", "wide"), FINANCIAL_DATA_READ);
    }
    private final List<Definition> definitions = List.of(
            new Definition("health-score", "core_finance", true, true, List.of("wide"), FINANCIAL_DATA_READ),
            core("health-components"), core("score-completeness"), core("cash-flow"), core("cash-projection"),
            core("financial-evidence"), core("next-step"), core("recommendation"), core("financial-summary"),
            new Definition("ai-explanation", "ai", true, false, List.of("compact", "wide"), FINANCIAL_DATA_READ),
            new Definition("document-workflow", "documents", true, false, List.of("compact", "wide"), DOCUMENT_READ),
            future("transactions", "transactions"), future("receivables", "receivables"), future("payables", "receivables"),
            future("invoices", "invoices"), future("projects", "projects"), future("costing", "costing"),
            future("inventory", "inventory"), future("production", "production"), future("commissions", "commissions"),
            future("multi-currency", "multi_currency"), future("budgets", "core_finance"),
            future("funding-gap", "projects"), future("reports", "reports"));
    public List<Definition> all() { return definitions; }
    public Optional<Definition> find(String key) { return definitions.stream().filter(d -> d.key().equals(key)).findFirst(); }
}
