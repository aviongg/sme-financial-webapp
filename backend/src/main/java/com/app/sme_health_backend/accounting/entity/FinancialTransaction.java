package com.app.sme_health_backend.accounting.entity;

import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "financial_transactions")
public class FinancialTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "destination_account_id", updatable = false)
    private UUID destinationAccountId;

    @Column(name = "reversed_transaction_id", updatable = false)
    private UUID reversedTransactionId;

    @Column(name = "reversal_reason", length = 255, updatable = false)
    private String reversalReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64, updatable = false)
    private TransactionEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_direction", nullable = false, length = 16, updatable = false)
    private MovementDirection movementDirection;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency = "PKR";

    @Column(name = "base_amount_pkr", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal baseAmountPkr;

    @Column(name = "effective_date", nullable = false, updatable = false)
    private LocalDate effectiveDate;

    @Column(name = "description", length = 255, updatable = false)
    private String description;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "counterparty_id")
    private UUID counterpartyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public FinancialTransaction() {}

    public FinancialTransaction(
            UUID businessId,
            UUID accountId,
            TransactionEventType eventType,
            MovementDirection movementDirection,
            BigDecimal amount,
            String currency,
            BigDecimal baseAmountPkr,
            LocalDate effectiveDate,
            String description,
            UUID createdByUserId
    ) {
        this.businessId = businessId;
        this.accountId = accountId;
        this.eventType = eventType;
        this.movementDirection = movementDirection;
        this.amount = amount;
        this.currency = currency;
        this.baseAmountPkr = baseAmountPkr;
        this.effectiveDate = effectiveDate;
        this.description = description;
        this.createdByUserId = createdByUserId;
        this.createdAt = Instant.now();
    }

    public FinancialTransaction(
            UUID businessId,
            UUID accountId,
            UUID destinationAccountId,
            TransactionEventType eventType,
            MovementDirection movementDirection,
            BigDecimal amount,
            String currency,
            BigDecimal baseAmountPkr,
            LocalDate effectiveDate,
            String description,
            UUID createdByUserId
    ) {
        this(businessId, accountId, eventType, movementDirection, amount, currency, baseAmountPkr, effectiveDate, description, createdByUserId);
        this.destinationAccountId = destinationAccountId;
    }

    public FinancialTransaction(
            UUID businessId,
            UUID accountId,
            TransactionEventType eventType,
            MovementDirection movementDirection,
            BigDecimal amount,
            String currency,
            BigDecimal baseAmountPkr,
            LocalDate effectiveDate,
            String description,
            UUID createdByUserId,
            UUID counterpartyId
    ) {
        this(businessId, accountId, eventType, movementDirection, amount, currency, baseAmountPkr, effectiveDate, description, createdByUserId);
        this.counterpartyId = counterpartyId;
    }

    public UUID getCounterpartyId() {
        return counterpartyId;
    }

    public void setCounterpartyId(UUID counterpartyId) {
        this.counterpartyId = counterpartyId;
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

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public void setDestinationAccountId(UUID destinationAccountId) {
        this.destinationAccountId = destinationAccountId;
    }

    public TransactionEventType getEventType() {
        return eventType;
    }

    public void setEventType(TransactionEventType eventType) {
        this.eventType = eventType;
    }

    public MovementDirection getMovementDirection() {
        return movementDirection;
    }

    public void setMovementDirection(MovementDirection movementDirection) {
        this.movementDirection = movementDirection;
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

    public BigDecimal getBaseAmountPkr() {
        return baseAmountPkr;
    }

    public void setBaseAmountPkr(BigDecimal baseAmountPkr) {
        this.baseAmountPkr = baseAmountPkr;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public UUID getReversedTransactionId() {
        return reversedTransactionId;
    }

    public void setReversedTransactionId(UUID reversedTransactionId) {
        this.reversedTransactionId = reversedTransactionId;
    }

    public String getReversalReason() {
        return reversalReason;
    }

    public void setReversalReason(String reversalReason) {
        this.reversalReason = reversalReason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FinancialTransaction that = (FinancialTransaction) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
