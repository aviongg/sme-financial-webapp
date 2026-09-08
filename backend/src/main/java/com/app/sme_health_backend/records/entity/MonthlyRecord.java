package com.app.sme_health_backend.records.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "monthly_records")
public class MonthlyRecord {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "month", nullable = false, length = 7)
    private String month;

    @Column(name = "cash_inflow", nullable = false, precision = 14, scale = 2)
    private BigDecimal cashInflow = BigDecimal.ZERO;

    @Column(name = "cash_outflow", nullable = false, precision = 14, scale = 2)
    private BigDecimal cashOutflow = BigDecimal.ZERO;

    @Column(name = "revenue", nullable = false, precision = 14, scale = 2)
    private BigDecimal revenue = BigDecimal.ZERO;

    @Column(name = "cogs", nullable = false, precision = 14, scale = 2)
    private BigDecimal cogs = BigDecimal.ZERO;

    @Column(name = "operating_expenses", nullable = false, precision = 14, scale = 2)
    private BigDecimal operatingExpenses = BigDecimal.ZERO;

    @Column(name = "cash_balance_eom", nullable = false, precision = 14, scale = 2)
    private BigDecimal cashBalanceEom = BigDecimal.ZERO;

    @Column(name = "receivables_outstanding", precision = 14, scale = 2)
    private BigDecimal receivablesOutstanding;

    @Column(name = "payables_outstanding", precision = 14, scale = 2)
    private BigDecimal payablesOutstanding;

    @Column(name = "inventory_value", precision = 14, scale = 2)
    private BigDecimal inventoryValue;

    @Column(name = "loan_outstanding", precision = 14, scale = 2)
    private BigDecimal loanOutstanding;

    @Column(name = "interest_expense", precision = 14, scale = 2)
    private BigDecimal interestExpense;

    @Column(name = "financing_type", nullable = false, length = 15)
    private String financingType = "none";

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public UUID getId() {
        return id;
    }

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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}