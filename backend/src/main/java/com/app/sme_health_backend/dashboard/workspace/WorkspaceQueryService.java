package com.app.sme_health_backend.dashboard.workspace;

import com.app.sme_health_backend.dashboard.service.DashboardService;
import com.app.sme_health_backend.dashboard.dto.DashboardResponse;
import com.app.sme_health_backend.cashflow.dto.CashFlowChartPointResponse;
import com.app.sme_health_backend.records.dto.MonthlyRecordResponse;
import com.app.sme_health_backend.records.service.MonthlyRecordService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import static com.app.sme_health_backend.dashboard.workspace.WorkspaceTypes.*;

/** Presentation adapter only: financial facts and per-month net come from the existing services/DTO. */
@Service
public class WorkspaceQueryService {
    public record Summary(DashboardResponse dashboard, List<MonthlyRecordResponse> records,
            List<CashFlowChartPointResponse> cashHistory, Map<String, CashSummary> cashSummaryByPeriod) { }
    private final DashboardService dashboards;
    private final MonthlyRecordService records;
    private final BusinessAuthorizationService authorization;
    public WorkspaceQueryService(DashboardService dashboards, MonthlyRecordService records, BusinessAuthorizationService authorization) {
        this.dashboards = dashboards; this.records = records; this.authorization = authorization;
    }
    public Summary query(BusinessAccessContext actor) {
        if (!authorization.hasRolePermission(actor.role(), BusinessPermission.FINANCIAL_DATA_READ))
            throw new AccessDeniedException("Financial read permission required");
        DashboardResponse dashboard = dashboards.getDashboard(actor.businessId());
        var canonical = records.getUserRecords(actor.businessId());
        var responseRecords = canonical.stream().map(MonthlyRecordResponse::fromEntity).toList();
        var history = canonical.stream().map(CashFlowChartPointResponse::fromEntity)
                .sorted(Comparator.comparing(CashFlowChartPointResponse::getMonth)).toList();
        return new Summary(dashboard, responseRecords, history, cashSummaries(history));
    }
    public Map<String, CashSummary> cashSummaries(List<CashFlowChartPointResponse> history) {
        var chronological = history.stream().sorted(Comparator.comparing(CashFlowChartPointResponse::getMonth)).toList();
        Map<String, CashSummary> summaries = new LinkedHashMap<>();
        for (String period : List.of("3", "6", "12", "all")) {
            List<CashFlowChartPointResponse> selected = chronological;
            if (!chronological.isEmpty() && !period.equals("all")) {
                YearMonth last = YearMonth.parse(chronological.getLast().getMonth());
                selected = chronological.stream().filter(p -> ChronoUnit.MONTHS.between(YearMonth.parse(p.getMonth()), last) < Integer.parseInt(period)).toList();
            }
            summaries.put(period, new CashSummary(sum(selected, CashFlowChartPointResponse::getInflow),
                    sum(selected, CashFlowChartPointResponse::getOutflow), sum(selected, CashFlowChartPointResponse::getNet),
                    selected.isEmpty() ? null : selected.getLast().getRunningBalance(),
                    selected.isEmpty() ? null : selected.getFirst().getMonth(),
                    selected.isEmpty() ? null : selected.getLast().getMonth(), selected.size()));
        }
        return Collections.unmodifiableMap(summaries);
    }
    private BigDecimal sum(List<CashFlowChartPointResponse> points, Function<CashFlowChartPointResponse, BigDecimal> value) {
        if (points.isEmpty() || points.stream().anyMatch(p -> value.apply(p) == null)) return null;
        return points.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
