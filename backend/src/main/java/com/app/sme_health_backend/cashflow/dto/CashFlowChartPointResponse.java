package com.app.sme_health_backend.cashflow.dto;

import com.app.sme_health_backend.records.entity.MonthlyRecord;

import java.math.BigDecimal;

public class CashFlowChartPointResponse {

    private String month;
    private BigDecimal inflow;
    private BigDecimal outflow;
    private BigDecimal net;
    private BigDecimal runningBalance;

    public CashFlowChartPointResponse() {
    }

    public CashFlowChartPointResponse(
            String month,
            BigDecimal inflow,
            BigDecimal outflow,
            BigDecimal net,
            BigDecimal runningBalance
    ) {
        this.month = month;
        this.inflow = inflow;
        this.outflow = outflow;
        this.net = net;
        this.runningBalance = runningBalance;
    }

    public static CashFlowChartPointResponse fromEntity(MonthlyRecord record) {
        if (record == null) {
            return null;
        }

        BigDecimal inflow = record.getCashInflow();
        BigDecimal outflow = record.getCashOutflow();
        BigDecimal net = null;
        if (inflow != null && outflow != null) {
            net = inflow.subtract(outflow);
        }
        BigDecimal runningBalance = record.getCashBalanceEom();

        return new CashFlowChartPointResponse(
                record.getMonth(),
                inflow,
                outflow,
                net,
                runningBalance
        );
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public BigDecimal getInflow() {
        return inflow;
    }

    public void setInflow(BigDecimal inflow) {
        this.inflow = inflow;
    }

    public BigDecimal getOutflow() {
        return outflow;
    }

    public void setOutflow(BigDecimal outflow) {
        this.outflow = outflow;
    }

    public BigDecimal getNet() {
        return net;
    }

    public void setNet(BigDecimal net) {
        this.net = net;
    }

    public BigDecimal getRunningBalance() {
        return runningBalance;
    }

    public void setRunningBalance(BigDecimal runningBalance) {
        this.runningBalance = runningBalance;
    }
}
