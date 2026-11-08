package com.app.sme_health_backend.commission.entity;

import com.app.sme_health_backend.commission.model.CalculationType;
import com.app.sme_health_backend.commission.model.CommissionRuleStatus;
import com.app.sme_health_backend.commission.model.EarningBasis;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "commission_rules")
public class CommissionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "salesperson_id")
    private UUID salespersonId;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_type", nullable = false, length = 32)
    private CalculationType calculationType = CalculationType.PERCENTAGE;

    @Enumerated(EnumType.STRING)
    @Column(name = "earning_basis", nullable = false, length = 32)
    private EarningBasis earningBasis = EarningBasis.COLLECTION_BASED;

    @Column(name = "rate_percentage", precision = 8, scale = 4)
    private BigDecimal ratePercentage;

    @Column(name = "fixed_amount", precision = 19, scale = 4)
    private BigDecimal fixedAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "PKR";

    @Column(name = "effective_start_date", nullable = false)
    private LocalDate effectiveStartDate;

    @Column(name = "effective_end_date")
    private LocalDate effectiveEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CommissionRuleStatus status = CommissionRuleStatus.ACTIVE;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CommissionRule() {
    }

    public CommissionRule(UUID businessId, UUID salespersonId, String ruleName,
                          CalculationType calculationType, EarningBasis earningBasis,
                          BigDecimal ratePercentage, BigDecimal fixedAmount, String currency,
                          LocalDate effectiveStartDate, LocalDate effectiveEndDate) {
        this.businessId = businessId;
        this.salespersonId = salespersonId;
        this.ruleName = ruleName;
        this.calculationType = calculationType;
        this.earningBasis = earningBasis;
        this.ratePercentage = ratePercentage;
        this.fixedAmount = fixedAmount;
        this.currency = currency != null ? currency : "PKR";
        this.effectiveStartDate = effectiveStartDate;
        this.effectiveEndDate = effectiveEndDate;
        this.status = CommissionRuleStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isEffectiveOn(LocalDate date) {
        if (date == null) return false;
        if (date.isBefore(effectiveStartDate)) return false;
        if (effectiveEndDate != null && date.isAfter(effectiveEndDate)) return false;
        return status == CommissionRuleStatus.ACTIVE;
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

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public CalculationType getCalculationType() {
        return calculationType;
    }

    public void setCalculationType(CalculationType calculationType) {
        this.calculationType = calculationType;
    }

    public EarningBasis getEarningBasis() {
        return earningBasis;
    }

    public void setEarningBasis(EarningBasis earningBasis) {
        this.earningBasis = earningBasis;
    }

    public BigDecimal getRatePercentage() {
        return ratePercentage;
    }

    public void setRatePercentage(BigDecimal ratePercentage) {
        this.ratePercentage = ratePercentage;
    }

    public BigDecimal getFixedAmount() {
        return fixedAmount;
    }

    public void setFixedAmount(BigDecimal fixedAmount) {
        this.fixedAmount = fixedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getEffectiveStartDate() {
        return effectiveStartDate;
    }

    public void setEffectiveStartDate(LocalDate effectiveStartDate) {
        this.effectiveStartDate = effectiveStartDate;
    }

    public LocalDate getEffectiveEndDate() {
        return effectiveEndDate;
    }

    public void setEffectiveEndDate(LocalDate effectiveEndDate) {
        this.effectiveEndDate = effectiveEndDate;
    }

    public CommissionRuleStatus getStatus() {
        return status;
    }

    public void setStatus(CommissionRuleStatus status) {
        this.status = status;
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
