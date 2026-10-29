package com.app.sme_health_backend.risk.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.risk.dto.FinancialRiskInsight;
import com.app.sme_health_backend.risk.dto.RiskActionRecommendation;
import com.app.sme_health_backend.risk.dto.RiskAssessmentResponse;
import com.app.sme_health_backend.risk.dto.RiskSummary;
import com.app.sme_health_backend.risk.exception.RiskValidationException;
import com.app.sme_health_backend.risk.model.RiskCategory;
import com.app.sme_health_backend.risk.model.RiskCompleteness;
import com.app.sme_health_backend.risk.model.RiskSeverity;
import com.app.sme_health_backend.risk.model.Urgency;
import com.app.sme_health_backend.risk.service.FinancialRiskAssessmentService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinancialRiskController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("FinancialRiskController WebMvc Tests (A-W6-FINANCIAL-RISK-INTELLIGENCE-V1)")
class FinancialRiskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinancialRiskAssessmentService riskService;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private BusinessAccessContext context;
    private final UUID businessId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final LocalDate evalDate = LocalDate.of(2026, 11, 1);

    @BeforeEach
    void setUp() {
        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ))).thenReturn(context);
    }

    @Test
    @DisplayName("GET /api/finance/risk/assessment returns 200 with complete assessment")
    void getAssessment_success_returns200() throws Exception {
        RiskSummary summary = new RiskSummary(1, 0, 0, 0, 0, 8, 8, evalDate, 30, "PKR", RiskSeverity.CRITICAL, false);
        FinancialRiskInsight insight = new FinancialRiskInsight(
                "RISK-R01-DEFICIT-PKR-2026-11-10", "R01_PROJECTED_CASH_DEFICIT", RiskCategory.LIQUIDITY_FORECAST,
                RiskSeverity.CRITICAL, "Projected Cash Shortfall", "Summary description", "Why it matters",
                "PKR", new BigDecimal("30000.00"), null, "By 2026-11-10", evalDate, evalDate.plusDays(10),
                Collections.emptyList(),
                new RiskActionRecommendation("EXPEDITE", "Expedite Collections", "Follow up", Urgency.IMMEDIATE, "/records?view=forecast"),
                RiskCompleteness.COMPLETE
        );
        RiskAssessmentResponse response = new RiskAssessmentResponse(
                "1.0", businessId, evalDate, 30, "PKR", summary, Collections.emptyList(), List.of(insight), Instant.now()
        );

        when(riskService.getAssessment(eq(context), any(), any(), any(), any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/finance/risk/assessment")
                        .param("asOfDate", evalDate.toString())
                        .param("horizonDays", "30")
                        .param("currency", "PKR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.0"))
                .andExpect(jsonPath("$.summary.criticalCount").value(1))
                .andExpect(jsonPath("$.summary.highestSeverity").value("CRITICAL"))
                .andExpect(jsonPath("$.insights[0].id").value("RISK-R01-DEFICIT-PKR-2026-11-10"));
    }

    @Test
    @DisplayName("GET /api/finance/risk/insights returns 200 with insights list")
    void getInsights_success_returns200() throws Exception {
        FinancialRiskInsight insight = new FinancialRiskInsight(
                "RISK-R03-RECEIVABLES-OVERDUE", "R03_OVERDUE_RECEIVABLES", RiskCategory.RECEIVABLES_COLLECTION,
                RiskSeverity.HIGH, "Overdue Receivables", "60k overdue", "Cash flow impact",
                "PKR", new BigDecimal("60000.00"), new BigDecimal("60.00"), "As of 2026-11-01", evalDate, evalDate,
                Collections.emptyList(),
                new RiskActionRecommendation("COLLECT", "Follow up", "Prioritize", Urgency.IMMEDIATE, "/records?view=receivables"),
                RiskCompleteness.COMPLETE
        );

        when(riskService.getInsights(eq(context), any(), any(), any(), any(), any())).thenReturn(List.of(insight));

        mockMvc.perform(get("/api/finance/risk/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].riskCode").value("R03_OVERDUE_RECEIVABLES"))
                .andExpect(jsonPath("$[0].severity").value("HIGH"));
    }

    @Test
    @DisplayName("GET /api/finance/risk/summary returns 200 with summary object")
    void getSummary_success_returns200() throws Exception {
        RiskSummary summary = new RiskSummary(0, 1, 2, 0, 1, 7, 8, evalDate, 30, "PKR", RiskSeverity.HIGH, true);
        when(riskService.getSummary(eq(context), any(), any(), any())).thenReturn(summary);

        mockMvc.perform(get("/api/finance/risk/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highCount").value(1))
                .andExpect(jsonPath("$.mediumCount").value(2))
                .andExpect(jsonPath("$.hasDataCompletenessIssues").value(true));
    }

    @Test
    @DisplayName("GET /api/finance/risk/assessment unauthorized returns 403")
    void getAssessment_unauthorized_returns403() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ)))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/api/finance/risk/assessment"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/finance/risk/assessment validation failure returns 400")
    void getAssessment_validationFailure_returns400() throws Exception {
        when(riskService.getAssessment(eq(context), any(), any(), any(), any(), any()))
                .thenThrow(new RiskValidationException("horizonDays", "Horizon days must be between 1 and 365"));

        mockMvc.perform(get("/api/finance/risk/assessment")
                        .param("horizonDays", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }
}
