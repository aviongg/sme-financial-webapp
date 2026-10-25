package com.app.sme_health_backend.records.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Pattern;

public class MonthlyRecordRequest {

    private String month;

    /** Null asserts that this month was absent when the form was loaded. Never means overwrite. */
    @jakarta.validation.constraints.PositiveOrZero
    private Long expectedVersion;
    public Long getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Long expectedVersion) { this.expectedVersion = expectedVersion; }

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @jakarta.validation.constraints.DecimalMin("0")
    private BigDecimal cashInflow;
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @jakarta.validation.constraints.DecimalMin("0")
    private BigDecimal cashOutflow;
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @jakarta.validation.constraints.DecimalMin("0")
    private BigDecimal revenue;
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @jakarta.validation.constraints.DecimalMin("0")
    private BigDecimal cogs;
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @jakarta.validation.constraints.DecimalMin("0")
    private BigDecimal operatingExpenses;
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @jakarta.validation.constraints.DecimalMin("0")
    private BigDecimal cashBalanceEom;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @jakarta.validation.constraints.DecimalMin("0")
    private BigDecimal receivablesOutstanding;
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @jakarta.validation.constraints.DecimalMin("0")
    private BigDecimal payablesOutstanding;
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @jakarta.validation.constraints.DecimalMin("0")
    private BigDecimal inventoryValue;
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @jakarta.validation.constraints.DecimalMin("0")
    private BigDecimal loanOutstanding;
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @jakarta.validation.constraints.DecimalMin("0")
    private BigDecimal interestExpense;

    @Pattern(
        regexp = "none|conventional|islamic",
        message = "financingType must be one of: none, conventional, islamic"
    )
    private String financingType = "none";

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