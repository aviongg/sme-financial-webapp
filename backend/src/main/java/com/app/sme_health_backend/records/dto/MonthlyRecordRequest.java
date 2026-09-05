package com.app.sme_health_backend.records.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class MonthlyRecordRequest {

    private UUID userId;
    private String month;

    private BigDecimal cashInflow;
    private BigDecimal cashOutflow;
    private BigDecimal revenue;
    private BigDecimal cogs;
    private BigDecimal operatingExpenses;
    private BigDecimal cashBalanceEom;

    private BigDecimal receivablesOutstanding;
    private BigDecimal payablesOutstanding;
    private BigDecimal inventoryValue;
    private BigDecimal loanOutstanding;
    private BigDecimal interestExpense;

    private String financingType;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public BigDecimal getCashInflow() {
        return cashInflow;
    }

    public void setCashInflow(BigDecimal cashInflow) {
        this.cashInflow = cashInflow;
    }

    public BigDecimal getCashOutflow() {
        return cashOutflow;
    }

    public void setCashOutflow(BigDecimal cashOutflow) {
        this.cashOutflow = cashOutflow;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public void setRevenue(BigDecimal revenue) {
        this.revenue = revenue;
    }

    public BigDecimal getCogs() {
        return cogs;
    }

    public void setCogs(BigDecimal cogs) {
        this.cogs = cogs;
    }

    public BigDecimal getOperatingExpenses() {
        return operatingExpenses;
    }

    public void setOperatingExpenses(BigDecimal operatingExpenses) {
        this.operatingExpenses = operatingExpenses;
    }

    public BigDecimal getCashBalanceEom() {
        return cashBalanceEom;
    }

    public void setCashBalanceEom(BigDecimal cashBalanceEom) {
        this.cashBalanceEom = cashBalanceEom;
    }

    public BigDecimal getReceivablesOutstanding() {
        return receivablesOutstanding;
    }

    public void setReceivablesOutstanding(BigDecimal receivablesOutstanding) {
        this.receivablesOutstanding = receivablesOutstanding;
    }

    public BigDecimal getPayablesOutstanding() {
        return payablesOutstanding;
    }

    public void setPayablesOutstanding(BigDecimal payablesOutstanding) {
        this.payablesOutstanding = payablesOutstanding;
    }

    public BigDecimal getInventoryValue() {
        return inventoryValue;
    }

    public void setInventoryValue(BigDecimal inventoryValue) {
        this.inventoryValue = inventoryValue;
    }

    public BigDecimal getLoanOutstanding() {
        return loanOutstanding;
    }

    public void setLoanOutstanding(BigDecimal loanOutstanding) {
        this.loanOutstanding = loanOutstanding;
    }

    public BigDecimal getInterestExpense() {
        return interestExpense;
    }

    public void setInterestExpense(BigDecimal interestExpense) {
        this.interestExpense = interestExpense;
    }

    public String getFinancingType() {
        return financingType;
    }

    public void setFinancingType(String financingType) {
        this.financingType = financingType;
    }
}