# FinSight Developer B Handoff Specification: Commission Cost Evidence (C08)

**Document Reference:** `docs/integration/dev-a/f10/C08_COMMISSION_COST_HANDOFF.md`
**Authoritative Contract Reference:** `docs/integration/dev-a/f10/C08_COMMISSION_AND_SALESPERSON_CONTRACT.md`
**Feature Wave:** Wave 2 (F10 Salesperson & Commission Management)
**Publishing Lead:** Developer A (Google Antigravity — Sole Full-Stack Lead)
**Target Domain:** Developer B (Commercial Documents F04, Project Planning F05, Cost Estimation F06/F09, Margin Analysis C05)
**Date:** 8 November 2026
**Status:** AUTHORITATIVE SPECIFICATION — APPROVED FOR INTEGRATION

---

## 1. Purpose & Integration Boundaries

In FinSight's Phase 2 architecture, commission calculations, salesperson customer assignments, and cash settlements are exclusively owned and calculated by Developer A.

Developer B's project profitability, commercial costing, and margin analysis subsystems require access to verified, immutable commission cost evidence to compute project-level margins and gross profit accurately.

This specification defines the read-only integration contract, Java interface, DTOs, and strict financial accounting rules governing how Developer B subsystems must consume commission data.

### Non-Duplication & Accounting Boundaries
1. **Developer A Authoritative Origin:** All commission calculations, earning statuses, advance records, payouts, and reversals originate exclusively from Developer A backend services.
2. **Read-Only Facade Access:** Developer B components must interact solely via the Spring service `CommissionCostEvidenceFacade`. Direct database mutations to commission tables (`salesperson_profiles`, `sales_assignments`, `commission_rules`, `commission_earnings`, `commission_advances`, `commission_payouts`) are strictly prohibited.
3. **No Automatic Project Attribution Without Verified Evidence:** Commissions cannot be assumed to belong to a project unless explicitly linked via commercial document or verified obligation metadata.
4. **No Double-Counting of Advances:** Advance disbursements (`CommissionAdvance`) represent cash outflows, NOT independent commission expense recognition. Applying an advance to an earning moves zero cash and must not be counted as an additional project cost.
5. **No General Ledger Liabilities:** Approved commission payable balances (`CommissionEarningStatus.APPROVED`) represent operational obligations to salespeople, not general-ledger nominal liabilities.
6. **Immutable Snapshots:** Recognized commission records carry rule rate snapshots and source fingerprints (`uq_commission_earning_source`). Rule modifications or status toggles never retroactively mutate historical cost evidence.

---

## 2. Spring Service Interface: `CommissionCostEvidenceFacade`

The facade is registered in the Spring application context as a read-only transactional service:

```java
package com.app.sme_health_backend.commission.facade;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommissionCostEvidenceFacade {

    /**
     * Retrieves authoritative cost evidence for a single recognized commission earning.
     */
    Optional<CommissionCostEvidence> getCommissionCostEvidence(
            BusinessAccessContext context,
            UUID earningId
    );

    /**
     * Lists commission cost evidence filtered by date window, salesperson, or counterparty.
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
```

---

## 3. Data Transfer Objects (DTOs)

### A. `CommissionCostEvidence` Record

```java
package com.app.sme_health_backend.commission.facade;

import com.app.sme_health_backend.commission.model.CommissionEarningStatus;
import com.app.sme_health_backend.commission.model.CommissionSourceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CommissionCostEvidence(
        UUID earningId,
        UUID businessId,
        UUID salespersonId,
        String salespersonName,
        UUID counterpartyId,
        String counterpartyName,
        CommissionSourceType sourceType,
        String sourceId,
        String sourceFingerprint,
        BigDecimal saleAmount,
        BigDecimal commissionRateSnapshot,
        BigDecimal earnedAmount,
        BigDecimal approvedAmount,
        CommissionEarningStatus status,
        BigDecimal settledAmount,
        BigDecimal netPaidAmount,
        String currency,
        Instant recognizedAt,
        Instant approvedAt,
        String reversalState, // "NONE", "PARTIALLY_REVERSED", "FULLY_REVERSED"
        String recognitionBasis // "COLLECTION_BASED" or "SALES_BASED"
) {}
```

### B. `SalespersonCostSummary` Record

```java
package com.app.sme_health_backend.commission.facade;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SalespersonCostSummary(
        UUID businessId,
        UUID salespersonId,
        String salespersonName,
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal totalEarnedAmount,
        BigDecimal totalApprovedAmount,
        BigDecimal totalSettledAmount,
        BigDecimal netPaidAmount,
        BigDecimal unappliedAdvanceBalance,
        String currency
) {}
```

---

## 4. Financial Semantics & Interpretation Rules

| Metric / Field | Financial Meaning | Project Profitability Treatment |
| :--- | :--- | :--- |
| `earnedAmount` | Commission recognized from verified cash collection or sale declaration. | Use as base commission cost if evaluating operational sales cost. |
| `approvedAmount` | Amount formally approved by Owner/Accountant for payout settlement. | Use as authoritative committed commission cost. |
| `settledAmount` | Cumulative settlements applied (`ADVANCE_APPLICATION` + net direct payouts). | Reflects obligation satisfaction progress. |
| `netPaidAmount` | Actual cash paid directly to salesperson (`PAYOUT` minus reversals). | Cash outflow metric; does NOT include advances. |
| `unappliedAdvanceBalance` | Outstanding cash advance held by salesperson not yet applied to any earning. | **DO NOT** count as project cost. It is an unapplied advance balance. |
| `reversalState` | Indicates whether payouts against this earning have been reversed. | If `FULLY_REVERSED` or `VOID`, exclude from recognized net project costs. |

---

## 5. Security, Multi-Tenant Scoping & Performance

1. **Multi-Tenant Isolation:** The caller's `BusinessAccessContext` enforces strict server-side scoping. Queries return only records matching `business_id = context.businessId()`.
2. **Transaction Isolation:** All facade queries execute under `@Transactional(readOnly = true)` to avoid acquiring unnecessary write locks or mutating database state.
3. **Currency & Precision:** All monetary amounts are returned in PKR with exact 2-decimal scale (`RoundingMode.HALF_UP`). Commission rate snapshots carry up to 4 decimal places (e.g. `5.0000%`).
