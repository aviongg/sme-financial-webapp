package com.app.sme_health_backend.documents.entity;

import com.app.sme_health_backend.documents.processing.DocumentStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "uploaded_documents")
public class UploadedDocument implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "file_url", nullable = false, columnDefinition = "TEXT")
    private String fileUrl;

    @Column(name = "upload_timestamp", nullable = false)
    private LocalDateTime uploadTimestamp;

    @Column(name = "document_type_hint", length = 20)
    private String documentTypeHint = "unknown";

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 15)
    private DocumentStatus processingStatus = DocumentStatus.pending;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_data", columnDefinition = "jsonb")
    private String extractedData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "confirmed_data", columnDefinition = "jsonb")
    private String confirmedData;

    @Column(name = "linked_month", length = 7)
    private String linkedMonth;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "storage_path", length = 500)
    private String storagePath;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;


    public UploadedDocument() {
        this.uploadTimestamp = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public LocalDateTime getUploadTimestamp() {
        return uploadTimestamp;
    }

    public void setUploadTimestamp(LocalDateTime uploadTimestamp) {
        this.uploadTimestamp = uploadTimestamp;
    }

    public String getDocumentTypeHint() {
        return documentTypeHint;
    }

    public void setDocumentTypeHint(String documentTypeHint) {
        this.documentTypeHint = documentTypeHint;
    }

    public DocumentStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(DocumentStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getExtractedData() {
        return extractedData;
    }

    public void setExtractedData(String extractedData) {
        this.extractedData = extractedData;
    }

    public String getConfirmedData() {
        return confirmedData;
    }

    public void setConfirmedData(String confirmedData) {
        this.confirmedData = confirmedData;
    }

    public String getLinkedMonth() {
        return linkedMonth;
    }

    public void setLinkedMonth(String linkedMonth) {
        this.linkedMonth = linkedMonth;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getProcessingStartedAt() {
        return processingStartedAt;
    }

    public void setProcessingStartedAt(LocalDateTime processingStartedAt) {
        this.processingStartedAt = processingStartedAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }


    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }
}
