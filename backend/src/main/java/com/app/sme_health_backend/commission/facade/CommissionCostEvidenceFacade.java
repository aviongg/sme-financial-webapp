package com.app.sme_health_backend.commission.facade;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Controlled, read-only integration facade allowing authorized Developer B commercial and
 * project planning subsystems (F04, F05, C05) to consume verified commission cost evidence
 * without recalculating figures or triggering financial ledger postings.
 */
public interface CommissionCostEvidenceFacade {

    /**
     * Retrieves cost evidence for a single recognized commission earning.
     */
    Optional<CommissionCostEvidence> getCommissionCostEvidence(BusinessAccessContext context, UUID earningId);

    /**
     * Lists cost evidence filtered by date window, salesperson, or counterparty.
     */
    List<CommissionCostEvidence> listCommissionCostEvidence(
            BusinessAccessContext context,
            LocalDate fromDate,
            LocalDate toDate,
            UUID salespersonId,
            UUID counterpartyId
    );

    /**
     * Obtains an aggregated commission cost summary for a salesperson across a specified period.
     */
    SalespersonCostSummary getSalespersonCostSummary(
            BusinessAccessContext context,
            UUID salespersonId,
            LocalDate fromDate,
            LocalDate toDate
    );
}
