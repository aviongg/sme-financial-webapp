package com.app.sme_health_backend.dashboard.dto;

import com.app.sme_health_backend.cashflow.dto.CashFlowChartPointResponse;
import com.app.sme_health_backend.cashflow.dto.CashFlowProjectionResponse;
import com.app.sme_health_backend.insight.dto.InsightResponse;
import com.app.sme_health_backend.profile.dto.BusinessProfileResponse;
import com.app.sme_health_backend.recommendation.dto.RecommendationResponse;
import com.app.sme_health_backend.scoring.dto.ScoreResultResponse;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class DashboardResponse {

    private UUID businessId;
    private BusinessProfileResponse profile;
    private ScoreResultResponse score;
    private InsightResponse topInsight;
    private RecommendationResponse topRecommendation;
    private List<CashFlowChartPointResponse> cashFlowHistory;
    private CashFlowProjectionResponse trendProjection;

    @JsonProperty("hasHistory")
    private boolean hasHistory;

    public DashboardResponse() {
        this.cashFlowHistory = Collections.emptyList();
    }

    public DashboardResponse(
            UUID businessId,
            BusinessProfileResponse profile,
            ScoreResultResponse score,
            InsightResponse topInsight,
            RecommendationResponse topRecommendation,
            List<CashFlowChartPointResponse> cashFlowHistory,
            CashFlowProjectionResponse trendProjection,
            boolean hasHistory
    ) {
        this.businessId = businessId;
        this.profile = profile;
        this.score = score;
        this.topInsight = topInsight;
        this.topRecommendation = topRecommendation;
        this.cashFlowHistory = cashFlowHistory != null ? cashFlowHistory : Collections.emptyList();
        this.trendProjection = trendProjection;
        this.hasHistory = hasHistory;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public void setBusinessId(UUID businessId) {
        this.businessId = businessId;
    }

    public BusinessProfileResponse getProfile() {
        return profile;
    }

    public void setProfile(BusinessProfileResponse profile) {
        this.profile = profile;
    }

    public ScoreResultResponse getScore() {
        return score;
    }

    public void setScore(ScoreResultResponse score) {
        this.score = score;
    }

    public InsightResponse getTopInsight() {
        return topInsight;
    }

    public void setTopInsight(InsightResponse topInsight) {
        this.topInsight = topInsight;
    }

    public RecommendationResponse getTopRecommendation() {
        return topRecommendation;
    }

    public void setTopRecommendation(RecommendationResponse topRecommendation) {
        this.topRecommendation = topRecommendation;
    }

    public List<CashFlowChartPointResponse> getCashFlowHistory() {
        return cashFlowHistory;
    }

    public void setCashFlowHistory(List<CashFlowChartPointResponse> cashFlowHistory) {
        this.cashFlowHistory = cashFlowHistory != null ? cashFlowHistory : Collections.emptyList();
    }

    public CashFlowProjectionResponse getTrendProjection() {
        return trendProjection;
    }

    public void setTrendProjection(CashFlowProjectionResponse trendProjection) {
        this.trendProjection = trendProjection;
    }

    public boolean isHasHistory() {
        return hasHistory;
    }

    public void setHasHistory(boolean hasHistory) {
        this.hasHistory = hasHistory;
    }
}
