package com.app.sme_health_backend.cashflow.dto;

import java.math.BigDecimal;

public class CashFlowProjectionResponse {

    private String projectedMonth;
    private BigDecimal projectedNetCashFlow;
    private String trendDirection;
    private String confidence;
    private int historicalMonthsCount;
    private String message;

    public CashFlowProjectionResponse() {
    }

    public CashFlowProjectionResponse(
            String projectedMonth,
            BigDecimal projectedNetCashFlow,
            String trendDirection,
            String confidence,
            int historicalMonthsCount,
            String message
    ) {
        this.projectedMonth = projectedMonth;
        this.projectedNetCashFlow = projectedNetCashFlow;
        this.trendDirection = trendDirection;
        this.confidence = confidence;
        this.historicalMonthsCount = historicalMonthsCount;
        this.message = message;
    }

    public static CashFlowProjectionResponse insufficientData(int historicalMonthsCount) {
        return new CashFlowProjectionResponse(
                null,
                null,
                null,
                null,
                historicalMonthsCount,
                "Need at least 3 months of data for a trend"
        );
    }

    public String getProjectedMonth() {
        return projectedMonth;
    }

    public void setProjectedMonth(String projectedMonth) {
        this.projectedMonth = projectedMonth;
    }

    public BigDecimal getProjectedNetCashFlow() {
        return projectedNetCashFlow;
    }

    public void setProjectedNetCashFlow(BigDecimal projectedNetCashFlow) {
        this.projectedNetCashFlow = projectedNetCashFlow;
    }

    public String getTrendDirection() {
        return trendDirection;
    }

    public void setTrendDirection(String trendDirection) {
        this.trendDirection = trendDirection;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public int getHistoricalMonthsCount() {
        return historicalMonthsCount;
    }

    public void setHistoricalMonthsCount(int historicalMonthsCount) {
        this.historicalMonthsCount = historicalMonthsCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
