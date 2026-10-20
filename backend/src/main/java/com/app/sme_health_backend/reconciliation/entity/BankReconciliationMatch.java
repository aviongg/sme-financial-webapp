package com.app.sme_health_backend.reconciliation.entity;

import com.app.sme_health_backend.reconciliation.model.ReconciliationMatchMethod;
import com.app.sme_health_backend.reconciliation.model.ReconciliationMatchStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "bank_reconciliation_matches")
public class BankReconciliationMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "statement_entry_id", nullable = false, updatable = false)
    private UUID statementEntryId;

    @Column(name = "account_movement_id", nullable = false, updatable = false)
    private UUID accountMovementId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReconciliationMatchStatus status = ReconciliationMatchStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_method", nullable = false, length = 32)
    private ReconciliationMatchMethod matchMethod = ReconciliationMatchMethod.EXACT;

    @Column(name = "confidence_score", nullable = false)
    private int confidenceScore = 100;

    @Column(name = "matched_by_user_id", nullable = false, updatable = false)
    private UUID matchedByUserId;

    @Column(name = "matched_at", nullable = false, updatable = false)
    private Instant matchedAt = Instant.now();

    @Column(name = "unmatched_by_user_id")
    private UUID unmatchedByUserId;

    @Column(name = "unmatched_at")
    private Instant unmatchedAt;

    @Column(name = "unmatch_reason", length = 500)
    private String unmatchReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public BankReconciliationMatch() {}

    public BankReconciliationMatch(
            UUID businessId,
            UUID statementEntryId,
            UUID accountMovementId,
            UUID accountId,
            ReconciliationMatchMethod matchMethod,
            int confidenceScore,
            UUID matchedByUserId
    ) {
        this.businessId = businessId;
        this.statementEntryId = statementEntryId;
        this.accountMovementId = accountMovementId;
        this.accountId = accountId;
        this.matchMethod = matchMethod != null ? matchMethod : ReconciliationMatchMethod.EXACT;
        this.confidenceScore = confidenceScore;
        this.matchedByUserId = matchedByUserId;
        this.status = ReconciliationMatchStatus.ACTIVE;
        this.matchedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }

    public UUID getStatementEntryId() { return statementEntryId; }
    public void setStatementEntryId(UUID statementEntryId) { this.statementEntryId = statementEntryId; }

    public UUID getAccountMovementId() { return accountMovementId; }
    public void setAccountMovementId(UUID accountMovementId) { this.accountMovementId = accountMovementId; }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public ReconciliationMatchStatus getStatus() { return status; }
    public void setStatus(ReconciliationMatchStatus status) { this.status = status; }

    public ReconciliationMatchMethod getMatchMethod() { return matchMethod; }
    public void setMatchMethod(ReconciliationMatchMethod matchMethod) { this.matchMethod = matchMethod; }

    public int getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(int confidenceScore) { this.confidenceScore = confidenceScore; }

    public UUID getMatchedByUserId() { return matchedByUserId; }
    public void setMatchedByUserId(UUID matchedByUserId) { this.matchedByUserId = matchedByUserId; }

    public Instant getMatchedAt() { return matchedAt; }
    public void setMatchedAt(Instant matchedAt) { this.matchedAt = matchedAt; }

    public UUID getUnmatchedByUserId() { return unmatchedByUserId; }
    public void setUnmatchedByUserId(UUID unmatchedByUserId) { this.unmatchedByUserId = unmatchedByUserId; }

    public Instant getUnmatchedAt() { return unmatchedAt; }
    public void setUnmatchedAt(Instant unmatchedAt) { this.unmatchedAt = unmatchedAt; }

    public String getUnmatchReason() { return unmatchReason; }
    public void setUnmatchReason(String unmatchReason) { this.unmatchReason = unmatchReason; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BankReconciliationMatch that = (BankReconciliationMatch) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
