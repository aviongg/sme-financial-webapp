package com.app.sme_health_backend.commission.entity;

import com.app.sme_health_backend.commission.model.CommissionPayoutStatus;
import com.app.sme_health_backend.commission.model.CommissionPayoutType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "commission_payouts",
        indexes = {
                @Index(name = "idx_commission_payouts_earning", columnList = "business_id, earning_id, status"),
                @Index(name = "idx_commission_payouts_salesperson", columnList = "business_id, salesperson_id, status"),
                @Index(name = "idx_commission_payouts_advance", columnList = "business_id, commission_advance_id"),
                @Index(name = "idx_commission_payouts_tx", columnList = "business_id, financial_transaction_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_commission_payout_idempotency", columnNames = {"business_id", "idempotency_key"})
        }
)
public class CommissionPayout {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "salesperson_id", nullable = false)
    private UUID salespersonId;

    @Column(name = "earning_id", nullable = false)
    private UUID earningId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_type", nullable = false, length = 32)
    private CommissionPayoutType payoutType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "PKR";

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "financial_account_id")
    private UUID financialAccountId;

    @Column(name = "financial_transaction_id")
    private UUID financialTransactionId;

    @Column(name = "commission_advance_id")
    private UUID commissionAdvanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CommissionPayoutStatus status = CommissionPayoutStatus.SETTLED;

    @Column(name = "reversal_transaction_id")
    private UUID reversalTransactionId;

    @Column(name = "reversal_reason", length = 255)
    private String reversalReason;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CommissionPayout() {
    }

    public CommissionPayout(UUID businessId, UUID salespersonId, UUID earningId,
                            CommissionPayoutType payoutType, BigDecimal amount, String currency,
                            LocalDate paymentDate, UUID financialAccountId, UUID financialTransactionId,
                            UUID commissionAdvanceId, CommissionPayoutStatus status, String notes,
                            String idempotencyKey, UUID createdByUserId) {
        this.businessId = businessId;
        this.salespersonId = salespersonId;
        this.earningId = earningId;
        this.payoutType = payoutType;
        this.amount = amount;
        this.currency = currency != null ? currency : "PKR";
        this.paymentDate = paymentDate;
        this.financialAccountId = financialAccountId;
        this.financialTransactionId = financialTransactionId;
        this.commissionAdvanceId = commissionAdvanceId;
        this.status = status != null ? status : CommissionPayoutStatus.SETTLED;
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

    public UUID getEarningId() {
        return earningId;
    }

    public void setEarningId(UUID earningId) {
        this.earningId = earningId;
    }

    public CommissionPayoutType getPayoutType() {
        return payoutType;
    }

    public void setPayoutType(CommissionPayoutType payoutType) {
        this.payoutType = payoutType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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

    public UUID getCommissionAdvanceId() {
        return commissionAdvanceId;
    }

    public void setCommissionAdvanceId(UUID commissionAdvanceId) {
        this.commissionAdvanceId = commissionAdvanceId;
    }

    public CommissionPayoutStatus getStatus() {
        return status;
    }

    public void setStatus(CommissionPayoutStatus status) {
        this.status = status;
    }

    public UUID getReversalTransactionId() {
        return reversalTransactionId;
    }

    public void setReversalTransactionId(UUID reversalTransactionId) {
        this.reversalTransactionId = reversalTransactionId;
    }

    public String getReversalReason() {
        return reversalReason;
    }

    public void setReversalReason(String reversalReason) {
        this.reversalReason = reversalReason;
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
