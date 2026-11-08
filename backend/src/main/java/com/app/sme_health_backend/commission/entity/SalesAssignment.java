package com.app.sme_health_backend.commission.entity;

import com.app.sme_health_backend.commission.model.AssignmentStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sales_assignments")
public class SalesAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "salesperson_id", nullable = false)
    private UUID salespersonId;

    @Column(name = "counterparty_id", nullable = false)
    private UUID counterpartyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AssignmentStatus status = AssignmentStatus.ACTIVE;

    @Column(name = "effective_start_date", nullable = false)
    private LocalDate effectiveStartDate;

    @Column(name = "effective_end_date")
    private LocalDate effectiveEndDate;

    @Column(name = "assigned_by_user_id", nullable = false)
    private UUID assignedByUserId;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public SalesAssignment() {
    }

    public SalesAssignment(UUID businessId, UUID salespersonId, UUID counterpartyId,
                           LocalDate effectiveStartDate, LocalDate effectiveEndDate,
                           UUID assignedByUserId, String notes) {
        this.businessId = businessId;
        this.salespersonId = salespersonId;
        this.counterpartyId = counterpartyId;
        this.effectiveStartDate = effectiveStartDate;
        this.effectiveEndDate = effectiveEndDate;
        this.assignedByUserId = assignedByUserId;
        this.notes = notes;
        this.status = AssignmentStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
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

    public UUID getSalespersonId() {
        return salespersonId;
    }

    public void setSalespersonId(UUID salespersonId) {
        this.salespersonId = salespersonId;
    }

    public UUID getCounterpartyId() {
        return counterpartyId;
    }

    public void setCounterpartyId(UUID counterpartyId) {
        this.counterpartyId = counterpartyId;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public void setStatus(AssignmentStatus status) {
        this.status = status;
    }

    public LocalDate getEffectiveStartDate() {
        return effectiveStartDate;
    }

    public void setEffectiveStartDate(LocalDate effectiveStartDate) {
        this.effectiveStartDate = effectiveStartDate;
    }

    public LocalDate getEffectiveEndDate() {
        return effectiveEndDate;
    }

    public void setEffectiveEndDate(LocalDate effectiveEndDate) {
        this.effectiveEndDate = effectiveEndDate;
    }

    public UUID getAssignedByUserId() {
        return assignedByUserId;
    }

    public void setAssignedByUserId(UUID assignedByUserId) {
        this.assignedByUserId = assignedByUserId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
