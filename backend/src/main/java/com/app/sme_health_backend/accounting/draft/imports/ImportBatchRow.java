package com.app.sme_health_backend.accounting.draft.imports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an imported spreadsheet row linkage and provenance.
 */
@Entity
@Table(name = "import_batch_rows")
public class ImportBatchRow {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "source_row_id", nullable = false, length = 100)
    private String sourceRowId;

    @Column(name = "source_row_number", nullable = false)
    private int sourceRowNumber;

    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    @Column(name = "raw_amount_input", length = 100)
    private String rawAmountInput;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "draft_id", nullable = false)
    private UUID draftId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ImportBatchRow() {
    }

    public ImportBatchRow(
            UUID businessId,
            UUID batchId,
            String sourceRowId,
            int sourceRowNumber,
            String sourceReference,
            String rawAmountInput,
            String payloadHash,
            UUID draftId
    ) {
        this.businessId = businessId;
        this.batchId = batchId;
        this.sourceRowId = sourceRowId;
        this.sourceRowNumber = sourceRowNumber;
        this.sourceReference = sourceReference;
        this.rawAmountInput = rawAmountInput;
        this.payloadHash = payloadHash;
        this.draftId = draftId;
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
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

    public UUID getBatchId() {
        return batchId;
    }

    public void setBatchId(UUID batchId) {
        this.batchId = batchId;
    }

    public String getSourceRowId() {
        return sourceRowId;
    }

    public void setSourceRowId(String sourceRowId) {
        this.sourceRowId = sourceRowId;
    }

    public int getSourceRowNumber() {
        return sourceRowNumber;
    }

    public void setSourceRowNumber(int sourceRowNumber) {
        this.sourceRowNumber = sourceRowNumber;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public void setSourceReference(String sourceReference) {
        this.sourceReference = sourceReference;
    }

    public String getRawAmountInput() {
        return rawAmountInput;
    }

    public void setRawAmountInput(String rawAmountInput) {
        this.rawAmountInput = rawAmountInput;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public UUID getDraftId() {
        return draftId;
    }

    public void setDraftId(UUID draftId) {
        this.draftId = draftId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
