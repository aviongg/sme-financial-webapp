package com.app.sme_health_backend.cashflow;

import com.app.sme_health_backend.cashflow.controller.CashFlowController;
import com.app.sme_health_backend.cashflow.dto.CashFlowChartPointResponse;
import com.app.sme_health_backend.cashflow.service.CashFlowService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CashFlowController.class)
@Import(GlobalExceptionHandler.class)
class CashFlowControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CashFlowService cashFlowService;

    @Test
    void shouldReturnCashFlowHistorySuccess() throws Exception {
        UUID userId = UUID.randomUUID();

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

        when(cashFlowService.getCashFlowHistory(userId)).thenReturn(response);

        mockMvc.perform(get("/api/cashflow/{userId}", userId))
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
        UUID userId = UUID.randomUUID();

        when(cashFlowService.getCashFlowHistory(userId)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/cashflow/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturn400WhenUserIdIsMalformed() throws Exception {
        mockMvc.perform(get("/api/cashflow/not-a-valid-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid user ID format: not-a-valid-uuid"));
    }
}
