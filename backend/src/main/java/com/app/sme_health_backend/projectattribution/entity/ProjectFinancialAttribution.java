package com.app.sme_health_backend.projectattribution.entity;

import com.app.sme_health_backend.projectattribution.model.*;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "project_financial_attributions", indexes = {
        @Index(name = "idx_proj_attr_biz_proj", columnList = "business_id, project_id, status"),
        @Index(name = "idx_proj_attr_biz_source", columnList = "business_id, source_type, source_id, status"),
        @Index(name = "idx_proj_attr_biz_date", columnList = "business_id, attribution_date")
})
public class ProjectFinancialAttribution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32, updatable = false)
    private AttributionSourceType sourceType;

    @Column(name = "source_id", nullable = false, updatable = false)
    private UUID sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_direction", nullable = false, length = 16, updatable = false)
    private AttributionDirection sourceDirection;

    @Column(name = "source_currency", nullable = false, length = 3, updatable = false)
    private String sourceCurrency;

    @Column(name = "source_eligible_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal sourceEligibleAmount;

    @Column(name = "attributed_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal attributedAmount;

    @Column(name = "attributed_currency", nullable = false, length = 3, updatable = false)
    private String attributedCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_category", nullable = false, length = 16, updatable = false)
    private AttributionCostCategory costCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "economic_classification", nullable = false, length = 32, updatable = false)
    private EconomicClassification economicClassification;

    @Column(name = "source_event_date", nullable = false, updatable = false)
    private LocalDate sourceEventDate;

    @Column(name = "attribution_date", nullable = false, updatable = false)
    private LocalDate attributionDate;

    @Column(name = "notes", length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AttributionStatus status = AttributionStatus.ACTIVE;

    @Column(name = "reversal_reason", length = 255)
    private String reversalReason;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversed_by")
    private UUID reversedBy;

    @Column(name = "idempotency_key", length = 128, updatable = false)
    private String idempotencyKey;

    @Column(name = "source_provenance", length = 255, updatable = false)
    private String sourceProvenance;

    @Column(name = "exchange_rate", precision = 19, scale = 6, updatable = false)
    private BigDecimal exchangeRate;

    @Column(name = "rate_source", length = 64, nullable = false)
    private String rateSource = "DECLARED_USER_ASSUMPTION";

    @Column(name = "rate_convention", length = 64, nullable = false)
    private String rateConvention = "MULTIPLY_SOURCE_TO_BASE";

    @Column(name = "rate_date")
    private LocalDate rateDate;

    @Column(name = "rate_verified", nullable = false)
    private boolean rateVerified = false;

    @Column(name = "base_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal baseAmount;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public ProjectFinancialAttribution() {
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

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public AttributionSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(AttributionSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public AttributionDirection getSourceDirection() {
        return sourceDirection;
    }

    public void setSourceDirection(AttributionDirection sourceDirection) {
        this.sourceDirection = sourceDirection;
    }

    public String getSourceCurrency() {
        return sourceCurrency;
    }

    public void setSourceCurrency(String sourceCurrency) {
        this.sourceCurrency = sourceCurrency;
    }

    public BigDecimal getSourceEligibleAmount() {
        return sourceEligibleAmount;
    }

    public void setSourceEligibleAmount(BigDecimal sourceEligibleAmount) {
        this.sourceEligibleAmount = sourceEligibleAmount;
    }

    public BigDecimal getAttributedAmount() {
        return attributedAmount;
    }

    public void setAttributedAmount(BigDecimal attributedAmount) {
        this.attributedAmount = attributedAmount;
    }

    public String getAttributedCurrency() {
        return attributedCurrency;
    }

    public void setAttributedCurrency(String attributedCurrency) {
        this.attributedCurrency = attributedCurrency;
    }

    public AttributionCostCategory getCostCategory() {
        return costCategory;
    }

    public void setCostCategory(AttributionCostCategory costCategory) {
        this.costCategory = costCategory;
    }

    public EconomicClassification getEconomicClassification() {
        return economicClassification;
    }

    public void setEconomicClassification(EconomicClassification economicClassification) {
        this.economicClassification = economicClassification;
    }

    public LocalDate getSourceEventDate() {
        return sourceEventDate;
    }

    public void setSourceEventDate(LocalDate sourceEventDate) {
        this.sourceEventDate = sourceEventDate;
    }

    public LocalDate getAttributionDate() {
        return attributionDate;
    }

    public void setAttributionDate(LocalDate attributionDate) {
        this.attributionDate = attributionDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public AttributionStatus getStatus() {
        return status;
    }

    public void setStatus(AttributionStatus status) {
        this.status = status;
    }

    public String getReversalReason() {
        return reversalReason;
    }

    public void setReversalReason(String reversalReason) {
        this.reversalReason = reversalReason;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public void setReversedAt(Instant reversedAt) {
        this.reversedAt = reversedAt;
    }

    public UUID getReversedBy() {
        return reversedBy;
    }

    public void setReversedBy(UUID reversedBy) {
        this.reversedBy = reversedBy;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getSourceProvenance() {
        return sourceProvenance;
    }

    public void setSourceProvenance(String sourceProvenance) {
        this.sourceProvenance = sourceProvenance;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public void setExchangeRate(BigDecimal exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    public String getRateSource() {
        return rateSource;
    }

    public void setRateSource(String rateSource) {
        this.rateSource = rateSource;
    }

    public String getRateConvention() {
        return rateConvention;
    }

    public void setRateConvention(String rateConvention) {
        this.rateConvention = rateConvention;
    }

    public LocalDate getRateDate() {
        return rateDate;
    }

    public void setRateDate(LocalDate rateDate) {
        this.rateDate = rateDate;
    }

    public boolean isRateVerified() {
        return rateVerified;
    }

    public void setRateVerified(boolean rateVerified) {
        this.rateVerified = rateVerified;
    }

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(BigDecimal baseAmount) {
        this.baseAmount = baseAmount;
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

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectFinancialAttribution that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
