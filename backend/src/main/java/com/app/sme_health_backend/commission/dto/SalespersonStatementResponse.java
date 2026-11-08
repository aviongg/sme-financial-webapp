package com.app.sme_health_backend.commission.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Authoritative salesperson commission statement for a specified period.
 */
public record SalespersonStatementResponse(
        // Header
        UUID businessId,
        String businessName,
        UUID salespersonId,
        String salespersonName,
        String salespersonEmail,
        String salespersonPhone,
        LocalDate startDate,
        LocalDate endDate,
        Instant generatedAt,

        // Positions & Summaries
        BigDecimal openingSettlementPosition,
        BigDecimal periodEarned,
        BigDecimal periodApproved,
        BigDecimal periodAdvancesIssued,
        BigDecimal periodAdvancesApplied,
        BigDecimal periodGrossPayouts,
        BigDecimal periodPayoutReversals,
        BigDecimal periodNetPayouts,
        BigDecimal periodTotalSettled,
        BigDecimal closingSettlementPosition,
        BigDecimal unappliedAdvanceBalance,
        String currency,

        // Line Items
        List<StatementCustomer> assignedCustomers,
        List<StatementEarning> earnings,
        List<StatementSettlement> settlements,

        // Provenance & Disclaimers
        String provenanceNotes
) {
    public record StatementCustomer(
            UUID counterpartyId,
            String counterpartyName,
            LocalDate effectiveStartDate,
            LocalDate effectiveEndDate
    ) {}

    public record StatementEarning(
            UUID earningId,
            LocalDate date,
            UUID counterpartyId,
            String counterpartyName,
            BigDecimal collectionAmount,
            String ruleDescription,
            BigDecimal earnedAmount,
            BigDecimal approvedAmount,
            String status,
            String notes
    ) {}

    public record StatementSettlement(
            UUID settlementId,
            LocalDate date,
            String settlementType, // "ADVANCE_ISSUED", "ADVANCE_APPLIED", "PAYOUT", "PAYOUT_REVERSED"
            BigDecimal amount,
            String currency,
            String status,
            UUID linkedTransactionId,
            String notes
    ) {}
}
