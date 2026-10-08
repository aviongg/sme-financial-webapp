package com.app.sme_health_backend.cashflow;

import com.app.sme_health_backend.cashflow.controller.CashFlowController;
import com.app.sme_health_backend.cashflow.dto.CashFlowChartPointResponse;
import com.app.sme_health_backend.cashflow.dto.CashFlowProjectionResponse;
import com.app.sme_health_backend.cashflow.service.CashFlowService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CashFlowController.class)
@Import(GlobalExceptionHandler.class)
class CashFlowControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CashFlowService cashFlowService;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private final UUID userId = UUID.randomUUID();
    private final UUID businessId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), any(BusinessPermission.class))).thenReturn(context);
    }

    @Test
    void shouldReturnCashFlowHistorySuccess() throws Exception {
        List<CashFlowChartPointResponse> response = List.of(
                new CashFlowChartPointResponse(
                        "2026-07",
                        new BigDecimal("120000"),
                        new BigDecimal("80000"),
                        new BigDecimal("40000"),
                        new BigDecimal("150000")
                ),
                new CashFlowChartPointResponse(
                        "2026-08",
                        new BigDecimal("150000"),
                        new BigDecimal("100000"),
                        new BigDecimal("50000"),
                        new BigDecimal("200000")
                )
        );

        when(cashFlowService.getCashFlowHistory(businessId)).thenReturn(response);

        mockMvc.perform(get("/api/cashflow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].month").value("2026-07"))
                .andExpect(jsonPath("$[0].inflow").value(120000))
                .andExpect(jsonPath("$[0].outflow").value(80000))
                .andExpect(jsonPath("$[0].net").value(40000))
                .andExpect(jsonPath("$[0].runningBalance").value(150000))
                .andExpect(jsonPath("$[1].month").value("2026-08"))
                .andExpect(jsonPath("$[1].inflow").value(150000))
                .andExpect(jsonPath("$[1].outflow").value(100000))
                .andExpect(jsonPath("$[1].net").value(50000))
                .andExpect(jsonPath("$[1].runningBalance").value(200000));
    }

    @Test
    void shouldReturnEmptyArrayWhenNoHistory() throws Exception {
        when(cashFlowService.getCashFlowHistory(businessId)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/cashflow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturnTrendProjectionSuccess() throws Exception {
        CashFlowProjectionResponse response = new CashFlowProjectionResponse(
                "2026-10",
                new BigDecimal("320000.00"),
                "upward",
                "reasonable",
                6,
                null
        );

        when(cashFlowService.getTrendProjection(businessId)).thenReturn(response);

        mockMvc.perform(get("/api/cashflow/projection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectedMonth").value("2026-10"))
                .andExpect(jsonPath("$.projectedNetCashFlow").value(320000.00))
                .andExpect(jsonPath("$.trendDirection").value("upward"))
                .andExpect(jsonPath("$.confidence").value("reasonable"))
                .andExpect(jsonPath("$.historicalMonthsCount").value(6))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void shouldReturnTrendProjectionWithInsufficientData() throws Exception {
        CashFlowProjectionResponse response = CashFlowProjectionResponse.insufficientData(1);

        when(cashFlowService.getTrendProjection(businessId)).thenReturn(response);

        mockMvc.perform(get("/api/cashflow/projection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectedMonth").doesNotExist())
                .andExpect(jsonPath("$.projectedNetCashFlow").doesNotExist())
                .andExpect(jsonPath("$.trendDirection").doesNotExist())
                .andExpect(jsonPath("$.confidence").doesNotExist())
                .andExpect(jsonPath("$.historicalMonthsCount").value(1))
                .andExpect(jsonPath("$.message").value("Need at least 3 months of data for a trend"));
    }

    @Test
    void shouldRejectLegacyCashFlowWithUserIdUrl() throws Exception {
        mockMvc.perform(get("/api/cashflow/{userId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectLegacyProjectionWithUserIdUrl() throws Exception {
        mockMvc.perform(get("/api/cashflow/{userId}/projection", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
