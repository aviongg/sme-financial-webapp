package com.app.sme_health_backend.dashboard.service;

import com.app.sme_health_backend.cashflow.dto.CashFlowChartPointResponse;
import com.app.sme_health_backend.cashflow.dto.CashFlowProjectionResponse;
import com.app.sme_health_backend.cashflow.service.CashFlowService;
import com.app.sme_health_backend.dashboard.dto.DashboardResponse;
import com.app.sme_health_backend.insight.dto.InsightResponse;
import com.app.sme_health_backend.insight.entity.Insight;
import com.app.sme_health_backend.insight.service.InsightService;
import com.app.sme_health_backend.profile.dto.BusinessProfileResponse;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.service.BusinessProfileService;
import com.app.sme_health_backend.recommendation.dto.RecommendationResponse;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.recommendation.service.RecommendationService;
import com.app.sme_health_backend.scoring.dto.ScoreResultResponse;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.service.ScoringService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DashboardService {

    private final BusinessProfileService businessProfileService;
    private final ScoringService scoringService;
    private final InsightService insightService;
    private final RecommendationService recommendationService;
    private final CashFlowService cashFlowService;

    public DashboardService(
            BusinessProfileService businessProfileService,
            ScoringService scoringService,
            InsightService insightService,
            RecommendationService recommendationService,
            CashFlowService cashFlowService
    ) {
        this.businessProfileService = businessProfileService;
        this.scoringService = scoringService;
        this.insightService = insightService;
        this.recommendationService = recommendationService;
        this.cashFlowService = cashFlowService;
    }

    @Transactional
    public DashboardResponse getDashboard(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        // Profile is mandatory for dashboard; throws ResourceNotFoundException if absent
        BusinessProfile profile = businessProfileService.getProfile(userId);
        BusinessProfileResponse profileResponse = BusinessProfileResponse.fromEntity(profile);

        // Historical cash flow & trend projection from Feature 3 & 9
        List<CashFlowChartPointResponse> cashFlowHistory = cashFlowService.getCashFlowHistory(userId);
        if (cashFlowHistory == null) {
            cashFlowHistory = Collections.emptyList();
        }
        boolean hasHistory = !cashFlowHistory.isEmpty();

        CashFlowProjectionResponse trendProjection = cashFlowService.getTrendProjection(userId);

        // Canonical ScoreResult from Feature 2
        Optional<ScoreResult> latestScoreOpt = scoringService.getLatestScore(userId);

        ScoreResultResponse scoreResponse = null;
        InsightResponse topInsight = null;
        RecommendationResponse topRecommendation = null;

        if (latestScoreOpt.isPresent()) {
            ScoreResult scoreResult = latestScoreOpt.get();
            scoreResponse = ScoreResultResponse.fromEntity(scoreResult);

            // Insights corresponding to the latest ScoreResult
            List<Insight> insights = insightService.getInsights(userId);
            if (insights != null && !insights.isEmpty()) {
                Insight top = insights.stream()
                        .filter(i -> "high".equalsIgnoreCase(i.getPriority()))
                        .findFirst()
                        .orElse(insights.get(0));
                topInsight = InsightResponse.fromEntity(top);
            }

            // Recommendations corresponding to the latest ScoreResult
            List<Recommendation> recommendations = recommendationService.getRecommendations(userId);
            if (recommendations != null && !recommendations.isEmpty()) {
                Recommendation top = recommendations.stream()
                        .filter(r -> "high".equalsIgnoreCase(r.getPriority()))
                        .findFirst()
                        .orElse(recommendations.get(0));
                topRecommendation = RecommendationResponse.fromEntity(top);
            }
        }

        return new DashboardResponse(
                userId,
                profileResponse,
                scoreResponse,
                topInsight,
                topRecommendation,
                cashFlowHistory,
                trendProjection,
                hasHistory
        );
    }
}
