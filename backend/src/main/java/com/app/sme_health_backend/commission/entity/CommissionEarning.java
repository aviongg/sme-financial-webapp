package com.app.sme_health_backend.commission.entity;

import com.app.sme_health_backend.commission.model.CommissionEarningStatus;
import com.app.sme_health_backend.commission.model.CommissionSourceType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "commission_earnings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_commission_earning_source", columnNames = {"business_id", "source_fingerprint"})
        }
)
public class CommissionEarning {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "salesperson_id", nullable = false)
    private UUID salespersonId;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "counterparty_id", nullable = false)
    private UUID counterpartyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private CommissionSourceType sourceType = CommissionSourceType.OBLIGATION_ALLOCATION;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(name = "source_fingerprint", nullable = false)
    private String sourceFingerprint;

    @Column(name = "sale_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal saleAmount;

    @Column(name = "commission_rate_snapshot", precision = 8, scale = 4)
    private BigDecimal commissionRateSnapshot;

    @Column(name = "commission_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal commissionAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "PKR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CommissionEarningStatus status = CommissionEarningStatus.EARNED;

    @Column(name = "approved_amount", precision = 19, scale = 4)
    private BigDecimal approvedAmount;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CommissionEarning() {
    }

    public CommissionEarning(UUID businessId, UUID salespersonId, UUID ruleId, UUID counterpartyId,
                             CommissionSourceType sourceType, String sourceId, String sourceFingerprint,
                             BigDecimal saleAmount, BigDecimal commissionRateSnapshot, BigDecimal commissionAmount,
                             String currency, CommissionEarningStatus status, String notes) {
        this.businessId = businessId;
        this.salespersonId = salespersonId;
        this.ruleId = ruleId;
        this.counterpartyId = counterpartyId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.sourceFingerprint = sourceFingerprint;
        this.saleAmount = saleAmount;
        this.commissionRateSnapshot = commissionRateSnapshot;
        this.commissionAmount = commissionAmount;
        this.currency = currency != null ? currency : "PKR";
        this.status = status != null ? status : CommissionEarningStatus.EARNED;
        this.notes = notes;
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

    public UUID getRuleId() {
        return ruleId;
    }

    public void setRuleId(UUID ruleId) {
        this.ruleId = ruleId;
    }

    public UUID getCounterpartyId() {
        return counterpartyId;
    }

    public void setCounterpartyId(UUID counterpartyId) {
        this.counterpartyId = counterpartyId;
    }

    public CommissionSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(CommissionSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceFingerprint() {
        return sourceFingerprint;
    }

    public void setSourceFingerprint(String sourceFingerprint) {
        this.sourceFingerprint = sourceFingerprint;
    }

    public BigDecimal getSaleAmount() {
        return saleAmount;
    }

    public void setSaleAmount(BigDecimal saleAmount) {
        this.saleAmount = saleAmount;
    }

    public BigDecimal getCommissionRateSnapshot() {
        return commissionRateSnapshot;
    }

    public void setCommissionRateSnapshot(BigDecimal commissionRateSnapshot) {
        this.commissionRateSnapshot = commissionRateSnapshot;
    }

    public BigDecimal getCommissionAmount() {
        return commissionAmount;
    }

    public void setCommissionAmount(BigDecimal commissionAmount) {
        this.commissionAmount = commissionAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public CommissionEarningStatus getStatus() {
        return status;
    }

    public void setStatus(CommissionEarningStatus status) {
        this.status = status;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public void setApprovedAmount(BigDecimal approvedAmount) {
        this.approvedAmount = approvedAmount;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public UUID getApprovedByUserId() {
        return approvedByUserId;
    }

    public void setApprovedByUserId(UUID approvedByUserId) {
        this.approvedByUserId = approvedByUserId;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
