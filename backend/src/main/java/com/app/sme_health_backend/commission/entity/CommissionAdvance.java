package com.app.sme_health_backend.commission.entity;

import com.app.sme_health_backend.commission.model.CommissionAdvanceStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "commission_advances",
        indexes = {
                @Index(name = "idx_commission_advances_sp", columnList = "business_id, salesperson_id, status"),
                @Index(name = "idx_commission_advances_tx", columnList = "business_id, financial_transaction_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_commission_advance_idempotency", columnNames = {"business_id", "idempotency_key"})
        }
)
public class CommissionAdvance {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "salesperson_id", nullable = false)
    private UUID salespersonId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "applied_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal appliedAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "PKR";

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "financial_account_id")
    private UUID financialAccountId;

    @Column(name = "financial_transaction_id")
    private UUID financialTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CommissionAdvanceStatus status = CommissionAdvanceStatus.ACTIVE;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CommissionAdvance() {
    }

    public CommissionAdvance(UUID businessId, UUID salespersonId, BigDecimal amount, BigDecimal appliedAmount,
                             String currency, LocalDate paymentDate, UUID financialAccountId,
                             UUID financialTransactionId, CommissionAdvanceStatus status, String notes,
                             String idempotencyKey, UUID createdByUserId) {
        this.businessId = businessId;
        this.salespersonId = salespersonId;
        this.amount = amount;
        this.appliedAmount = appliedAmount != null ? appliedAmount : BigDecimal.ZERO;
        this.currency = currency != null ? currency : "PKR";
        this.paymentDate = paymentDate;
        this.financialAccountId = financialAccountId;
        this.financialTransactionId = financialTransactionId;
        this.status = status != null ? status : CommissionAdvanceStatus.ACTIVE;
        this.notes = notes;
        this.idempotencyKey = idempotencyKey;
        this.createdByUserId = createdByUserId;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public BigDecimal getUnappliedAmount() {
        if (amount == null) return BigDecimal.ZERO;
        BigDecimal applied = appliedAmount != null ? appliedAmount : BigDecimal.ZERO;
        BigDecimal diff = amount.subtract(applied);
        return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public void setBusinessId(UUID businessId) {
        this.businessId = businessId;
    }

    public UUID getSalespersonId() {
        return salespersonId;
    }

    public void setSalespersonId(UUID salespersonId) {
        this.salespersonId = salespersonId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getAppliedAmount() {
        return appliedAmount;
    }

    public void setAppliedAmount(BigDecimal appliedAmount) {
        this.appliedAmount = appliedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public UUID getFinancialAccountId() {
        return financialAccountId;
    }

    public void setFinancialAccountId(UUID financialAccountId) {
        this.financialAccountId = financialAccountId;
    }

    public UUID getFinancialTransactionId() {
        return financialTransactionId;
    }

    public void setFinancialTransactionId(UUID financialTransactionId) {
        this.financialTransactionId = financialTransactionId;
    }

    public CommissionAdvanceStatus getStatus() {
        return status;
    }

    public void setStatus(CommissionAdvanceStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(UUID createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
