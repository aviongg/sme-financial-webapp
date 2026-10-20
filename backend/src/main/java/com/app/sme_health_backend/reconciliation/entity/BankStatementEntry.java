package com.app.sme_health_backend.reconciliation.entity;

import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.reconciliation.model.StatementEntryStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "bank_statement_entries")
public class BankStatementEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "import_id", nullable = false, updatable = false)
    private UUID importId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "source_row_number", nullable = false, updatable = false)
    private int sourceRowNumber;

    @Column(name = "posted_date", nullable = false, updatable = false)
    private LocalDate postedDate;

    @Column(name = "value_date", updatable = false)
    private LocalDate valueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16, updatable = false)
    private MovementDirection direction;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency = "PKR";

    @Column(name = "description", length = 500, updatable = false)
    private String description;

    @Column(name = "bank_reference", length = 255, updatable = false)
    private String bankReference;

    @Column(name = "external_transaction_id", length = 255, updatable = false)
    private String externalTransactionId;

    @Column(name = "running_balance", precision = 19, scale = 4, updatable = false)
    private BigDecimal runningBalance;

    @Column(name = "source_fingerprint", nullable = false, length = 64, updatable = false)
    private String sourceFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private StatementEntryStatus status = StatementEntryStatus.UNMATCHED;

    @Column(name = "exclusion_reason", length = 255)
    private String exclusionReason;

    @Column(name = "linked_draft_id")
    private UUID linkedDraftId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public BankStatementEntry() {}

    public BankStatementEntry(
            UUID businessId,
            UUID importId,
            UUID accountId,
            int sourceRowNumber,
            LocalDate postedDate,
            LocalDate valueDate,
            MovementDirection direction,
            BigDecimal amount,
            String currency,
            String description,
            String bankReference,
            String externalTransactionId,
            BigDecimal runningBalance,
            String sourceFingerprint
    ) {
        this.businessId = businessId;
        this.importId = importId;
        this.accountId = accountId;
        this.sourceRowNumber = sourceRowNumber;
        this.postedDate = postedDate;
        this.valueDate = valueDate;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency != null ? currency : "PKR";
        this.description = description;
        this.bankReference = bankReference;
        this.externalTransactionId = externalTransactionId;
        this.runningBalance = runningBalance;
        this.sourceFingerprint = sourceFingerprint;
        this.status = StatementEntryStatus.UNMATCHED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }

    public UUID getImportId() { return importId; }
    public void setImportId(UUID importId) { this.importId = importId; }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public int getSourceRowNumber() { return sourceRowNumber; }
    public void setSourceRowNumber(int sourceRowNumber) { this.sourceRowNumber = sourceRowNumber; }

    public LocalDate getPostedDate() { return postedDate; }
    public void setPostedDate(LocalDate postedDate) { this.postedDate = postedDate; }

    public LocalDate getValueDate() { return valueDate; }
    public void setValueDate(LocalDate valueDate) { this.valueDate = valueDate; }

    public MovementDirection getDirection() { return direction; }
    public void setDirection(MovementDirection direction) { this.direction = direction; }

    public BigDecimal getAmount() {
        return amount != null ? amount.setScale(2, RoundingMode.HALF_UP) : null;
    }
    public void setAmount(BigDecimal amount) {
        this.amount = amount != null ? amount.setScale(2, RoundingMode.HALF_UP) : null;
    }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBankReference() { return bankReference; }
    public void setBankReference(String bankReference) { this.bankReference = bankReference; }

    public String getExternalTransactionId() { return externalTransactionId; }
    public void setExternalTransactionId(String externalTransactionId) { this.externalTransactionId = externalTransactionId; }

    public BigDecimal getRunningBalance() { return runningBalance; }
    public void setRunningBalance(BigDecimal runningBalance) { this.runningBalance = runningBalance; }

    public String getSourceFingerprint() { return sourceFingerprint; }
    public void setSourceFingerprint(String sourceFingerprint) { this.sourceFingerprint = sourceFingerprint; }

    public StatementEntryStatus getStatus() { return status; }
    public void setStatus(StatementEntryStatus status) { this.status = status; }

    public String getExclusionReason() { return exclusionReason; }
    public void setExclusionReason(String exclusionReason) { this.exclusionReason = exclusionReason; }

    public UUID getLinkedDraftId() { return linkedDraftId; }
    public void setLinkedDraftId(UUID linkedDraftId) { this.linkedDraftId = linkedDraftId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BankStatementEntry that = (BankStatementEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
