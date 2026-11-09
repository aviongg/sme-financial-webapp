package com.app.sme_health_backend.projectattribution.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "project_financial_obligation_links",
        indexes = {
                @Index(name = "idx_proj_fin_ob_proj", columnList = "business_id, project_id, link_status"),
                @Index(name = "idx_proj_fin_ob_ob", columnList = "business_id, obligation_id, link_status")
        }
)
public class ProjectFinancialObligationLink {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "obligation_id", nullable = false)
    private UUID obligationId;

    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    @Column(name = "original_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal originalAmount;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "PKR";

    @Column(name = "link_status", nullable = false, length = 16)
    private String linkStatus = "ACTIVE";

    @Column(name = "link_provenance", length = 255)
    private String linkProvenance;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "unlinked_at")
    private Instant unlinkedAt;

    @Column(name = "unlinked_by")
    private UUID unlinkedBy;

    @Column(name = "unlink_reason", length = 255)
    private String unlinkReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public ProjectFinancialObligationLink() {
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

    public UUID getObligationId() {
        return obligationId;
    }

    public void setObligationId(UUID obligationId) {
        this.obligationId = obligationId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public void setOriginalAmount(BigDecimal originalAmount) {
        this.originalAmount = originalAmount;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }

    public void setAllocatedAmount(BigDecimal allocatedAmount) {
        this.allocatedAmount = allocatedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getLinkStatus() {
        return linkStatus;
    }

    public void setLinkStatus(String linkStatus) {
        this.linkStatus = linkStatus;
    }

    public String getLinkProvenance() {
        return linkProvenance;
    }

    public void setLinkProvenance(String linkProvenance) {
        this.linkProvenance = linkProvenance;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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

    public Instant getUnlinkedAt() {
        return unlinkedAt;
    }

    public void setUnlinkedAt(Instant unlinkedAt) {
        this.unlinkedAt = unlinkedAt;
    }

    public UUID getUnlinkedBy() {
        return unlinkedBy;
    }

    public void setUnlinkedBy(UUID unlinkedBy) {
        this.unlinkedBy = unlinkedBy;
    }

    public String getUnlinkReason() {
        return unlinkReason;
    }

    public void setUnlinkReason(String unlinkReason) {
        this.unlinkReason = unlinkReason;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
