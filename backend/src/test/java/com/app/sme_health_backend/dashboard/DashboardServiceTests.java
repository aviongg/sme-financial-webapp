package com.app.sme_health_backend.dashboard;

import com.app.sme_health_backend.cashflow.dto.CashFlowChartPointResponse;
import com.app.sme_health_backend.cashflow.dto.CashFlowProjectionResponse;
import com.app.sme_health_backend.cashflow.service.CashFlowService;
import com.app.sme_health_backend.dashboard.dto.DashboardResponse;
import com.app.sme_health_backend.dashboard.service.DashboardService;
import com.app.sme_health_backend.insight.entity.Insight;
import com.app.sme_health_backend.insight.service.InsightService;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.service.BusinessProfileService;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.recommendation.service.RecommendationService;
import com.app.sme_health_backend.scoring.dto.ComponentScoresDto;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.service.ScoringService;
import com.app.sme_health_backend.shared.advice.AdviceContext;
import com.app.sme_health_backend.shared.advice.AdviceContextService;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTests {

    @Mock
    private BusinessProfileService businessProfileService;

    @Mock
    private ScoringService scoringService;

    @Mock
    private InsightService insightService;

    @Mock
    private RecommendationService recommendationService;

    @Mock
    private CashFlowService cashFlowService;

    @Mock
    private AdviceContextService adviceContextService;

    private DashboardService dashboardService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                businessProfileService,
                scoringService,
                insightService,
                recommendationService,
                cashFlowService,
                adviceContextService
        );
        userId = UUID.randomUUID();
    }

    @Test
    void shouldRejectNullUserId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> dashboardService.getDashboard(null)
        );
        assertEquals("User ID is required", exception.getMessage());
        verifyNoInteractions(adviceContextService, businessProfileService, scoringService,
                cashFlowService, insightService, recommendationService);
    }

    @Test
    void shouldThrowWhenUserProfileNotFound() {
        when(adviceContextService.latest(userId)).thenReturn(Optional.empty());
        when(businessProfileService.getProfile(userId))
                .thenThrow(new ResourceNotFoundException("Business profile not found for this user"));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> dashboardService.getDashboard(userId)
        );
        assertEquals("Business profile not found for this user", exception.getMessage());
        verify(businessProfileService).getProfile(userId);
    }

    @Test
    void shouldReturnFullyPopulatedDashboard() {
        BusinessProfile profile = createProfile(userId);
        ScoreResult score = createScoreResult(userId, "2026-08", new BigDecimal("78.50"), "Stable", "repayment");

        Insight insightHigh = createInsight(userId, "2026-08", "repayment", "high", "Focus first on repayment.");
        Insight insightMed = createInsight(userId, "2026-08", "overall_health", "medium", "Health is stable.");
        List<Insight> insights = List.of(insightHigh, insightMed);

        Recommendation recHigh = createRecommendation(userId, "2026-08", "repayment", "high", "Track debt schedules.");
        Recommendation recMed = createRecommendation(userId, "2026-08", "overall_health", "medium", "Keep monitoring.");
        List<Recommendation> recs = List.of(recHigh, recMed);

        List<CashFlowChartPointResponse> chartPoints = List.of(
                new CashFlowChartPointResponse("2026-07", new BigDecimal("100000"), new BigDecimal("60000"), new BigDecimal("40000"), new BigDecimal("120000")),
                new CashFlowChartPointResponse("2026-08", new BigDecimal("120000"), new BigDecimal("70000"), new BigDecimal("50000"), new BigDecimal("170000"))
        );
        CashFlowProjectionResponse projection = new CashFlowProjectionResponse(
                "2026-09", new BigDecimal("55000"), "upward", "reasonable", 2, "Positive trend"
        );

        when(businessProfileService.getProfile(userId)).thenReturn(profile);
        when(cashFlowService.getCashFlowHistory(userId)).thenReturn(chartPoints);
        when(cashFlowService.getTrendProjection(userId)).thenReturn(projection);
        when(adviceContextService.latest(userId)).thenReturn(Optional.of(new AdviceContext(score, null, "en", "fp-123")));
        when(insightService.getInsights(userId, "2026-08")).thenReturn(insights);
        when(recommendationService.getRecommendations(userId, "2026-08")).thenReturn(recs);

        DashboardResponse response = dashboardService.getDashboard(userId);

        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertNotNull(response.getProfile());
        assertNotNull(response.getScore());
        assertEquals("2026-08", response.getScore().getMonth());
        assertEquals(new BigDecimal("78.50"), response.getScore().getCompositeScore());
        assertEquals("Stable", response.getScore().getBand());

        assertNotNull(response.getTopInsight());
        assertEquals("repayment", response.getTopInsight().getCategory());
        assertEquals("high", response.getTopInsight().getPriority());

        assertNotNull(response.getTopRecommendation());
        assertEquals("repayment", response.getTopRecommendation().getCategory());
        assertEquals("high", response.getTopRecommendation().getPriority());

        assertEquals(2, response.getCashFlowHistory().size());
        assertTrue(response.isHasHistory());
        assertNotNull(response.getTrendProjection());

        verify(adviceContextService).latest(userId);
        verify(businessProfileService).getProfile(userId);
        verify(cashFlowService).getCashFlowHistory(userId);
        verify(cashFlowService).getTrendProjection(userId);
        verify(insightService).getInsights(userId, "2026-08");
        verify(recommendationService).getRecommendations(userId, "2026-08");
    }

    @Test
    void shouldHandleDashboardWithNoHistoryOrScore() {
        BusinessProfile profile = createProfile(userId);

        when(businessProfileService.getProfile(userId)).thenReturn(profile);
        when(cashFlowService.getCashFlowHistory(userId)).thenReturn(Collections.emptyList());
        when(cashFlowService.getTrendProjection(userId)).thenReturn(CashFlowProjectionResponse.insufficientData(0));
        when(adviceContextService.latest(userId)).thenReturn(Optional.empty());

        DashboardResponse response = dashboardService.getDashboard(userId);

        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertNotNull(response.getProfile());
        assertNull(response.getScore());
        assertNull(response.getTopInsight());
        assertNull(response.getTopRecommendation());
        assertTrue(response.getCashFlowHistory().isEmpty());
        assertFalse(response.isHasHistory());

        verify(adviceContextService).latest(userId);
        verify(businessProfileService).getProfile(userId);
        verify(cashFlowService).getCashFlowHistory(userId);
        verify(cashFlowService).getTrendProjection(userId);
        verifyNoInteractions(scoringService, insightService, recommendationService);
    }

    @Test
    void shouldAlwaysUseLatestScoreResult() {
        BusinessProfile profile = createProfile(userId);
        ScoreResult latestScore = createScoreResult(userId, "2026-09", new BigDecimal("85.00"), "Strong", "compliance");

        when(businessProfileService.getProfile(userId)).thenReturn(profile);
        when(cashFlowService.getCashFlowHistory(userId)).thenReturn(Collections.emptyList());
        when(cashFlowService.getTrendProjection(userId)).thenReturn(CashFlowProjectionResponse.insufficientData(0));
        when(adviceContextService.latest(userId)).thenReturn(Optional.of(new AdviceContext(latestScore, null, "en", "fp-latest")));
        when(insightService.getInsights(userId, "2026-09")).thenReturn(Collections.emptyList());
        when(recommendationService.getRecommendations(userId, "2026-09")).thenReturn(Collections.emptyList());

        DashboardResponse response = dashboardService.getDashboard(userId);

        assertEquals("2026-09", response.getScore().getMonth());
        assertEquals("Strong", response.getScore().getBand());
        verify(adviceContextService).latest(userId);
    }

    @Test
    void shouldSelectHighPriorityItemOverLowerPriorityEvenIfNotFirst() {
        BusinessProfile profile = createProfile(userId);
        ScoreResult score = createScoreResult(userId, "2026-08", new BigDecimal("70.00"), "Stable", "profitability");

        Insight lowInsight = createInsight(userId, "2026-08", "data_quality", "low", "Data is okay.");
        Insight highInsight = createInsight(userId, "2026-08", "profitability", "high", "Improve margins.");

        Recommendation lowRec = createRecommendation(userId, "2026-08", "data_quality", "low", "Keep records.");
        Recommendation highRec = createRecommendation(userId, "2026-08", "profitability", "high", "Review pricing.");

        when(businessProfileService.getProfile(userId)).thenReturn(profile);
        when(cashFlowService.getCashFlowHistory(userId)).thenReturn(Collections.emptyList());
        when(cashFlowService.getTrendProjection(userId)).thenReturn(CashFlowProjectionResponse.insufficientData(0));
        when(adviceContextService.latest(userId)).thenReturn(Optional.of(new AdviceContext(score, null, "en", "fp-priorities")));
        when(insightService.getInsights(userId, "2026-08")).thenReturn(List.of(lowInsight, highInsight));
        when(recommendationService.getRecommendations(userId, "2026-08")).thenReturn(List.of(lowRec, highRec));

        DashboardResponse response = dashboardService.getDashboard(userId);

        assertEquals("profitability", response.getTopInsight().getCategory());
        assertEquals("high", response.getTopInsight().getPriority());
        assertEquals("profitability", response.getTopRecommendation().getCategory());
        assertEquals("high", response.getTopRecommendation().getPriority());
    }

    private BusinessProfile createProfile(UUID userId) {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);
        profile.setBusinessType("trade");
        profile.setLanguagePreference("en");
        profile.setCreatedAt(LocalDateTime.now());
        return profile;
    }

    private ScoreResult createScoreResult(UUID userId, String month, BigDecimal compositeScore, String band, String weakestComponent) {
        ScoreResult result = new ScoreResult();
        result.setId(UUID.randomUUID());
        result.setUserId(userId);
        result.setMonth(month);
        result.setCompositeScore(compositeScore);
        result.setBand(band);
        result.setComponentScores(new ComponentScoresDto(
                new BigDecimal("80.00"),
                new BigDecimal("70.00"),
                new BigDecimal("60.00"),
                new BigDecimal("75.00"),
                new BigDecimal("85.00")
        ));
        result.setWeakestComponent(weakestComponent);
        result.setDataCompleteness(new BigDecimal("1.00"));
        result.setComputedAt(LocalDateTime.now());
        return result;
    }

    private Insight createInsight(UUID userId, String month, String category, String priority, String text) {
        Insight insight = new Insight();
        insight.setUserId(userId);
        insight.setMonth(month);
        insight.setCategory(category);
        insight.setPriority(priority);
        insight.setText(text);
        insight.setCreatedAt(LocalDateTime.now());
        return insight;
    }

    private Recommendation createRecommendation(UUID userId, String month, String category, String priority, String text) {
        Recommendation rec = new Recommendation();
        rec.setUserId(userId);
        rec.setMonth(month);
        rec.setCategory(category);
        rec.setPriority(priority);
        rec.setText(text);
        rec.setCreatedAt(LocalDateTime.now());
        return rec;
    }
}
