package com.app.sme_health_backend.records.projection.controller;

import com.app.sme_health_backend.financialquery.dto.FinancialEvidenceReference;
import com.app.sme_health_backend.financialquery.exception.FinancialQueryValidationException;
import com.app.sme_health_backend.financialquery.model.CapabilityStatus;
import com.app.sme_health_backend.financialquery.model.DataCompleteness;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.records.projection.dto.*;
import com.app.sme_health_backend.records.projection.model.ComparisonMetricStatus;
import com.app.sme_health_backend.records.projection.model.RecognitionBasis;
import com.app.sme_health_backend.records.projection.service.MonthlyLedgerProjectionService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MonthlyLedgerProjectionController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("MonthlyLedgerProjectionController WebMvc Tests")
class MonthlyLedgerProjectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonthlyLedgerProjectionService projectionService;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private BusinessAccessContext context;
    private UUID businessId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
    }

    @Test
    @DisplayName("GET /api/records/monthly/projection returns HTTP 200 with monthly ledger breakdown")
    void testGetMonthlyLedgerProjection_Success() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ)))
                .thenReturn(context);

        MonthlyOperatingCashFlow cashFlow = new MonthlyOperatingCashFlow(
                "PKR", "30000.00", "0.00", "30000.00", "0.00", "0.00", "0.00", "0.00", "30000.00",
                "8000.00", "0.00", "8000.00", "0.00", "0.00", "0.00", "0.00", "8000.00", "22000.00",
                "10000.00", "10000.00", "0.00", "22000.00"
        );
        MonthlyRecognizedFigures recognized = new MonthlyRecognizedFigures(
                "PKR",
                "30000.00", "0.00", "0.00", CapabilityStatus.AVAILABLE, DataCompleteness.COMPLETE, "30000.00", "30000.00",
                "8000.00", "0.00", "0.00", "8000.00", "8000.00",
                CapabilityStatus.UNAVAILABLE, null, "F08 dependency",
                CapabilityStatus.UNAVAILABLE, null, "F08 dependency",
                CapabilityStatus.UNAVAILABLE, null, "Pending complete accrual",
                CapabilityStatus.UNAVAILABLE, null, "Pending COGS & tax",
                "22000.00", "22000.00", "Notes"
        );
        MonthlyObligationPositions positions = new MonthlyObligationPositions(
                LocalDate.of(2026, 10, 31), "PKR", "0.00", 0, "0.00", 0, "0.00", "0.00", "0.00"
        );
        MonthlyDraftSummary draftSummary = new MonthlyDraftSummary(0, "0.00", 0, "0.00", true);

        MonthlyLedgerProjectionResponse response = new MonthlyLedgerProjectionResponse(
                "1.0", businessId, "2026-10",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 31),
                DataCompleteness.COMPLETE, Collections.emptyList(), Collections.emptyList(),
                List.of(new CurrencyMonthlyCashSummary("PKR", 2, "100000.00", "0.00", "30000.00", "8000.00", "22000.00", "122000.00", 1)),
                Collections.emptyList(), cashFlow, recognized, positions, draftSummary
        );

        when(projectionService.projectMonthlyLedger(eq(context), eq("2026-10"), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/records/monthly/projection")
                        .param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value("1.0"))
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.cashFlow.totalCashInflow").value("30000.00"))
                .andExpect(jsonPath("$.cashFlow.totalCashOutflow").value("8000.00"))
                .andExpect(jsonPath("$.cashFlow.netOperatingCashFlow").value("22000.00"))
                .andExpect(jsonPath("$.recognizedFigures.cogsStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.currencySummaries[0].totalClosingBalance").value("122000.00"));

        verify(projectionService).projectMonthlyLedger(eq(context), eq("2026-10"), any());
    }

    @Test
    @DisplayName("GET /api/records/monthly/comparison returns HTTP 200 with shadow comparison payload")
    void testGetMonthlyShadowComparison_Success() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ)))
                .thenReturn(context);

        MonthlyMetricComparison comp = new MonthlyMetricComparison(
                "cashInflow", "Cash Inflow", "30000.00", "30000.00", "0.00",
                ComparisonMetricStatus.MATCH, RecognitionBasis.CASH_FLOW, "Direct match"
        );

        MonthlyShadowComparisonResponse response = new MonthlyShadowComparisonResponse(
                "1.0", businessId, "2026-10", LocalDate.of(2026, 10, 31),
                true, UUID.randomUUID(), List.of(comp), 1, 0, 0,
                "Comparison completed", List.of("Note 1")
        );

        when(projectionService.compareWithMonthlyRecord(eq(context), eq("2026-10"), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/records/monthly/comparison")
                        .param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value("1.0"))
                .andExpect(jsonPath("$.hasMonthlyRecord").value(true))
                .andExpect(jsonPath("$.matchingMetricCount").value(1))
                .andExpect(jsonPath("$.comparisons[0].metricKey").value("cashInflow"))
                .andExpect(jsonPath("$.comparisons[0].status").value("MATCH"));
    }

    @Test
    @DisplayName("GET /api/records/monthly/projection without permission throws HTTP 403")
    void testGetMonthlyLedgerProjection_Forbidden() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ)))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/api/records/monthly/projection")
                        .param("month", "2026-10"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/records/monthly/projection with invalid month returns HTTP 400")
    void testGetMonthlyLedgerProjection_InvalidMonth_Returns400() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ)))
                .thenReturn(context);

        when(projectionService.projectMonthlyLedger(eq(context), eq("bad-month"), any()))
                .thenThrow(new FinancialQueryValidationException("month", "Invalid month format. Expected YYYY-MM"));

        mockMvc.perform(get("/api/records/monthly/projection")
                        .param("month", "bad-month"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.month").value("Invalid month format. Expected YYYY-MM"));
    }
}
