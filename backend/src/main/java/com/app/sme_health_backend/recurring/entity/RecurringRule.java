package com.app.sme_health_backend.recurring.entity;

import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.recurring.model.RecurringFrequency;
import com.app.sme_health_backend.recurring.model.RecurringRuleStatus;
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
@Table(name = "recurring_rules")
public class RecurringRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private TransactionEventType eventType;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "counterparty_id")
    private UUID counterpartyId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "PKR";

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private RecurringFrequency frequency;

    @Column(name = "anchor_day_of_month", nullable = false)
    private int anchorDayOfMonth;

    @Column(name = "anchor_month", nullable = false)
    private int anchorMonth;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecurringRuleStatus status = RecurringRuleStatus.ACTIVE;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "updated_by_user_id", nullable = false)
    private UUID updatedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RecurringRule() {}

    public RecurringRule(
            UUID businessId,
            String name,
            String description,
            TransactionEventType eventType,
            UUID accountId,
            UUID counterpartyId,
            BigDecimal amount,
            RecurringFrequency frequency,
            int anchorDayOfMonth,
            int anchorMonth,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate nextDueDate,
            UUID createdByUserId
    ) {
        this.businessId = businessId;
        this.name = name;
        this.description = description;
        this.eventType = eventType;
        this.accountId = accountId;
        this.counterpartyId = counterpartyId;
        this.amount = amount;
        this.currency = "PKR";
        this.frequency = frequency;
        this.anchorDayOfMonth = anchorDayOfMonth;
        this.anchorMonth = anchorMonth;
        this.startDate = startDate;
        this.endDate = endDate;
        this.nextDueDate = nextDueDate;
        this.status = RecurringRuleStatus.ACTIVE;
        this.createdByUserId = createdByUserId;
        this.updatedByUserId = createdByUserId;
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
        if (currency == null) {
            currency = "PKR";
        }
        if (status == null) {
            status = RecurringRuleStatus.ACTIVE;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public RecurringFrequency getFrequency() {
        return frequency;
    }

    public void setFrequency(RecurringFrequency frequency) {
        this.frequency = frequency;
    }

    public int getAnchorDayOfMonth() {
        return anchorDayOfMonth;
    }

    public void setAnchorDayOfMonth(int anchorDayOfMonth) {
        this.anchorDayOfMonth = anchorDayOfMonth;
    }

    public int getAnchorMonth() {
        return anchorMonth;
    }

    public void setAnchorMonth(int anchorMonth) {
        this.anchorMonth = anchorMonth;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public void setNextDueDate(LocalDate nextDueDate) {
        this.nextDueDate = nextDueDate;
    }

    public RecurringRuleStatus getStatus() {
        return status;
    }

    public void setStatus(RecurringRuleStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(UUID createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public UUID getUpdatedByUserId() {
        return updatedByUserId;
    }

    public void setUpdatedByUserId(UUID updatedByUserId) {
        this.updatedByUserId = updatedByUserId;
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
