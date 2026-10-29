package com.app.sme_health_backend.forecast.controller;

import com.app.sme_health_backend.financialquery.model.DataCompleteness;
import com.app.sme_health_backend.forecast.dto.*;
import com.app.sme_health_backend.forecast.exception.ForecastValidationException;
import com.app.sme_health_backend.forecast.service.DailyCashForecastService;
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
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CashForecastController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("CashForecastController WebMvc Tests (A-W6-ADVANCED-CASH-FORECAST-V1)")
class CashForecastControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DailyCashForecastService forecastService;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private BusinessAccessContext context;
    private final UUID businessId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final LocalDate evalDate = LocalDate.of(2026, 11, 1);

    @BeforeEach
    void setUp() {
        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ)))
                .thenReturn(context);
    }

    private DailyCashForecastResponse mockForecastResponse() {
        ForecastSummary summary = new ForecastSummary(
                "PKR", evalDate, 30, evalDate.plusDays(30),
                "100000.00", "105000.00", "100000.00", evalDate, null,
                "0.00", "40000.00", "35000.00", "5000.00",
                "0.00", "0.00", "0.00", "0.00", 0
        );
        ForecastCompleteness completeness = new ForecastCompleteness(
                DataCompleteness.COMPLETE, Collections.emptyList(),
                0, "0.00", false, false, 1, Collections.emptyList()
        );
        DailyForecastRow row = new DailyForecastRow(
                evalDate, 0, "100000.00", "0.00", "0.00", "0.00",
                "0.00", "0.00", "0.00", "0.00", "100000.00", false, "0.00", Collections.emptyList()
        );
        return new DailyCashForecastResponse(
                "1.0", "PKR", evalDate, 30, evalDate.plusDays(30),
                summary, completeness, List.of(row), "Forecast disclaimer"
        );
    }

    @Test
    @DisplayName("GET /api/finance/forecast/daily returns 200 OK with DailyCashForecastResponse")
    void testGetDailyForecast_Success() throws Exception {
        when(forecastService.getDailyCashForecast(eq(context), eq("PKR"), eq(30), eq("2026-11-01")))
                .thenReturn(mockForecastResponse());

        mockMvc.perform(get("/api/finance/forecast/daily")
                        .param("currency", "PKR")
                        .param("horizonDays", "30")
                        .param("asOfDate", "2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value("1.0"))
                .andExpect(jsonPath("$.currency").value("PKR"))
                .andExpect(jsonPath("$.summary.startingCash").value("100000.00"))
                .andExpect(jsonPath("$.summary.endingProjectedCash").value("105000.00"));
    }

    @Test
    @DisplayName("GET /api/finance/forecast/daily with invalid parameters returns 400 Bad Request")
    void testGetDailyForecast_ValidationFailure() throws Exception {
        when(forecastService.getDailyCashForecast(eq(context), eq("INVALID"), eq(30), any()))
                .thenThrow(new ForecastValidationException("currency", "Currency code must be a 3-letter ISO code"));

        mockMvc.perform(get("/api/finance/forecast/daily")
                        .param("currency", "INVALID")
                        .param("horizonDays", "30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    @Test
    @DisplayName("POST /api/finance/forecast/scenario-preview returns 200 OK with ForecastScenarioResponse")
    void testPreviewScenario_Success() throws Exception {
        DailyCashForecastResponse base = mockForecastResponse();
        DailyCashForecastResponse scen = mockForecastResponse();
        ForecastScenarioComparison comp = new ForecastScenarioComparison(
                "User Adjusted Scenario", Map.of("collectionDelayDays", 10),
                base.summary(), scen.summary(), "0.00", "0.00", "NO_DEFICIT", Collections.emptyList()
        );
        ForecastScenarioResponse response = new ForecastScenarioResponse("1.0", base, scen, comp);

        when(forecastService.previewScenario(eq(context), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/finance/forecast/scenario-preview")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currency": "PKR",
                                  "horizonDays": 30,
                                  "asOfDate": "2026-11-01",
                                  "collectionDelayDays": 10,
                                  "collectionPercentage": 100.00,
                                  "paymentDelayDays": 0,
                                  "includeOverdueReceivables": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value("1.0"))
                .andExpect(jsonPath("$.comparison.scenarioName").value("User Adjusted Scenario"))
                .andExpect(jsonPath("$.baseline.summary.startingCash").value("100000.00"));
    }

    @Test
    @DisplayName("POST /api/finance/forecast/scenario-preview with invalid percentage returns 400 Bad Request")
    void testPreviewScenario_ValidationFailure() throws Exception {
        when(forecastService.previewScenario(eq(context), any()))
                .thenThrow(new ForecastValidationException("collectionPercentage", "collectionPercentage must be between 0.00 and 100.00"));

        mockMvc.perform(post("/api/finance/forecast/scenario-preview")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currency": "PKR",
                                  "horizonDays": 30,
                                  "collectionPercentage": 150.00
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.collectionPercentage").exists());
    }

    @Test
    @DisplayName("Access denied throws 403 Forbidden when user lacks permission")
    void testAccessDenied() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ)))
                .thenThrow(new AccessDeniedException("Role VIEWER does not have permission"));

        mockMvc.perform(get("/api/finance/forecast/daily"))
                .andExpect(status().isForbidden());
    }
}

