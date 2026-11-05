package com.app.sme_health_backend.dashboard.workspace;

import com.app.sme_health_backend.cashflow.dto.CashFlowChartPointResponse;
import com.app.sme_health_backend.dashboard.dto.DashboardResponse;
import com.app.sme_health_backend.dashboard.service.DashboardService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.service.MonthlyRecordService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkspaceQueryServiceTests {
    final DashboardService dashboards = mock(DashboardService.class);
    final MonthlyRecordService records = mock(MonthlyRecordService.class);
    final WorkspaceQueryService query = new WorkspaceQueryService(dashboards, records, new BusinessAuthorizationService(null));
    CashFlowChartPointResponse point(String month, String inflow, String outflow, String ending) {
        BigDecimal in = inflow == null ? null : new BigDecimal(inflow), out = outflow == null ? null : new BigDecimal(outflow);
        return new CashFlowChartPointResponse(month, in, out, in == null || out == null ? null : in.subtract(out), new BigDecimal(ending));
    }
    @Test void exactCalendarWindowTotalsPreserveSparseMonthsAndLatestBalance() {
        var summaries = query.cashSummaries(List.of(point("2026-09", "0.10", "0.03", "-23.45"),
                point("2026-06", "8000.00", "50.00", "200.00"), point("2026-08", "0.20", "0.02", "98.00")));
        var three = summaries.get("3");
        assertEquals(2, three.recordCount()); assertEquals("2026-08", three.fromMonth());
        assertEquals(new BigDecimal("0.30"), three.totalInflow()); assertEquals(new BigDecimal("0.25"), three.net());
        assertEquals(new BigDecimal("-23.45"), three.endingCash());
        assertEquals(new BigDecimal("8000.30"), summaries.get("6").totalInflow());
        assertEquals(summaries.get("6"), summaries.get("all"));
    }
    @Test void missingAndZeroAreDistinctWithoutInventedTotals() {
        var missing = query.cashSummaries(List.of(point("2026-09", null, "0.00", "0.00"))).get("all");
        assertNull(missing.totalInflow()); assertNull(missing.net()); assertEquals(new BigDecimal("0.00"), missing.totalOutflow());
        var empty = query.cashSummaries(List.of()).get("3");
        assertNull(empty.totalInflow()); assertNull(empty.endingCash()); assertNull(empty.fromMonth()); assertEquals(0, empty.recordCount());
    }
    @Test void batchesCanonicalServicesOnceAndPassesScoreAndNullableRecordsUnchanged() {
        var actor = new BusinessAccessContext(UUID.randomUUID(), UUID.randomUUID(), MembershipRole.VIEWER);
        var dashboard = new DashboardResponse(); dashboard.setBusinessId(actor.businessId());
        var record = new MonthlyRecord(); record.setUserId(actor.businessId()); record.setMonth("2026-09");
        record.setRevenue(new BigDecimal("12345.67")); record.setCogs(null);
        when(dashboards.getDashboard(actor.businessId())).thenReturn(dashboard);
        when(records.getUserRecords(actor.businessId())).thenReturn(List.of(record));
        var result = query.query(actor);
        assertSame(dashboard, result.dashboard());
        assertEquals(new BigDecimal("12345.67"), result.records().getFirst().getRevenue()); assertNull(result.records().getFirst().getCogs());
        verify(dashboards, times(1)).getDashboard(actor.businessId()); verify(records, times(1)).getUserRecords(actor.businessId());
        verifyNoMoreInteractions(dashboards, records);
    }
}
