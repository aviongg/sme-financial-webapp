package com.app.sme_health_backend.reconciliation.entity;

import com.app.sme_health_backend.reconciliation.model.StatementImportStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "bank_statement_imports")
public class BankStatementImport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "source_filename", length = 255)
    private String sourceFilename;

    @Column(name = "file_fingerprint", nullable = false, length = 64)
    private String fileFingerprint;

    @Column(name = "mapping_config", columnDefinition = "TEXT")
    private String mappingConfig;

    @Column(name = "total_entries", nullable = false)
    private int totalEntries = 0;

    @Column(name = "opening_balance", precision = 19, scale = 4)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", precision = 19, scale = 4)
    private BigDecimal closingBalance;

    @Column(name = "from_date")
    private LocalDate fromDate;

    @Column(name = "to_date")
    private LocalDate toDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private StatementImportStatus status = StatementImportStatus.ACTIVE;

    @Column(name = "imported_by_user_id", nullable = false, updatable = false)
    private UUID importedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public BankStatementImport() {}

    public BankStatementImport(
            UUID businessId,
            UUID accountId,
            String sourceFilename,
            String fileFingerprint,
            String mappingConfig,
            int totalEntries,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            LocalDate fromDate,
            LocalDate toDate,
            UUID importedByUserId
    ) {
        this.businessId = businessId;
        this.accountId = accountId;
        this.sourceFilename = sourceFilename;
        this.fileFingerprint = fileFingerprint;
        this.mappingConfig = mappingConfig;
        this.totalEntries = totalEntries;
        this.openingBalance = openingBalance;
        this.closingBalance = closingBalance;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.importedByUserId = importedByUserId;
        this.status = StatementImportStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public String getSourceFilename() { return sourceFilename; }
    public void setSourceFilename(String sourceFilename) { this.sourceFilename = sourceFilename; }

    public String getFileFingerprint() { return fileFingerprint; }
    public void setFileFingerprint(String fileFingerprint) { this.fileFingerprint = fileFingerprint; }

    public String getMappingConfig() { return mappingConfig; }
    public void setMappingConfig(String mappingConfig) { this.mappingConfig = mappingConfig; }

    public int getTotalEntries() { return totalEntries; }
    public void setTotalEntries(int totalEntries) { this.totalEntries = totalEntries; }

    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }

    public BigDecimal getClosingBalance() { return closingBalance; }
    public void setClosingBalance(BigDecimal closingBalance) { this.closingBalance = closingBalance; }

    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }

    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }

    public StatementImportStatus getStatus() { return status; }
    public void setStatus(StatementImportStatus status) { this.status = status; }

    public UUID getImportedByUserId() { return importedByUserId; }
    public void setImportedByUserId(UUID importedByUserId) { this.importedByUserId = importedByUserId; }

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
        BankStatementImport that = (BankStatementImport) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
