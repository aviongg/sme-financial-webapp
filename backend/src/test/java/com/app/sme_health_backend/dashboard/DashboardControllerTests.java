package com.app.sme_health_backend.dashboard;

import com.app.sme_health_backend.cashflow.dto.CashFlowChartPointResponse;
import com.app.sme_health_backend.cashflow.dto.CashFlowProjectionResponse;
import com.app.sme_health_backend.dashboard.controller.DashboardController;
import com.app.sme_health_backend.dashboard.dto.DashboardResponse;
import com.app.sme_health_backend.dashboard.service.DashboardService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.insight.dto.InsightResponse;
import com.app.sme_health_backend.insight.entity.Insight;
import com.app.sme_health_backend.profile.dto.BusinessProfileResponse;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.recommendation.dto.RecommendationResponse;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.scoring.dto.ComponentScoresDto;
import com.app.sme_health_backend.scoring.dto.ScoreResultResponse;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@Import(GlobalExceptionHandler.class)
class DashboardControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

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
    void shouldReturn200AndFullyPopulatedDashboard() throws Exception {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(businessId);
        profile.setBusinessType("trade");
        profile.setLanguagePreference("en");
        profile.setCreatedAt(LocalDateTime.now());
        BusinessProfileResponse profileResponse = BusinessProfileResponse.fromEntity(profile);

        ScoreResult score = new ScoreResult();
        score.setId(UUID.randomUUID());
        score.setUserId(businessId);
        score.setMonth("2026-08");
        score.setCompositeScore(new BigDecimal("78.50"));
        score.setBand("Stable");
        score.setComponentScores(new ComponentScoresDto(
                new BigDecimal("80.00"),
                new BigDecimal("70.00"),
                new BigDecimal("60.00"),
                new BigDecimal("75.00"),
                new BigDecimal("85.00")
        ));
        score.setWeakestComponent("repayment");
        score.setDataCompleteness(new BigDecimal("1.00"));
        score.setComputedAt(LocalDateTime.now());
        ScoreResultResponse scoreResponse = ScoreResultResponse.fromEntity(score);

        Insight insight = new Insight();
        insight.setUserId(businessId);
        insight.setMonth("2026-08");
        insight.setCategory("repayment");
        insight.setPriority("high");
        insight.setText("Focus first on repayment.");
        insight.setCreatedAt(LocalDateTime.now());
        InsightResponse topInsight = InsightResponse.fromEntity(insight);

        Recommendation rec = new Recommendation();
        rec.setUserId(businessId);
        rec.setMonth("2026-08");
        rec.setCategory("repayment");
        rec.setPriority("high");
        rec.setText("Track debt schedules.");
        rec.setCreatedAt(LocalDateTime.now());
        RecommendationResponse topRec = RecommendationResponse.fromEntity(rec);

        List<CashFlowChartPointResponse> cashFlowHistory = List.of(
                new CashFlowChartPointResponse("2026-07", new BigDecimal("100000"), new BigDecimal("60000"), new BigDecimal("40000"), new BigDecimal("120000")),
                new CashFlowChartPointResponse("2026-08", new BigDecimal("120000"), new BigDecimal("70000"), new BigDecimal("50000"), new BigDecimal("170000"))
        );

        CashFlowProjectionResponse trendProjection = new CashFlowProjectionResponse(
                "2026-09",
                new BigDecimal("55000.00"),
                "UP",
                "HIGH",
                2,
                null
        );

        DashboardResponse response = new DashboardResponse(
                businessId,
                profileResponse,
                scoreResponse,
                topInsight,
                topRec,
                cashFlowHistory,
                trendProjection,
                true
        );

        when(dashboardService.getDashboard(businessId)).thenReturn(response);

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(businessId.toString()))
                .andExpect(jsonPath("$.hasHistory").value(true))
                .andExpect(jsonPath("$.profile.businessType").value("trade"))
                .andExpect(jsonPath("$.score.compositeScore").value(78.50))
                .andExpect(jsonPath("$.score.band").value("Stable"))
                .andExpect(jsonPath("$.topInsight.category").value("repayment"))
                .andExpect(jsonPath("$.topInsight.priority").value("high"))
                .andExpect(jsonPath("$.topRecommendation.category").value("repayment"))
                .andExpect(jsonPath("$.cashFlowHistory.length()").value(2))
                .andExpect(jsonPath("$.cashFlowHistory[0].month").value("2026-07"))
                .andExpect(jsonPath("$.trendProjection.projectedMonth").value("2026-09"))
                .andExpect(jsonPath("$.trendProjection.trendDirection").value("UP"));
    }

    @Test
    void shouldReturn200ForEmptyStateNewUser() throws Exception {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(businessId);
        profile.setBusinessType("services");
        profile.setCreatedAt(LocalDateTime.now());

        DashboardResponse response = new DashboardResponse(
                businessId,
                BusinessProfileResponse.fromEntity(profile),
                null,
                null,
                null,
                Collections.emptyList(),
                CashFlowProjectionResponse.insufficientData(0),
                false
        );

        when(dashboardService.getDashboard(businessId)).thenReturn(response);

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(businessId.toString()))
                .andExpect(jsonPath("$.hasHistory").value(false))
                .andExpect(jsonPath("$.profile.businessType").value("services"))
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.topInsight").doesNotExist())
                .andExpect(jsonPath("$.topRecommendation").doesNotExist())
                .andExpect(jsonPath("$.cashFlowHistory").isEmpty())
                .andExpect(jsonPath("$.trendProjection.message").value("Need at least 3 months of data for a trend"));
    }

    @Test
    void shouldReturn404WhenProfileNotFound() throws Exception {
        when(dashboardService.getDashboard(businessId))
                .thenThrow(new ResourceNotFoundException("Business profile not found for this user"));

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Business profile not found for this user"));
    }

    @Test
    void shouldRejectLegacyDashboardWithUserIdUrl() throws Exception {
        mockMvc.perform(get("/api/dashboard/{userId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
