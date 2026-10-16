package com.app.sme_health_backend.accounting.draft;

import com.app.sme_health_backend.accounting.model.TransactionEventType;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entity representing an incomplete, persistent financial entry draft.
 */
@Entity
@Table(name = "record_drafts")
public class RecordDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "account_id")
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50)
    private TransactionEventType eventType;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "amount_input", length = 100)
    private String amountInput;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DraftStatus status = DraftStatus.DRAFT;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "recorded_transaction_id")
    private UUID recordedTransactionId;

    @Column(name = "created_by_id", nullable = false)
    private UUID createdById;

    @Column(name = "updated_by_id", nullable = false)
    private UUID updatedById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RecordDraft() {
    }

    public RecordDraft(
            UUID businessId,
            UUID accountId,
            TransactionEventType eventType,
            LocalDate effectiveDate,
            String amountInput,
            String description,
            UUID createdById
    ) {
        this.businessId = businessId;
        this.accountId = accountId;
        this.eventType = eventType;
        this.effectiveDate = effectiveDate;
        this.amountInput = amountInput;
        this.description = description;
        this.status = DraftStatus.DRAFT;
        this.version = 0L;
        this.createdById = createdById;
        this.updatedById = createdById;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        if (status == null) {
            status = DraftStatus.DRAFT;
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters

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

    public TransactionEventType getEventType() {
        return eventType;
    }

    public void setEventType(TransactionEventType eventType) {
        this.eventType = eventType;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public String getAmountInput() {
        return amountInput;
    }

    public void setAmountInput(String amountInput) {
        this.amountInput = amountInput;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DraftStatus getStatus() {
        return status;
    }

    public void setStatus(DraftStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public UUID getRecordedTransactionId() {
        return recordedTransactionId;
    }

    public void setRecordedTransactionId(UUID recordedTransactionId) {
        this.recordedTransactionId = recordedTransactionId;
    }

    public UUID getCreatedById() {
        return createdById;
    }

    public void setCreatedById(UUID createdById) {
        this.createdById = createdById;
    }

    public UUID getUpdatedById() {
        return updatedById;
    }

    public void setUpdatedById(UUID updatedById) {
        this.updatedById = updatedById;
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
