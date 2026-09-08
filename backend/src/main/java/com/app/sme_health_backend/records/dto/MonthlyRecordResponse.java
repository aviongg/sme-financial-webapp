package com.app.sme_health_backend.records.dto;

import com.app.sme_health_backend.records.entity.MonthlyRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class MonthlyRecordResponse {

    private UUID id;
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
    private LocalDateTime updatedAt;

    public static MonthlyRecordResponse fromEntity(MonthlyRecord record) {
        MonthlyRecordResponse response = new MonthlyRecordResponse();

        response.id = record.getId();
        response.userId = record.getUserId();
        response.month = record.getMonth();

        response.cashInflow = record.getCashInflow();
        response.cashOutflow = record.getCashOutflow();
        response.revenue = record.getRevenue();
        response.cogs = record.getCogs();
        response.operatingExpenses = record.getOperatingExpenses();
        response.cashBalanceEom = record.getCashBalanceEom();

        response.receivablesOutstanding = record.getReceivablesOutstanding();
        response.payablesOutstanding = record.getPayablesOutstanding();
        response.inventoryValue = record.getInventoryValue();
        response.loanOutstanding = record.getLoanOutstanding();
        response.interestExpense = record.getInterestExpense();

        response.financingType = record.getFinancingType();
        response.updatedAt = record.getUpdatedAt();

        return response;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getMonth() {
        return month;
    }

    public BigDecimal getCashInflow() {
        return cashInflow;
    }

    public BigDecimal getCashOutflow() {
        return cashOutflow;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public BigDecimal getCogs() {
        return cogs;
    }

    public BigDecimal getOperatingExpenses() {
        return operatingExpenses;
    }

    public BigDecimal getCashBalanceEom() {
        return cashBalanceEom;
    }

    public BigDecimal getReceivablesOutstanding() {
        return receivablesOutstanding;
    }

    public BigDecimal getPayablesOutstanding() {
        return payablesOutstanding;
    }

    public BigDecimal getInventoryValue() {
        return inventoryValue;
    }

    public BigDecimal getLoanOutstanding() {
        return loanOutstanding;
    }

    public BigDecimal getInterestExpense() {
        return interestExpense;
    }

    public String getFinancingType() {
        return financingType;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}