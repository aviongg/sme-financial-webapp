package com.app.sme_health_backend.scoring.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class ComponentScoresDto implements Serializable {

    private BigDecimal cashflow;
    private BigDecimal profitability;
    private BigDecimal repayment;
    private BigDecimal trend;
    private BigDecimal compliance;

    public ComponentScoresDto() {
    }

    public ComponentScoresDto(
            BigDecimal cashflow,
            BigDecimal profitability,
            BigDecimal repayment,
            BigDecimal trend,
            BigDecimal compliance
    ) {
        this.cashflow = cashflow;
        this.profitability = profitability;
        this.repayment = repayment;
        this.trend = trend;
        this.compliance = compliance;
    }

    public Map<String, BigDecimal> toMap() {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        map.put("cashflow", cashflow);
        map.put("profitability", profitability);
        map.put("repayment", repayment);
        map.put("trend", trend);
        map.put("compliance", compliance);
        return map;
    }

    public static ComponentScoresDto fromMap(Map<String, BigDecimal> map) {
        if (map == null) {
            return new ComponentScoresDto();
        }
        return new ComponentScoresDto(
                map.get("cashflow"),
                map.get("profitability"),
                map.get("repayment"),
                map.get("trend"),
                map.get("compliance")
        );
    }

    public BigDecimal getCashflow() {
        return cashflow;
    }

    public void setCashflow(BigDecimal cashflow) {
        this.cashflow = cashflow;
    }

    public BigDecimal getProfitability() {
        return profitability;
    }

    public void setProfitability(BigDecimal profitability) {
        this.profitability = profitability;
    }

    public BigDecimal getRepayment() {
        return repayment;
    }

    public void setRepayment(BigDecimal repayment) {
        this.repayment = repayment;
    }

    public BigDecimal getTrend() {
        return trend;
    }

    public void setTrend(BigDecimal trend) {
        this.trend = trend;
    }

    public BigDecimal getCompliance() {
        return compliance;
    }

    public void setCompliance(BigDecimal compliance) {
        this.compliance = compliance;
    }
}
