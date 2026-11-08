package com.app.sme_health_backend.fx.entity;

import com.app.sme_health_backend.fx.model.FxSettlementStatus;
import com.app.sme_health_backend.fx.model.FxTransferFeePlacement;
import com.app.sme_health_backend.fx.model.RateConvention;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Authoritative dual-currency financial evidence entity for internal account transfers.
 */
@Entity
@Table(name = "fx_internal_transfers")
public class FxInternalTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "source_account_id", nullable = false, updatable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id", nullable = false, updatable = false)
    private UUID destinationAccountId;

    @Column(name = "source_currency", nullable = false, length = 3, updatable = false)
    private String sourceCurrency;

    @Column(name = "destination_currency", nullable = false, length = 3, updatable = false)
    private String destinationCurrency;

    @Column(name = "source_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal sourceAmount;

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal exchangeRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_convention", nullable = false, length = 32, updatable = false)
    private RateConvention rateConvention = RateConvention.BASE_TO_QUOTE;

    @Column(name = "base_currency", nullable = false, length = 3, updatable = false)
    private String baseCurrency;

    @Column(name = "quote_currency", nullable = false, length = 3, updatable = false)
    private String quoteCurrency;

    @Column(name = "rate_source", nullable = false, length = 64, updatable = false)
    private String rateSource = "MANUAL_USER_RATE";

    @Column(name = "rate_timestamp", nullable = false, updatable = false)
    private Instant rateTimestamp = Instant.now();

    @Column(name = "converted_destination_principal", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal convertedDestinationPrincipal;

    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(name = "fee_currency", nullable = false, length = 3, updatable = false)
    private String feeCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_placement", nullable = false, length = 32, updatable = false)
    private FxTransferFeePlacement feePlacement = FxTransferFeePlacement.DESTINATION_DEDUCTED;

    @Column(name = "net_destination_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal netDestinationAmount;

    @Column(name = "total_source_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal totalSourceAmount;

    @Column(name = "financial_transaction_id", nullable = false, updatable = false)
    private UUID financialTransactionId;

    @Column(name = "source_movement_id", nullable = false, updatable = false)
    private UUID sourceMovementId;

    @Column(name = "destination_movement_id", nullable = false, updatable = false)
    private UUID destinationMovementId;

    @Column(name = "transfer_date", nullable = false, updatable = false)
    private LocalDate transferDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private FxSettlementStatus status = FxSettlementStatus.SETTLED;

    @Column(name = "reversal_transaction_id")
    private UUID reversalTransactionId;

    @Column(name = "reversal_source_movement_id")
    private UUID reversalSourceMovementId;

    @Column(name = "reversal_destination_movement_id")
    private UUID reversalDestinationMovementId;

    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public FxInternalTransfer() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }

    public UUID getSourceAccountId() { return sourceAccountId; }
    public void setSourceAccountId(UUID sourceAccountId) { this.sourceAccountId = sourceAccountId; }

    public UUID getDestinationAccountId() { return destinationAccountId; }
    public void setDestinationAccountId(UUID destinationAccountId) { this.destinationAccountId = destinationAccountId; }

    public String getSourceCurrency() { return sourceCurrency; }
    public void setSourceCurrency(String sourceCurrency) { this.sourceCurrency = sourceCurrency; }

    public String getDestinationCurrency() { return destinationCurrency; }
    public void setDestinationCurrency(String destinationCurrency) { this.destinationCurrency = destinationCurrency; }

    public BigDecimal getSourceAmount() { return sourceAmount; }
    public void setSourceAmount(BigDecimal sourceAmount) { this.sourceAmount = sourceAmount; }

    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }

    public RateConvention getRateConvention() { return rateConvention; }
    public void setRateConvention(RateConvention rateConvention) { this.rateConvention = rateConvention; }

    public String getBaseCurrency() { return baseCurrency; }
    public void setBaseCurrency(String baseCurrency) { this.baseCurrency = baseCurrency; }

    public String getQuoteCurrency() { return quoteCurrency; }
    public void setQuoteCurrency(String quoteCurrency) { this.quoteCurrency = quoteCurrency; }

    public String getRateSource() { return rateSource; }
    public void setRateSource(String rateSource) { this.rateSource = rateSource; }

    public Instant getRateTimestamp() { return rateTimestamp; }
    public void setRateTimestamp(Instant rateTimestamp) { this.rateTimestamp = rateTimestamp; }

    public BigDecimal getConvertedDestinationPrincipal() { return convertedDestinationPrincipal; }
    public void setConvertedDestinationPrincipal(BigDecimal convertedDestinationPrincipal) { this.convertedDestinationPrincipal = convertedDestinationPrincipal; }

    public BigDecimal getFeeAmount() { return feeAmount; }
    public void setFeeAmount(BigDecimal feeAmount) { this.feeAmount = feeAmount; }

    public String getFeeCurrency() { return feeCurrency; }
    public void setFeeCurrency(String feeCurrency) { this.feeCurrency = feeCurrency; }

    public FxTransferFeePlacement getFeePlacement() { return feePlacement; }
    public void setFeePlacement(FxTransferFeePlacement feePlacement) { this.feePlacement = feePlacement; }

    public BigDecimal getNetDestinationAmount() { return netDestinationAmount; }
    public void setNetDestinationAmount(BigDecimal netDestinationAmount) { this.netDestinationAmount = netDestinationAmount; }

    public BigDecimal getTotalSourceAmount() { return totalSourceAmount; }
    public void setTotalSourceAmount(BigDecimal totalSourceAmount) { this.totalSourceAmount = totalSourceAmount; }

    public UUID getFinancialTransactionId() { return financialTransactionId; }
    public void setFinancialTransactionId(UUID financialTransactionId) { this.financialTransactionId = financialTransactionId; }

    public UUID getSourceMovementId() { return sourceMovementId; }
    public void setSourceMovementId(UUID sourceMovementId) { this.sourceMovementId = sourceMovementId; }

    public UUID getDestinationMovementId() { return destinationMovementId; }
    public void setDestinationMovementId(UUID destinationMovementId) { this.destinationMovementId = destinationMovementId; }

    public LocalDate getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDate transferDate) { this.transferDate = transferDate; }

    public FxSettlementStatus getStatus() { return status; }
    public void setStatus(FxSettlementStatus status) { this.status = status; }

    public UUID getReversalTransactionId() { return reversalTransactionId; }
    public void setReversalTransactionId(UUID reversalTransactionId) { this.reversalTransactionId = reversalTransactionId; }

    public UUID getReversalSourceMovementId() { return reversalSourceMovementId; }
    public void setReversalSourceMovementId(UUID reversalSourceMovementId) { this.reversalSourceMovementId = reversalSourceMovementId; }

    public UUID getReversalDestinationMovementId() { return reversalDestinationMovementId; }
    public void setReversalDestinationMovementId(UUID reversalDestinationMovementId) { this.reversalDestinationMovementId = reversalDestinationMovementId; }

    public String getReversalReason() { return reversalReason; }
    public void setReversalReason(String reversalReason) { this.reversalReason = reversalReason; }

    public Instant getReversedAt() { return reversedAt; }
    public void setReversedAt(Instant reversedAt) { this.reversedAt = reversedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public UUID getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(UUID createdByUserId) { this.createdByUserId = createdByUserId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
