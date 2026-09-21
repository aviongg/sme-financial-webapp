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
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import com.app.sme_health_backend.shared.advice.AdviceContext;
import com.app.sme_health_backend.shared.advice.AdviceContextService;
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
        when(adviceContextService.latest(userId))
                .thenThrow(new ResourceNotFoundException("Business profile not found for this user"));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> dashboardService.getDashboard(userId)
        );
        assertEquals("Business profile not found for this user", exception.getMessage());
        verify(adviceContextService).latest(userId);
        verifyNoInteractions(businessProfileService, scoringService, cashFlowService, insightService, recommendationService);
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
                "2026-09",
                new BigDecimal("55000.00"),
                "UP",
                "HIGH",
                2,
                null
        );

        when(businessProfileService.getProfile(userId)).thenReturn(profile);
        when(cashFlowService.getCashFlowHistory(userId)).thenReturn(chartPoints);
        when(cashFlowService.getTrendProjection(userId)).thenReturn(projection);
        stubSelectedScore(score);
        when(insightService.getInsights(userId, "2026-08")).thenReturn(insights);
        when(recommendationService.getRecommendations(userId, "2026-08")).thenReturn(recs);

        DashboardResponse response = dashboardService.getDashboard(userId);

        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertTrue(response.isHasHistory());

        // Profile assertions
        assertNotNull(response.getProfile());
        assertEquals("trade", response.getProfile().getBusinessType());

        // Score assertions
        assertNotNull(response.getScore());
        assertEquals(new BigDecimal("78.50"), response.getScore().getCompositeScore());
        assertEquals("Stable", response.getScore().getBand());
        assertEquals("repayment", response.getScore().getWeakestComponent());

        // Top Insight assertions
        assertNotNull(response.getTopInsight());
        assertEquals("repayment", response.getTopInsight().getCategory());
        assertEquals("high", response.getTopInsight().getPriority());
        assertEquals("2026-08", response.getTopInsight().getMonth());

        // Top Recommendation assertions
        assertNotNull(response.getTopRecommendation());
        assertEquals("repayment", response.getTopRecommendation().getCategory());
        assertEquals("high", response.getTopRecommendation().getPriority());
        assertEquals("2026-08", response.getTopRecommendation().getMonth());

        // Cash flow & trend assertions
        assertEquals(2, response.getCashFlowHistory().size());
        assertEquals("2026-07", response.getCashFlowHistory().get(0).getMonth());
        assertNotNull(response.getTrendProjection());
        assertEquals("2026-09", response.getTrendProjection().getProjectedMonth());
        assertEquals("UP", response.getTrendProjection().getTrendDirection());

        // Lock before any profile/score read; all advice is pinned to the locked month.
        var ordered = inOrder(adviceContextService, businessProfileService, cashFlowService,
                scoringService, insightService, recommendationService);
        ordered.verify(adviceContextService).latest(userId);
        ordered.verify(businessProfileService).getProfile(userId);
        ordered.verify(cashFlowService).getCashFlowHistory(userId);
        ordered.verify(cashFlowService).getTrendProjection(userId);
        ordered.verify(scoringService).getScore(userId, "2026-08");
        ordered.verify(insightService).getInsights(userId, "2026-08");
        ordered.verify(recommendationService).getRecommendations(userId, "2026-08");
        verify(scoringService, never()).getLatestScore(userId);
        verify(insightService, never()).getInsights(userId);
        verify(recommendationService, never()).getRecommendations(userId);
    }

    @Test
    void shouldHandleNewUserWithNoFinancialHistory() {
        BusinessProfile profile = createProfile(userId);

        when(businessProfileService.getProfile(userId)).thenReturn(profile);
        when(cashFlowService.getCashFlowHistory(userId)).thenReturn(Collections.emptyList());
        when(cashFlowService.getTrendProjection(userId)).thenReturn(CashFlowProjectionResponse.insufficientData(0));
        when(adviceContextService.latest(userId)).thenReturn(Optional.empty());

        DashboardResponse response = dashboardService.getDashboard(userId);

        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertFalse(response.isHasHistory());
        assertNotNull(response.getProfile());
        assertNull(response.getScore());
        assertNull(response.getTopInsight());
        assertNull(response.getTopRecommendation());
        assertTrue(response.getCashFlowHistory().isEmpty());
        assertNotNull(response.getTrendProjection());
        assertEquals("Need at least 3 months of data for a trend", response.getTrendProjection().getMessage());
        assertEquals(0, response.getTrendProjection().getHistoricalMonthsCount());

        // Crucial: No mock insights or recommendations should be triggered when score is absent
        verifyNoInteractions(scoringService, insightService, recommendationService);
    }

    @Test
    void shouldHandleUserWithHistoricalRecordsButNoScore() {
        BusinessProfile profile = createProfile(userId);
        List<CashFlowChartPointResponse> chartPoints = List.of(
                new CashFlowChartPointResponse("2026-08", new BigDecimal("100000"), new BigDecimal("80000"), new BigDecimal("20000"), new BigDecimal("50000"))
        );

        when(businessProfileService.getProfile(userId)).thenReturn(profile);
        when(cashFlowService.getCashFlowHistory(userId)).thenReturn(chartPoints);
        when(cashFlowService.getTrendProjection(userId)).thenReturn(CashFlowProjectionResponse.insufficientData(1));
        when(adviceContextService.latest(userId)).thenReturn(Optional.empty());

        DashboardResponse response = dashboardService.getDashboard(userId);

        assertNotNull(response);
        assertTrue(response.isHasHistory()); // Has history despite no score!
        assertNull(response.getScore());
        assertNull(response.getTopInsight());
        assertNull(response.getTopRecommendation());
        assertEquals(1, response.getCashFlowHistory().size());

        verifyNoInteractions(scoringService, insightService, recommendationService);
    }

    @Test
    void shouldGenerateAndReturnTopInsightAndRecommendationWhenInitiallyAbsent() {
        BusinessProfile profile = createProfile(userId);
        ScoreResult score = createScoreResult(userId, "2026-08", new BigDecimal("65.00"), "Stable", "cashflow");

        Insight freshlyGeneratedInsight = createInsight(userId, "2026-08", "cashflow", "high", "Focus first on cashflow.");
        Recommendation freshlyGeneratedRec = createRecommendation(userId, "2026-08", "cashflow", "high", "Prioritize cashflow.");

        when(businessProfileService.getProfile(userId)).thenReturn(profile);
        when(cashFlowService.getCashFlowHistory(userId)).thenReturn(Collections.emptyList());
        when(cashFlowService.getTrendProjection(userId)).thenReturn(CashFlowProjectionResponse.insufficientData(0));
        stubSelectedScore(score);
        when(insightService.getInsights(userId, "2026-08")).thenReturn(List.of(freshlyGeneratedInsight));
        when(recommendationService.getRecommendations(userId, "2026-08")).thenReturn(List.of(freshlyGeneratedRec));

        DashboardResponse response = dashboardService.getDashboard(userId);

        assertNotNull(response.getScore());
        assertNotNull(response.getTopInsight());
        assertEquals("cashflow", response.getTopInsight().getCategory());
        assertNotNull(response.getTopRecommendation());
        assertEquals("cashflow", response.getTopRecommendation().getCategory());

        verify(insightService).getInsights(userId, "2026-08");
        verify(recommendationService).getRecommendations(userId, "2026-08");
    }

    @Test
    void shouldPreserveFeature9InsufficientTrendDataBehavior() {
        BusinessProfile profile = createProfile(userId);
        CashFlowProjectionResponse insufficient = CashFlowProjectionResponse.insufficientData(2);

        when(businessProfileService.getProfile(userId)).thenReturn(profile);
        when(cashFlowService.getCashFlowHistory(userId)).thenReturn(List.of(
                new CashFlowChartPointResponse("2026-07", new BigDecimal("100000"), new BigDecimal("80000"), new BigDecimal("20000"), new BigDecimal("50000")),
                new CashFlowChartPointResponse("2026-08", new BigDecimal("120000"), new BigDecimal("90000"), new BigDecimal("30000"), new BigDecimal("80000"))
        ));
        when(cashFlowService.getTrendProjection(userId)).thenReturn(insufficient);
        when(adviceContextService.latest(userId)).thenReturn(Optional.empty());

        DashboardResponse response = dashboardService.getDashboard(userId);

        assertNotNull(response.getTrendProjection());
        assertNull(response.getTrendProjection().getProjectedNetCashFlow());
        assertNull(response.getTrendProjection().getTrendDirection());
        assertEquals("Need at least 3 months of data for a trend", response.getTrendProjection().getMessage());
        assertEquals(2, response.getTrendProjection().getHistoricalMonthsCount());
    }

    @Test
    void shouldAlwaysUseLatestScoreResult() {
        BusinessProfile profile = createProfile(userId);
        ScoreResult latestScore = createScoreResult(userId, "2026-09", new BigDecimal("85.00"), "Strong", "compliance");

        when(businessProfileService.getProfile(userId)).thenReturn(profile);
        when(cashFlowService.getCashFlowHistory(userId)).thenReturn(Collections.emptyList());
        when(cashFlowService.getTrendProjection(userId)).thenReturn(CashFlowProjectionResponse.insufficientData(0));
        stubSelectedScore(latestScore);
        when(insightService.getInsights(userId, "2026-09")).thenReturn(Collections.emptyList());
        when(recommendationService.getRecommendations(userId, "2026-09")).thenReturn(Collections.emptyList());

        DashboardResponse response = dashboardService.getDashboard(userId);

        assertEquals("2026-09", response.getScore().getMonth());
        assertEquals("Strong", response.getScore().getBand());
        verify(scoringService).getScore(userId, "2026-09");
        verify(scoringService, never()).getLatestScore(userId);
    }

    @Test
    void shouldEnsureTopInsightAndRecommendationCorrespondToLatestScore() {
        BusinessProfile profile = createProfile(userId);
        ScoreResult latestScore = createScoreResult(userId, "2026-09", new BigDecimal("82.00"), "Strong", "trend");

        Insight insight = createInsight(userId, "2026-09", "trend", "high", "Monitor recent trend.");
        Recommendation rec = createRecommendation(userId, "2026-09", "trend", "high", "Act early on trend changes.");

        when(businessProfileService.getProfile(userId)).thenReturn(profile);
        when(cashFlowService.getCashFlowHistory(userId)).thenReturn(Collections.emptyList());
        when(cashFlowService.getTrendProjection(userId)).thenReturn(CashFlowProjectionResponse.insufficientData(0));
        stubSelectedScore(latestScore);
        when(insightService.getInsights(userId, "2026-09")).thenReturn(List.of(insight));
        when(recommendationService.getRecommendations(userId, "2026-09")).thenReturn(List.of(rec));

        DashboardResponse response = dashboardService.getDashboard(userId);

        assertEquals("2026-09", response.getTopInsight().getMonth());
        assertEquals("trend", response.getTopInsight().getCategory());
        assertEquals("2026-09", response.getTopRecommendation().getMonth());
        assertEquals("trend", response.getTopRecommendation().getCategory());
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
        stubSelectedScore(score);
        when(insightService.getInsights(userId, "2026-08")).thenReturn(List.of(lowInsight, highInsight));
        when(recommendationService.getRecommendations(userId, "2026-08")).thenReturn(List.of(lowRec, highRec));

        DashboardResponse response = dashboardService.getDashboard(userId);

        assertEquals("profitability", response.getTopInsight().getCategory());
        assertEquals("high", response.getTopInsight().getPriority());
        assertEquals("profitability", response.getTopRecommendation().getCategory());
        assertEquals("high", response.getTopRecommendation().getPriority());
    }

    private void stubSelectedScore(ScoreResult score) {
        var adviceScore = new com.app.sme_health_backend.score.dto.ScoreResult(
                score.getUserId(), score.getMonth(), score.getCompositeScore(), score.getBand(),
                score.getComponentScores().toMap(), score.getWeakestComponent(),
                score.getDataCompleteness(), score.getComputedAt());
        when(adviceContextService.latest(userId)).thenReturn(
                Optional.of(new AdviceContext(adviceScore, null, "en", "a".repeat(64))));
        when(scoringService.getScore(userId, score.getMonth())).thenReturn(Optional.of(score));
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
