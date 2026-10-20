package com.app.sme_health_backend.obligation;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "obligation_payment_allocations",
        indexes = {
                @Index(name = "idx_allocations_obligation", columnList = "obligation_id, status, effective_date"),
                @Index(name = "idx_allocations_business_tx", columnList = "business_id, financial_transaction_id"),
                @Index(name = "idx_allocations_business_advance", columnList = "business_id, advance_id"),
                @Index(name = "idx_allocations_reversal_tx", columnList = "reversal_transaction_id")
        }
)
public class ObligationPaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "obligation_id", nullable = false)
    private UUID obligationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_type", nullable = false, length = 32)
    private AllocationType allocationType = AllocationType.CASH;

    @Column(name = "financial_transaction_id")
    private UUID financialTransactionId;

    @Column(name = "advance_id")
    private UUID advanceId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AllocationStatus status = AllocationStatus.EFFECTIVE;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "reversal_transaction_id")
    private UUID reversalTransactionId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public ObligationPaymentAllocation() {
    }

    public ObligationPaymentAllocation(
            UUID businessId,
            UUID obligationId,
            UUID financialTransactionId,
            BigDecimal amount,
            LocalDate effectiveDate,
            UUID createdByUserId
    ) {
        this.businessId = businessId;
        this.obligationId = obligationId;
        this.allocationType = AllocationType.CASH;
        this.financialTransactionId = financialTransactionId;
        this.advanceId = null;
        this.amount = amount;
        this.effectiveDate = effectiveDate;
        this.createdByUserId = createdByUserId;
        this.status = AllocationStatus.EFFECTIVE;
        this.createdAt = Instant.now();
    }

    public static ObligationPaymentAllocation fromAdvance(
            UUID businessId,
            UUID obligationId,
            UUID advanceId,
            BigDecimal amount,
            LocalDate effectiveDate,
            UUID createdByUserId
    ) {
        ObligationPaymentAllocation alloc = new ObligationPaymentAllocation();
        alloc.businessId = businessId;
        alloc.obligationId = obligationId;
        alloc.allocationType = AllocationType.ADVANCE;
        alloc.financialTransactionId = null;
        alloc.advanceId = advanceId;
        alloc.amount = amount;
        alloc.effectiveDate = effectiveDate;
        alloc.createdByUserId = createdByUserId;
        alloc.status = AllocationStatus.EFFECTIVE;
        alloc.createdAt = Instant.now();
        return alloc;
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

    public UUID getObligationId() {
        return obligationId;
    }

    public void setObligationId(UUID obligationId) {
        this.obligationId = obligationId;
    }

    public AllocationType getAllocationType() {
        return allocationType;
    }

    public void setAllocationType(AllocationType allocationType) {
        this.allocationType = allocationType;
    }

    public UUID getFinancialTransactionId() {
        return financialTransactionId;
    }

    public void setFinancialTransactionId(UUID financialTransactionId) {
        this.financialTransactionId = financialTransactionId;
    }

    public UUID getAdvanceId() {
        return advanceId;
    }

    public void setAdvanceId(UUID advanceId) {
        this.advanceId = advanceId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public AllocationStatus getStatus() {
        return status;
    }

    public void setStatus(AllocationStatus status) {
        this.status = status;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public UUID getReversalTransactionId() {
        return reversalTransactionId;
    }

    public void setReversalTransactionId(UUID reversalTransactionId) {
        this.reversalTransactionId = reversalTransactionId;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(UUID createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
