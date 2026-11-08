package com.app.sme_health_backend.fx.entity;

import com.app.sme_health_backend.fx.model.FxSettlementStatus;
import com.app.sme_health_backend.fx.model.RealizedFxImpact;
import com.app.sme_health_backend.fx.model.RecognitionEvidenceStatus;
import com.app.sme_health_backend.fx.model.SettlementDirection;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fx_obligation_settlements")
public class FxObligationSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "obligation_id", nullable = false, updatable = false)
    private UUID obligationId;

    @Column(name = "financial_transaction_id", nullable = false, updatable = false)
    private UUID financialTransactionId;

    @Column(name = "allocation_id", nullable = false, updatable = false)
    private UUID allocationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_direction", nullable = false, length = 32, updatable = false)
    private SettlementDirection settlementDirection;

    @Column(name = "obligation_currency", nullable = false, length = 3, updatable = false)
    private String obligationCurrency;

    @Column(name = "obligation_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal obligationAmount;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "account_currency", nullable = false, length = 3, updatable = false)
    private String accountCurrency;

    @Column(name = "converted_principal", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal convertedPrincipal;

    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(name = "fee_currency", nullable = false, length = 3, updatable = false)
    private String feeCurrency;

    @Column(name = "total_account_movement", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal totalAccountMovement;

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal exchangeRate;

    @Column(name = "rate_convention", nullable = false, length = 32, updatable = false)
    private String rateConvention = "BASE_TO_QUOTE";

    @Column(name = "base_currency", nullable = false, length = 3, updatable = false)
    private String baseCurrency;

    @Column(name = "quote_currency", nullable = false, length = 3, updatable = false)
    private String quoteCurrency;

    @Column(name = "rate_source", nullable = false, length = 64, updatable = false)
    private String rateSource = "MANUAL_USER_RATE";

    @Column(name = "rate_timestamp", nullable = false, updatable = false)
    private Instant rateTimestamp = Instant.now();

    @Column(name = "settlement_date", nullable = false, updatable = false)
    private LocalDate settlementDate;

    @Column(name = "effective_rate", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal effectiveRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private FxSettlementStatus status = FxSettlementStatus.SETTLED;

    @Column(name = "reversal_transaction_id")
    private UUID reversalTransactionId;

    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "allocated_book_value", precision = 19, scale = 4)
    private BigDecimal allocatedBookValue;

    @Column(name = "realized_fx_difference", precision = 19, scale = 4)
    private BigDecimal realizedFxDifference;

    @Enumerated(EnumType.STRING)
    @Column(name = "realized_fx_impact", length = 16)
    private RealizedFxImpact realizedFxImpact;

    @Enumerated(EnumType.STRING)
    @Column(name = "recognition_evidence_status", nullable = false, length = 32)
    private RecognitionEvidenceStatus recognitionEvidenceStatus = RecognitionEvidenceStatus.UNAVAILABLE;

    @Column(name = "missing_evidence_reason", length = 255)
    private String missingEvidenceReason;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public FxObligationSettlement() {}

    public FxObligationSettlement(
            UUID businessId,
            UUID obligationId,
            UUID financialTransactionId,
            UUID allocationId,
            SettlementDirection settlementDirection,
            String obligationCurrency,
            BigDecimal obligationAmount,
            UUID accountId,
            String accountCurrency,
            BigDecimal convertedPrincipal,
            BigDecimal feeAmount,
            String feeCurrency,
            BigDecimal totalAccountMovement,
            BigDecimal exchangeRate,
            String rateConvention,
            String baseCurrency,
            String quoteCurrency,
            String rateSource,
            Instant rateTimestamp,
            LocalDate settlementDate,
            BigDecimal effectiveRate,
            String notes,
            String idempotencyKey,
            UUID createdByUserId
    ) {
        this.businessId = businessId;
        this.obligationId = obligationId;
        this.financialTransactionId = financialTransactionId;
        this.allocationId = allocationId;
        this.settlementDirection = settlementDirection;
        this.obligationCurrency = obligationCurrency;
        this.obligationAmount = obligationAmount;
        this.accountId = accountId;
        this.accountCurrency = accountCurrency;
        this.convertedPrincipal = convertedPrincipal;
        this.feeAmount = feeAmount != null ? feeAmount : BigDecimal.ZERO;
        this.feeCurrency = feeCurrency;
        this.totalAccountMovement = totalAccountMovement;
        this.exchangeRate = exchangeRate;
        this.rateConvention = rateConvention != null ? rateConvention : "BASE_TO_QUOTE";
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
        this.rateSource = rateSource != null ? rateSource : "MANUAL_USER_RATE";
        this.rateTimestamp = rateTimestamp != null ? rateTimestamp : Instant.now();
        this.settlementDate = settlementDate;
        this.effectiveRate = effectiveRate;
        this.status = FxSettlementStatus.SETTLED;
        this.notes = notes;
        this.idempotencyKey = idempotencyKey;
        this.createdByUserId = createdByUserId;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getBusinessId() { return businessId; }
    public UUID getObligationId() { return obligationId; }
    public UUID getFinancialTransactionId() { return financialTransactionId; }
    public UUID getAllocationId() { return allocationId; }
    public SettlementDirection getSettlementDirection() { return settlementDirection; }
    public String getObligationCurrency() { return obligationCurrency; }
    public BigDecimal getObligationAmount() { return obligationAmount; }
    public UUID getAccountId() { return accountId; }
    public String getAccountCurrency() { return accountCurrency; }
    public BigDecimal getConvertedPrincipal() { return convertedPrincipal; }
    public void setConvertedPrincipal(BigDecimal convertedPrincipal) { this.convertedPrincipal = convertedPrincipal; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public void setFeeAmount(BigDecimal feeAmount) { this.feeAmount = feeAmount; }
    public String getFeeCurrency() { return feeCurrency; }
    public BigDecimal getTotalAccountMovement() { return totalAccountMovement; }
    public void setTotalAccountMovement(BigDecimal totalAccountMovement) { this.totalAccountMovement = totalAccountMovement; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public String getRateConvention() { return rateConvention; }
    public String getBaseCurrency() { return baseCurrency; }
    public String getQuoteCurrency() { return quoteCurrency; }
    public String getRateSource() { return rateSource; }
    public Instant getRateTimestamp() { return rateTimestamp; }
    public LocalDate getSettlementDate() { return settlementDate; }
    public void setSettlementDate(LocalDate settlementDate) { this.settlementDate = settlementDate; }
    public void setSettlementDirection(SettlementDirection settlementDirection) { this.settlementDirection = settlementDirection; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public void setObligationId(UUID obligationId) { this.obligationId = obligationId; }
    public void setObligationCurrency(String obligationCurrency) { this.obligationCurrency = obligationCurrency; }
    public void setObligationAmount(BigDecimal obligationAmount) { this.obligationAmount = obligationAmount; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public void setAccountCurrency(String accountCurrency) { this.accountCurrency = accountCurrency; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }
    public BigDecimal getEffectiveRate() { return effectiveRate; }
    public FxSettlementStatus getStatus() { return status; }
    public void setStatus(FxSettlementStatus status) { this.status = status; }
    public UUID getReversalTransactionId() { return reversalTransactionId; }
    public void setReversalTransactionId(UUID reversalTransactionId) { this.reversalTransactionId = reversalTransactionId; }
    public String getReversalReason() { return reversalReason; }
    public void setReversalReason(String reversalReason) { this.reversalReason = reversalReason; }
    public Instant getReversedAt() { return reversedAt; }
    public void setReversedAt(Instant reversedAt) { this.reversedAt = reversedAt; }
    public String getNotes() { return notes; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public BigDecimal getAllocatedBookValue() { return allocatedBookValue; }
    public void setAllocatedBookValue(BigDecimal allocatedBookValue) { this.allocatedBookValue = allocatedBookValue; }
    public BigDecimal getRealizedFxDifference() { return realizedFxDifference; }
    public void setRealizedFxDifference(BigDecimal realizedFxDifference) { this.realizedFxDifference = realizedFxDifference; }
    public RealizedFxImpact getRealizedFxImpact() { return realizedFxImpact; }
    public void setRealizedFxImpact(RealizedFxImpact realizedFxImpact) { this.realizedFxImpact = realizedFxImpact; }
    public RecognitionEvidenceStatus getRecognitionEvidenceStatus() { return recognitionEvidenceStatus; }
    public void setRecognitionEvidenceStatus(RecognitionEvidenceStatus recognitionEvidenceStatus) { this.recognitionEvidenceStatus = recognitionEvidenceStatus; }
    public String getMissingEvidenceReason() { return missingEvidenceReason; }
    public void setMissingEvidenceReason(String missingEvidenceReason) { this.missingEvidenceReason = missingEvidenceReason; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getVersion() { return version; }
}
