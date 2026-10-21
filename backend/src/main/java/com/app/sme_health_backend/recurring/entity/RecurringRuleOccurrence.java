package com.app.sme_health_backend.recurring.entity;

import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.recurring.model.RecurringOccurrenceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recurring_rule_occurrences")
public class RecurringRuleOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "recurring_rule_id", nullable = false)
    private UUID recurringRuleId;

    @Column(name = "rule_version", nullable = false)
    private Long ruleVersion;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private TransactionEventType eventType;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "counterparty_id")
    private UUID counterpartyId;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecurringOccurrenceStatus status = RecurringOccurrenceStatus.SCHEDULED;

    @Column(name = "draft_id")
    private UUID draftId;

    @Column(name = "posted_transaction_id")
    private UUID postedTransactionId;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "skipped_at")
    private Instant skippedAt;

    @Column(name = "skipped_reason", length = 255)
    private String skippedReason;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RecurringRuleOccurrence() {}

    public RecurringRuleOccurrence(
            UUID businessId,
            UUID recurringRuleId,
            Long ruleVersion,
            LocalDate scheduledDate,
            BigDecimal amount,
            TransactionEventType eventType,
            UUID accountId,
            UUID counterpartyId,
            String description,
            RecurringOccurrenceStatus status,
            UUID createdByUserId
    ) {
        this.businessId = businessId;
        this.recurringRuleId = recurringRuleId;
        this.ruleVersion = ruleVersion;
        this.scheduledDate = scheduledDate;
        this.amount = amount;
        this.eventType = eventType;
        this.accountId = accountId;
        this.counterpartyId = counterpartyId;
        this.description = description;
        this.status = status != null ? status : RecurringOccurrenceStatus.SCHEDULED;
        this.createdByUserId = createdByUserId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        if (status == null) {
            status = RecurringOccurrenceStatus.SCHEDULED;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
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

    public UUID getRecurringRuleId() {
        return recurringRuleId;
    }

    public void setRecurringRuleId(UUID recurringRuleId) {
        this.recurringRuleId = recurringRuleId;
    }

    public Long getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(Long ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public TransactionEventType getEventType() {
        return eventType;
    }

    public void setEventType(TransactionEventType eventType) {
        this.eventType = eventType;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public UUID getCounterpartyId() {
        return counterpartyId;
    }

    public void setCounterpartyId(UUID counterpartyId) {
        this.counterpartyId = counterpartyId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public RecurringOccurrenceStatus getStatus() {
        return status;
    }

    public void setStatus(RecurringOccurrenceStatus status) {
        this.status = status;
    }

    public UUID getDraftId() {
        return draftId;
    }

    public void setDraftId(UUID draftId) {
        this.draftId = draftId;
    }

    public UUID getPostedTransactionId() {
        return postedTransactionId;
    }

    public void setPostedTransactionId(UUID postedTransactionId) {
        this.postedTransactionId = postedTransactionId;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }

    public Instant getSkippedAt() {
        return skippedAt;
    }

    public void setSkippedAt(Instant skippedAt) {
        this.skippedAt = skippedAt;
    }

    public String getSkippedReason() {
        return skippedReason;
    }

    public void setSkippedReason(String skippedReason) {
        this.skippedReason = skippedReason;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public void setReversedAt(Instant reversedAt) {
        this.reversedAt = reversedAt;
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

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
