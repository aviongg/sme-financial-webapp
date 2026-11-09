package com.app.sme_health_backend.projectattribution.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "project_commercial_document_links",
        indexes = {
                @Index(name = "idx_proj_comm_doc_proj", columnList = "business_id, project_id, link_status"),
                @Index(name = "idx_proj_comm_doc_doc", columnList = "business_id, document_id, link_status")
        }
)
public class ProjectCommercialDocumentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "document_kind", nullable = false, length = 16)
    private String documentKind;

    @Column(name = "document_number", length = 32)
    private String documentNumber;

    @Column(name = "counterparty_name")
    private String counterpartyName;

    @Column(name = "document_date")
    private LocalDate documentDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "document_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal documentTotal;

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

    public ProjectCommercialDocumentLink() {
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

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public String getDocumentKind() {
        return documentKind;
    }

    public void setDocumentKind(String documentKind) {
        this.documentKind = documentKind;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public void setDocumentNumber(String documentNumber) {
        this.documentNumber = documentNumber;
    }

    public String getCounterpartyName() {
        return counterpartyName;
    }

    public void setCounterpartyName(String counterpartyName) {
        this.counterpartyName = counterpartyName;
    }

    public LocalDate getDocumentDate() {
        return documentDate;
    }

    public void setDocumentDate(LocalDate documentDate) {
        this.documentDate = documentDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public BigDecimal getDocumentTotal() {
        return documentTotal;
    }

    public void setDocumentTotal(BigDecimal documentTotal) {
        this.documentTotal = documentTotal;
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
