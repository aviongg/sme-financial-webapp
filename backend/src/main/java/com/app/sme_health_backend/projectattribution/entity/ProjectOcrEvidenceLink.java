package com.app.sme_health_backend.projectattribution.entity;

import com.app.sme_health_backend.projectattribution.model.AttributionSourceType;
import com.app.sme_health_backend.projectattribution.model.ProjectOcrEvidenceStatus;
import com.app.sme_health_backend.projectattribution.model.ProjectOcrMatchMethod;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "project_ocr_evidence_links",
        indexes = {
                @Index(name = "idx_proj_ocr_ev_proj", columnList = "business_id, project_id, evidence_status"),
                @Index(name = "idx_proj_ocr_ev_doc", columnList = "business_id, document_id, evidence_status"),
                @Index(name = "idx_proj_ocr_ev_attr", columnList = "business_id, attribution_id"),
                @Index(name = "idx_proj_ocr_ev_source", columnList = "business_id, financial_source_type, financial_source_id")
        }
)
public class ProjectOcrEvidenceLink {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_status", nullable = false, length = 32)
    private ProjectOcrEvidenceStatus evidenceStatus = ProjectOcrEvidenceStatus.EXTRACTED_UNREVIEWED;

    @Enumerated(EnumType.STRING)
    @Column(name = "financial_source_type", length = 32)
    private AttributionSourceType financialSourceType;

    @Column(name = "financial_source_id")
    private UUID financialSourceId;

    @Column(name = "attribution_id")
    private UUID attributionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_method", nullable = false, length = 32)
    private ProjectOcrMatchMethod matchMethod = ProjectOcrMatchMethod.UNMATCHED;

    @Column(name = "ocr_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal ocrAmount;

    @Column(name = "ocr_currency", nullable = false, length = 3)
    private String ocrCurrency = "PKR";

    @Column(name = "ocr_date")
    private LocalDate ocrDate;

    @Column(name = "counterparty_name", length = 255)
    private String counterpartyName;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "document_type", length = 64)
    private String documentType;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "discrepancy_amount", precision = 19, scale = 4)
    private BigDecimal discrepancyAmount;

    @Column(name = "discrepancy_notes", length = 500)
    private String discrepancyNotes;

    @Column(name = "link_provenance", length = 255)
    private String linkProvenance;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "confirmed_by_user_id")
    private UUID confirmedByUserId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "invalidated_by")
    private UUID invalidatedBy;

    @Column(name = "invalidation_reason", length = 255)
    private String invalidationReason;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public ProjectOcrEvidenceLink() {
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

    public ProjectOcrEvidenceStatus getEvidenceStatus() {
        return evidenceStatus;
    }

    public void setEvidenceStatus(ProjectOcrEvidenceStatus evidenceStatus) {
        this.evidenceStatus = evidenceStatus;
    }

    public AttributionSourceType getFinancialSourceType() {
        return financialSourceType;
    }

    public void setFinancialSourceType(AttributionSourceType financialSourceType) {
        this.financialSourceType = financialSourceType;
    }

    public UUID getFinancialSourceId() {
        return financialSourceId;
    }

    public void setFinancialSourceId(UUID financialSourceId) {
        this.financialSourceId = financialSourceId;
    }

    public UUID getAttributionId() {
        return attributionId;
    }

    public void setAttributionId(UUID attributionId) {
        this.attributionId = attributionId;
    }

    public ProjectOcrMatchMethod getMatchMethod() {
        return matchMethod;
    }

    public void setMatchMethod(ProjectOcrMatchMethod matchMethod) {
        this.matchMethod = matchMethod;
    }

    public BigDecimal getOcrAmount() {
        return ocrAmount;
    }

    public void setOcrAmount(BigDecimal ocrAmount) {
        this.ocrAmount = ocrAmount;
    }

    public String getOcrCurrency() {
        return ocrCurrency;
    }

    public void setOcrCurrency(String ocrCurrency) {
        this.ocrCurrency = ocrCurrency;
    }

    public LocalDate getOcrDate() {
        return ocrDate;
    }

    public void setOcrDate(LocalDate ocrDate) {
        this.ocrDate = ocrDate;
    }

    public String getCounterpartyName() {
        return counterpartyName;
    }

    public void setCounterpartyName(String counterpartyName) {
        this.counterpartyName = counterpartyName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public BigDecimal getDiscrepancyAmount() {
        return discrepancyAmount;
    }

    public void setDiscrepancyAmount(BigDecimal discrepancyAmount) {
        this.discrepancyAmount = discrepancyAmount;
    }

    public String getDiscrepancyNotes() {
        return discrepancyNotes;
    }

    public void setDiscrepancyNotes(String discrepancyNotes) {
        this.discrepancyNotes = discrepancyNotes;
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

    public UUID getConfirmedByUserId() {
        return confirmedByUserId;
    }

    public void setConfirmedByUserId(UUID confirmedByUserId) {
        this.confirmedByUserId = confirmedByUserId;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }

    public void setInvalidatedAt(Instant invalidatedAt) {
        this.invalidatedAt = invalidatedAt;
    }

    public UUID getInvalidatedBy() {
        return invalidatedBy;
    }

    public void setInvalidatedBy(UUID invalidatedBy) {
        this.invalidatedBy = invalidatedBy;
    }

    public String getInvalidationReason() {
        return invalidationReason;
    }

    public void setInvalidationReason(String invalidationReason) {
        this.invalidationReason = invalidationReason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
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

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
