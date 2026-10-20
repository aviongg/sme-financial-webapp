package com.app.sme_health_backend.obligation;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "obligation_payment_schedules",
        indexes = {
                @Index(name = "idx_schedules_business_date", columnList = "business_id, expected_date"),
                @Index(name = "idx_schedules_obligation", columnList = "obligation_id, installment_number")
        }
)
public class ObligationPaymentSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "obligation_id", nullable = false)
    private UUID obligationId;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "expected_date", nullable = false)
    private LocalDate expectedDate;

    @Column(name = "expected_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ScheduleStatus status = ScheduleStatus.PLANNED;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public ObligationPaymentSchedule() {
    }

    public ObligationPaymentSchedule(
            UUID businessId,
            UUID obligationId,
            Integer installmentNumber,
            LocalDate expectedDate,
            BigDecimal expectedAmount,
            String notes,
            UUID createdByUserId
    ) {
        this.businessId = businessId;
        this.obligationId = obligationId;
        this.installmentNumber = installmentNumber;
        this.expectedDate = expectedDate;
        this.expectedAmount = expectedAmount;
        this.status = ScheduleStatus.PLANNED;
        this.notes = notes;
        this.createdByUserId = createdByUserId;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
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

    public UUID getObligationId() {
        return obligationId;
    }

    public void setObligationId(UUID obligationId) {
        this.obligationId = obligationId;
    }

    public Integer getInstallmentNumber() {
        return installmentNumber;
    }

    public void setInstallmentNumber(Integer installmentNumber) {
        this.installmentNumber = installmentNumber;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public void setExpectedDate(LocalDate expectedDate) {
        this.expectedDate = expectedDate;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public void setExpectedAmount(BigDecimal expectedAmount) {
        this.expectedAmount = expectedAmount;
    }

    public ScheduleStatus getStatus() {
        return status;
    }

    public void setStatus(ScheduleStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
