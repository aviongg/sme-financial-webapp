package com.app.sme_health_backend.fx.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fx_obligation_recognition_evidence")
public class FxObligationRecognitionEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "obligation_id", nullable = false, updatable = false, unique = true)
    private UUID obligationId;

    @Column(name = "native_currency", nullable = false, length = 3, updatable = false)
    private String nativeCurrency;

    @Column(name = "native_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal nativeAmount;

    @Column(name = "functional_currency", nullable = false, length = 3, updatable = false)
    private String functionalCurrency = "PKR";

    @Column(name = "recognition_rate", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal recognitionRate;

    @Column(name = "rate_convention", nullable = false, length = 32, updatable = false)
    private String rateConvention = "BASE_TO_QUOTE";

    @Column(name = "book_value", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal bookValue;

    @Column(name = "recognition_date", nullable = false, updatable = false)
    private LocalDate recognitionDate;

    @Column(name = "recognition_source", nullable = false, length = 64, updatable = false)
    private String recognitionSource = "MANUAL_DECLARATION";

    @Column(name = "evidence_provenance", nullable = false, length = 64, updatable = false)
    private String evidenceProvenance = "VERIFIED_AT_CREATION";

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public FxObligationRecognitionEvidence() {}

    public FxObligationRecognitionEvidence(
            UUID businessId,
            UUID obligationId,
            String nativeCurrency,
            BigDecimal nativeAmount,
            String functionalCurrency,
            BigDecimal recognitionRate,
            String rateConvention,
            BigDecimal bookValue,
            LocalDate recognitionDate,
            String recognitionSource,
            String evidenceProvenance,
            String notes,
            UUID createdByUserId
    ) {
        this.businessId = businessId;
        this.obligationId = obligationId;
        this.nativeCurrency = nativeCurrency;
        this.nativeAmount = nativeAmount;
        this.functionalCurrency = (functionalCurrency != null && !functionalCurrency.isBlank()) ? functionalCurrency : "PKR";
        this.recognitionRate = recognitionRate;
        this.rateConvention = (rateConvention != null && !rateConvention.isBlank()) ? rateConvention : "BASE_TO_QUOTE";
        this.bookValue = bookValue;
        this.recognitionDate = recognitionDate;
        this.recognitionSource = (recognitionSource != null && !recognitionSource.isBlank()) ? recognitionSource : "MANUAL_DECLARATION";
        this.evidenceProvenance = (evidenceProvenance != null && !evidenceProvenance.isBlank()) ? evidenceProvenance : "USER_DECLARED_RECOGNITION";
        this.notes = notes;
        this.createdByUserId = createdByUserId;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getBusinessId() { return businessId; }
    public UUID getObligationId() { return obligationId; }
    public String getNativeCurrency() { return nativeCurrency; }
    public BigDecimal getNativeAmount() { return nativeAmount; }
    public String getFunctionalCurrency() { return functionalCurrency; }
    public BigDecimal getRecognitionRate() { return recognitionRate; }
    public String getRateConvention() { return rateConvention; }
    public BigDecimal getBookValue() { return bookValue; }
    public LocalDate getRecognitionDate() { return recognitionDate; }
    public String getRecognitionSource() { return recognitionSource; }
    public String getEvidenceProvenance() { return evidenceProvenance; }
    public String getNotes() { return notes; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getVersion() { return version; }
}

