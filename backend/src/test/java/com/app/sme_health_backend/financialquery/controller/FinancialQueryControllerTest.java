package com.app.sme_health_backend.financialquery.controller;

import com.app.sme_health_backend.financialquery.FinancialQueryFacade;
import com.app.sme_health_backend.financialquery.dto.*;
import com.app.sme_health_backend.financialquery.exception.FinancialQueryValidationException;
import com.app.sme_health_backend.financialquery.model.CapabilityStatus;
import com.app.sme_health_backend.financialquery.model.DataCompleteness;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
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

@WebMvcTest(FinancialQueryController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("FinancialQueryController WebMvc Tests")
class FinancialQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinancialQueryFacade financialQueryFacade;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private BusinessAccessContext context;
    private UUID businessId;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        context = new BusinessAccessContext(UUID.randomUUID(), businessId, MembershipRole.OWNER);
    }

    @Test
    @DisplayName("GET /api/finance/query/capabilities returns capabilities for authorized user")
    void testGetCapabilitiesSuccess() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ))).thenReturn(context);

        CapabilitiesResponse resp = new CapabilitiesResponse(
                "1.0",
                businessId,
                Instant.now(),
                List.of(new CapabilityItem("getCashPosition", CapabilityStatus.AVAILABLE, "Cash position", null))
        );
        when(financialQueryFacade.getCapabilities(context)).thenReturn(resp);

        mockMvc.perform(get("/api/finance/query/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value("1.0"))
                .andExpect(jsonPath("$.capabilities[0].toolName").value("getCashPosition"))
                .andExpect(jsonPath("$.capabilities[0].status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("GET /api/finance/query/cash-position succeeds, ignores client businessId param, and serializes exact decimals")
    void testGetCashPositionSuccess() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_ACCOUNT_READ))).thenReturn(context);

        CashPositionResult data = new CashPositionResult(
                LocalDate.of(2026, 10, 20),
                List.of(new CurrencyCashSummary("PKR", 2, "100000.00", "15000.00", "115000.00", 1)),
                Collections.emptyList(),
                1
        );
        FinancialQueryEnvelope<CashPositionResult> envelope = FinancialQueryEnvelope.available(
                "getCashPosition", "1.0", businessId, "2026-10-20", DataCompleteness.COMPLETE,
                Collections.emptyList(), Collections.emptyList(), data
        );
        when(financialQueryFacade.getCashPosition(eq(context), any())).thenReturn(envelope);

        UUID spoofedBusinessId = UUID.randomUUID();
        mockMvc.perform(get("/api/finance/query/cash-position")
                        .param("asOfDate", "2026-10-20")
                        .param("businessId", spoofedBusinessId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolName").value("getCashPosition"))
                .andExpect(jsonPath("$.businessId").value(businessId.toString()))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.totalAccounts").value(1))
                .andExpect(jsonPath("$.data.currencySummaries[0].currency").value("PKR"))
                .andExpect(jsonPath("$.data.currencySummaries[0].currentBalance").value("115000.00"));

        verify(financialQueryFacade).getCashPosition(eq(context), eq(new CashPositionQuery("2026-10-20", null)));
    }

    @Test
    @DisplayName("GET /api/finance/query/overdue-receivables forwards limit and offset and handles validation errors with HTTP 400")
    void testGetOverdueReceivablesValidationAndPagination() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);
        when(financialQueryFacade.getOverdueReceivables(eq(context), eq(new OverdueReceivablesQuery(null, 500, null))))
                .thenThrow(new FinancialQueryValidationException("limit", "Query limit must be between 1 and 100"));

        mockMvc.perform(get("/api/finance/query/overdue-receivables")
                        .param("limit", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.limit").value("Query limit must be between 1 and 100"));

        OverdueReceivablesResult okRes = new OverdueReceivablesResult(
                LocalDate.of(2026, 10, 20), "PKR", "30000.00", 3, Collections.emptyList(), Collections.emptyList()
        );
        when(financialQueryFacade.getOverdueReceivables(eq(context), eq(new OverdueReceivablesQuery("2026-10-20", 10, 2))))
                .thenReturn(FinancialQueryEnvelope.available(
                        "getOverdueReceivables", "1.0", businessId, "2026-10-20",
                        DataCompleteness.PARTIAL, List.of("Paged"), Collections.emptyList(), okRes
                ));

        mockMvc.perform(get("/api/finance/query/overdue-receivables")
                        .param("asOfDate", "2026-10-20")
                        .param("limit", "10")
                        .param("offset", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completeness").value("PARTIAL"))
                .andExpect(jsonPath("$.data.totalOverdueAmount").value("30000.00"));
    }

    @Test
    @DisplayName("GET /api/finance/query/upcoming-obligations returns upcoming obligations and projections")
    void testGetUpcomingObligationsSuccess() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);

        UpcomingObligationsResult data = new UpcomingObligationsResult(
                LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 19), "PKR",
                "0.00", "50000.00", "0.00", "0.00", Collections.emptyList(), Collections.emptyList()
        );
        when(financialQueryFacade.getUpcomingObligations(eq(context), eq(new UpcomingObligationsQuery("PAYABLE", "2026-10-20", "2026-11-19"))))
                .thenReturn(FinancialQueryEnvelope.available(
                        "getUpcomingObligations", "1.0", businessId, "2026-10-20..2026-11-19",
                        DataCompleteness.COMPLETE, List.of("Non-binding"), Collections.emptyList(), data
                ));

        mockMvc.perform(get("/api/finance/query/upcoming-obligations")
                        .param("direction", "PAYABLE")
                        .param("fromDate", "2026-10-20")
                        .param("toDate", "2026-11-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolName").value("getUpcomingObligations"))
                .andExpect(jsonPath("$.data.totalUpcomingPayables").value("50000.00"));
    }

    @Test
    @DisplayName("All 4 unavailable tool endpoints return HTTP 200 with explicit UNAVAILABLE envelope and null data")
    void testAllFourUnavailableEndpoints() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ))).thenReturn(context);

        when(financialQueryFacade.getProjectFinancials(eq(context), any())).thenReturn(
                FinancialQueryEnvelope.unavailable("getProjectFinancials", "1.0", businessId, "F05 unavailable")
        );
        when(financialQueryFacade.getProjectFundingGap(eq(context), any())).thenReturn(
                FinancialQueryEnvelope.unavailable("getProjectFundingGap", "1.0", businessId, "F07 unavailable")
        );
        when(financialQueryFacade.getMarginDrivers(eq(context), any())).thenReturn(
                FinancialQueryEnvelope.unavailable("getMarginDrivers", "1.0", businessId, "F08 unavailable")
        );
        when(financialQueryFacade.getFxSettlementImpact(eq(context), any())).thenReturn(
                FinancialQueryEnvelope.unavailable("getFxSettlementImpact", "1.0", businessId, "F11 unavailable")
        );

        UUID projId = UUID.randomUUID();

        mockMvc.perform(get("/api/finance/query/project-financials").param("projectId", projId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolName").value("getProjectFinancials"))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.unavailableReason").value("F05 unavailable"));

        mockMvc.perform(get("/api/finance/query/project-funding-gap").param("projectId", projId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolName").value("getProjectFundingGap"))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/finance/query/margin-drivers").param("query", "cogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolName").value("getMarginDrivers"))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/finance/query/fx-settlement-impact").param("query", "fx"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolName").value("getFxSettlementImpact"))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("Malformed UUID projectId and non-integer limit return HTTP 400 Bad Request")
    void testInvalidProjectIdUuidReturnsBadRequest() throws Exception {
        when(authService.requirePermission(any(), any())).thenReturn(context);

        mockMvc.perform(get("/api/finance/query/project-financials")
                        .param("projectId", "not-a-valid-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.projectId").value("Invalid UUID format"));

        mockMvc.perform(get("/api/finance/query/overdue-receivables")
                        .param("limit", "not-an-int"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.limit").value("Invalid integer format"));
    }

    @Test
    @DisplayName("Endpoints deny unauthorized access when permission check fails with AccessDeniedException")
    void testUnauthorizedAccess() throws Exception {
        when(authService.requirePermission(any(), any()))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/api/finance/query/capabilities"))
                .andExpect(status().isForbidden());
    }
}
