package com.app.sme_health_backend;

import com.app.sme_health_backend.records.controller.MonthlyRecordController;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.service.MonthlyRecordService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MonthlyRecordController.class)
@Import(GlobalExceptionHandler.class)
class MonthlyRecordControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonthlyRecordService monthlyRecordService;

    @Test
    void shouldCreateMonthlyRecord() throws Exception {
        UUID userId = UUID.randomUUID();

        MonthlyRecord record = validRecord(userId);

        when(monthlyRecordService.saveMonthlyRecord(any()))
                .thenReturn(record);

        String request = """
                {
                  "userId": "%s",
                  "month": "2026-09",
                  "cashInflow": 500000,
                  "cashOutflow": 200000,
                  "revenue": 500000,
                  "cogs": 150000,
                  "operatingExpenses": 50000,
                  "cashBalanceEom": 300000,
                  "financingType": "none"
                }
                """.formatted(userId);

        mockMvc.perform(post("/api/records/monthly")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.revenue").value(500000))
                .andExpect(jsonPath("$.financingType").value("none"));
    }

    @Test
    void shouldRejectInvalidFinancingType() throws Exception {
        UUID userId = UUID.randomUUID();

        String request = """
                {
                  "userId": "%s",
                  "month": "2026-09",
                  "cashInflow": 500000,
                  "cashOutflow": 200000,
                  "revenue": 500000,
                  "cogs": 150000,
                  "operatingExpenses": 50000,
                  "cashBalanceEom": 300000,
                  "financingType": "invalid"
                }
                """.formatted(userId);

        mockMvc.perform(post("/api/records/monthly")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.financingType").exists());
    }

    @Test
    void shouldRejectMissingRequiredFields() throws Exception {
        UUID userId = UUID.randomUUID();

        String request = """
                {
                  "userId": "%s",
                  "month": "2026-09"
                }
                """.formatted(userId);

        when(monthlyRecordService.saveMonthlyRecord(any()))
                .thenThrow(new IllegalArgumentException("Cash inflow is required"));

        mockMvc.perform(post("/api/records/monthly")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldRejectNegativeFinancialValue() throws Exception {
        UUID userId = UUID.randomUUID();

        when(monthlyRecordService.saveMonthlyRecord(any()))
                .thenThrow(new IllegalArgumentException(
                        "Revenue cannot be negative"
                ));

        String request = """
                {
                  "userId": "%s",
                  "month": "2026-09",
                  "cashInflow": 500000,
                  "cashOutflow": 200000,
                  "revenue": -500000,
                  "cogs": 150000,
                  "operatingExpenses": 50000,
                  "cashBalanceEom": 300000,
                  "financingType": "none"
                }
                """.formatted(userId);

        mockMvc.perform(post("/api/records/monthly")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Revenue cannot be negative"));
    }

    @Test
    void shouldRejectInvalidMonth() throws Exception {
        UUID userId = UUID.randomUUID();

        when(monthlyRecordService.saveMonthlyRecord(any()))
                .thenThrow(new IllegalArgumentException(
                        "Month must be in YYYY-MM format"
                ));

        String request = """
                {
                  "userId": "%s",
                  "month": "2026-99",
                  "cashInflow": 500000,
                  "cashOutflow": 200000,
                  "revenue": 500000,
                  "cogs": 150000,
                  "operatingExpenses": 50000,
                  "cashBalanceEom": 300000,
                  "financingType": "none"
                }
                """.formatted(userId);

        mockMvc.perform(post("/api/records/monthly")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Month must be in YYYY-MM format"));
    }

    private MonthlyRecord validRecord(UUID userId) {
        MonthlyRecord record = new MonthlyRecord();

        record.setUserId(userId);
        record.setMonth("2026-09");
        record.setCashInflow(new BigDecimal("500000"));
        record.setCashOutflow(new BigDecimal("200000"));
        record.setRevenue(new BigDecimal("500000"));
        record.setCogs(new BigDecimal("150000"));
        record.setOperatingExpenses(new BigDecimal("50000"));
        record.setCashBalanceEom(new BigDecimal("300000"));
        record.setFinancingType("none");
        record.setUpdatedAt(LocalDateTime.now());

        return record;
    }
}