package com.app.sme_health_backend.commission.controller;

import com.app.sme_health_backend.commission.dto.CommissionAnalyticsOverviewResponse;
import com.app.sme_health_backend.commission.dto.CommissionAuditEventResponse;
import com.app.sme_health_backend.commission.dto.CommissionPeriodTrendResponse;
import com.app.sme_health_backend.commission.dto.SalespersonPerformanceResponse;
import com.app.sme_health_backend.commission.dto.SalespersonStatementResponse;
import com.app.sme_health_backend.commission.model.CommissionAuditEventType;
import com.app.sme_health_backend.commission.model.SalespersonStatus;
import com.app.sme_health_backend.commission.service.CommissionReportingService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommissionReportingController.class)
@Import(GlobalExceptionHandler.class)
class CommissionReportingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommissionReportingService reportingService;

    @MockitoBean
    private BusinessAuthorizationService authorizationService;

    private final UUID businessId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID salespersonId = UUID.randomUUID();

    private BusinessAccessContext ownerContext;
    private BusinessAccessContext salespersonContext;

    @BeforeEach
    void setUp() {
        ownerContext = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        salespersonContext = new BusinessAccessContext(userId, businessId, MembershipRole.SALESPERSON);
    }

    @Test
    @DisplayName("GET /api/finance/commissions/analytics/overview: Returns 200 with flow and stock metrics")
    void testGetOverview_Success() throws Exception {
        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_READ)))
                .thenReturn(ownerContext);

        CommissionAnalyticsOverviewResponse response = new CommissionAnalyticsOverviewResponse(
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                BigDecimal.ZERO,
                new BigDecimal("2000.00"),
                new BigDecimal("2000.00"),
                BigDecimal.ZERO,
                new BigDecimal("3000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("4000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("4000.00"),
                null, null, null, null, null, null, null, null, null, null, null,
                "PKR", 1, 1
        );

        when(reportingService.getOverview(eq(ownerContext), any(), any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/finance/commissions/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEarned").value(5000.00))
                .andExpect(jsonPath("$.totalApproved").value(5000.00))
                .andExpect(jsonPath("$.totalNetPayouts").value(2000.00))
                .andExpect(jsonPath("$.totalOutstandingPayable").value(1000.00))
                .andExpect(jsonPath("$.totalNetCashOutflow").value(4000.00))
                .andExpect(jsonPath("$.currency").value("PKR"));
    }

    @Test
    @DisplayName("GET /api/finance/commissions/analytics/overview: Returns 403 Forbidden when requested by SALESPERSON role")
    void testGetOverview_SalespersonForbidden() throws Exception {
        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_READ)))
                .thenReturn(salespersonContext);

        mockMvc.perform(get("/api/finance/commissions/analytics/overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/finance/commissions/analytics/salespersons: Returns 200 with performance list")
    void testGetSalespersonBreakdown_Success() throws Exception {
        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_READ)))
                .thenReturn(ownerContext);

        SalespersonPerformanceResponse spResponse = new SalespersonPerformanceResponse(
                salespersonId,
                "Tariq Mahmood",
                "tariq@example.com",
                SalespersonStatus.ACTIVE,
                3,
                new BigDecimal("100000.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                BigDecimal.ZERO,
                new BigDecimal("2000.00"),
                new BigDecimal("2000.00"),
                BigDecimal.ZERO,
                new BigDecimal("3000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("4000.00"),
                new BigDecimal("1000.00"),
                "PKR"
        );

        when(reportingService.getSalespersonBreakdown(eq(ownerContext), any(), any()))
                .thenReturn(List.of(spResponse));

        mockMvc.perform(get("/api/finance/commissions/analytics/salespersons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Tariq Mahmood"))
                .andExpect(jsonPath("$[0].earnedAmount").value(5000.00))
                .andExpect(jsonPath("$[0].outstandingPayable").value(1000.00));
    }

    @Test
    @DisplayName("GET /api/finance/commissions/analytics/periods: Returns 200 with monthly trends")
    void testGetPeriodTrends_Success() throws Exception {
        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_READ)))
                .thenReturn(ownerContext);

        CommissionPeriodTrendResponse trend = new CommissionPeriodTrendResponse(
                "2026-11",
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("4000.00"),
                "PKR"
        );

        when(reportingService.getPeriodTrends(eq(ownerContext), eq(6))).thenReturn(List.of(trend));

        mockMvc.perform(get("/api/finance/commissions/analytics/periods?months=6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].periodLabel").value("2026-11"))
                .andExpect(jsonPath("$[0].netCashOutflow").value(4000.00));
    }

    @Test
    @DisplayName("GET /api/finance/commissions/analytics/audit-ledger: Returns 200 with event log")
    void testGetAuditLedger_Success() throws Exception {
        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_READ)))
                .thenReturn(ownerContext);

        CommissionAuditEventResponse event = new CommissionAuditEventResponse(
                UUID.randomUUID(),
                Instant.now(),
                salespersonId,
                "Tariq Mahmood",
                UUID.randomUUID(),
                "Al-Madina Traders",
                CommissionAuditEventType.EARNING_RECOGNIZED,
                new BigDecimal("5000.00"),
                "PKR",
                "EARNED",
                "earning-123",
                null,
                null,
                "Commission recognized"
        );

        when(reportingService.getAuditLedger(eq(ownerContext), any(), any(), any(), any()))
                .thenReturn(List.of(event));

        mockMvc.perform(get("/api/finance/commissions/analytics/audit-ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].salespersonName").value("Tariq Mahmood"))
                .andExpect(jsonPath("$[0].eventType").value("EARNING_RECOGNIZED"))
                .andExpect(jsonPath("$[0].amount").value(5000.00));
    }

    @Test
    @DisplayName("GET /api/finance/commissions/statements/{id}: Returns 200 with structured statement")
    void testGetStatement_Success() throws Exception {
        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_READ)))
                .thenReturn(ownerContext);

        SalespersonStatementResponse statement = new SalespersonStatementResponse(
                businessId,
                "FinSight Demo Corp",
                salespersonId,
                "Tariq Mahmood",
                "tariq@example.com",
                null,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                Instant.now(),
                BigDecimal.ZERO,
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("4000.00"),
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                "PKR",
                List.of(),
                List.of(),
                List.of(),
                "Provenance Notes"
        );

        when(reportingService.getSalespersonStatement(eq(ownerContext), eq(salespersonId), any(), any()))
                .thenReturn(statement);

        mockMvc.perform(get("/api/finance/commissions/statements/" + salespersonId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salespersonName").value("Tariq Mahmood"))
                .andExpect(jsonPath("$.closingSettlementPosition").value(1000.00))
                .andExpect(jsonPath("$.periodTotalSettled").value(4000.00));
    }

    @Test
    @DisplayName("GET /api/finance/commissions/statements/{id}/export: Returns CSV attachment with correct headers")
    void testExportStatementCsv_Success() throws Exception {
        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_READ)))
                .thenReturn(ownerContext);

        String sampleCsv = "FINSIGHT COMMISSION STATEMENT\nBusiness,FinSight Demo Corp\n";
        byte[] bytes = sampleCsv.getBytes(StandardCharsets.UTF_8);

        when(reportingService.exportStatementCsv(eq(ownerContext), eq(salespersonId), any(), any()))
                .thenReturn(bytes);

        mockMvc.perform(get("/api/finance/commissions/statements/" + salespersonId + "/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"commission-statement-" + salespersonId + ".csv\""))
                .andExpect(content().contentType("text/csv; charset=UTF-8"))
                .andExpect(content().string(sampleCsv));
    }

    @Test
    @DisplayName("GET /api/finance/commissions/my-statement: Salesperson fetches own statement")
    void testGetMyStatement_Success() throws Exception {
        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_READ)))
                .thenReturn(salespersonContext);

        SalespersonStatementResponse statement = new SalespersonStatementResponse(
                businessId,
                "FinSight Demo Corp",
                salespersonId,
                "Tariq Mahmood",
                "tariq@example.com",
                null,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                Instant.now(),
                BigDecimal.ZERO,
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("5000.00"),
                BigDecimal.ZERO,
                "PKR",
                List.of(),
                List.of(),
                List.of(),
                "Provenance Notes"
        );

        when(reportingService.getSalespersonStatement(eq(salespersonContext), eq(null), any(), any()))
                .thenReturn(statement);

        mockMvc.perform(get("/api/finance/commissions/my-statement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salespersonName").value("Tariq Mahmood"))
                .andExpect(jsonPath("$.closingSettlementPosition").value(5000.00));
    }
}
