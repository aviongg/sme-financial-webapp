package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "financial_obligations",
        indexes = {
                @Index(name = "idx_obligations_business_dir_status", columnList = "business_id, direction, status, issue_date"),
                @Index(name = "idx_obligations_business_cp", columnList = "business_id, counterparty_id"),
                @Index(name = "idx_obligations_business_due_date", columnList = "business_id, due_date")
        }
)
public class FinancialObligation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "counterparty_id", nullable = false)
    private UUID counterpartyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 32)
    private ObligationDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private ObligationSourceType sourceType = ObligationSourceType.MANUAL_DECLARATION;

    @Column(name = "source_id", length = 255)
    private String sourceId;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "original_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal originalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "PKR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ObligationStatus status = ObligationStatus.OPEN;

    @Column(name = "void_date")
    private LocalDate voidDate;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public FinancialObligation() {
    }

    public FinancialObligation(
            UUID businessId,
            UUID counterpartyId,
            ObligationDirection direction,
            ObligationSourceType sourceType,
            String sourceId,
            String referenceNumber,
            LocalDate issueDate,
            LocalDate dueDate,
            BigDecimal originalAmount,
            String currency,
            String description,
            UUID createdByUserId
    ) {
        this.businessId = businessId;
        this.counterpartyId = counterpartyId;
        this.direction = direction;
        this.sourceType = sourceType != null ? sourceType : ObligationSourceType.MANUAL_DECLARATION;
        this.sourceId = sourceId;
        this.referenceNumber = referenceNumber;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.originalAmount = originalAmount;
        this.currency = (currency != null && !currency.isBlank()) ? currency : "PKR";
        this.description = description;
        this.createdByUserId = createdByUserId;
        this.status = ObligationStatus.OPEN;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
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

    public UUID getCounterpartyId() {
        return counterpartyId;
    }

    public void setCounterpartyId(UUID counterpartyId) {
        this.counterpartyId = counterpartyId;
    }

    public ObligationDirection getDirection() {
        return direction;
    }

    public void setDirection(ObligationDirection direction) {
        this.direction = direction;
    }

    public ObligationSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(ObligationSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public void setOriginalAmount(BigDecimal originalAmount) {
        this.originalAmount = originalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public ObligationStatus getStatus() {
        return status;
    }

    public void setStatus(ObligationStatus status) {
        this.status = status;
    }

    public LocalDate getVoidDate() {
        return voidDate;
    }

    public void setVoidDate(LocalDate voidDate) {
        this.voidDate = voidDate;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public void setVoidReason(String voidReason) {
        this.voidReason = voidReason;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
