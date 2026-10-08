package com.app.sme_health_backend.documents.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Table(name = "document_corrections")
public class DocumentCorrection {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "document_id", nullable = false, updatable = false) private UUID documentId;
    @Column(name = "business_id", nullable = false, updatable = false) private UUID businessId;
    @Column(name = "actor_user_id", updatable = false) private UUID actorUserId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "previous_data", columnDefinition = "jsonb", updatable = false) private String previousData;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "new_data", nullable = false, columnDefinition = "jsonb", updatable = false) private String newData;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "changed_fields", nullable = false, columnDefinition = "jsonb", updatable = false) private List<String> changedFields;
    @Column(name = "corrected_at", nullable = false, updatable = false) private OffsetDateTime correctedAt = OffsetDateTime.now();

    protected DocumentCorrection() {}
    public DocumentCorrection(UUID documentId, UUID businessId, UUID actorUserId, String previousData, String newData, List<String> changedFields) {
        this.documentId = documentId; this.businessId = businessId; this.actorUserId = actorUserId;
        this.previousData = previousData; this.newData = newData; this.changedFields = List.copyOf(changedFields);
    }
    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public UUID getBusinessId() { return businessId; }
    public UUID getActorUserId() { return actorUserId; }
    public String getPreviousData() { return previousData; }
    public String getNewData() { return newData; }
    public List<String> getChangedFields() { return changedFields; }
    public OffsetDateTime getCorrectedAt() { return correctedAt; }
}
