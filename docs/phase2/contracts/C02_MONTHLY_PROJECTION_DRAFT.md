# Contract C02: Monthly Projection & Cutover Specification (Draft)

**Contract ID:** `C02_MONTHLY_PROJECTION_DRAFT`  
**Version:** `1.1`  
**Date:** 14 October 2026  
**Status:** `DRAFT_PENDING_REVIEW`  
**Owner:** Antigravity (Developer A Backend)  
**Reviewers:** Codex (Developer A Frontend), Fatima (Developer B & AI Orchestration)  
**Target Release:** Wave 1 (`A-W1-04-BE` / `A-W1-04-FE`)  

> [!WARNING]
> This contract is a technical draft proposal undergoing cross-assistant review. It is not approved, frozen, or authorized for implementation.

---

## 1. Revision History & Codex Review Disposition

| Version | Date | Author | Summary of Changes |
| :--- | :--- | :--- | :--- |
| 1.0 | 2026-10-14 | Antigravity | Initial proposal for ledger cutover, legacy preservation, and monthly materialization. |
| 1.1 | 2026-10-14 | Antigravity | **Addressed Codex Mutation Findings M4–M9:** Documented opening date vs. cutover date (M4); detailed multi-month cash balance carry-forward propagation (M5); addressed negative derived figure rejection (M6); specified historical account inclusion and multi-currency base valuation (M7); created field authority matrix and PATCH semantics for optional declarations (M8); verified OCR posting authority (M9); replaced "Approved Rule" with "Proposed Policy". |

---

## 2. Cutover Policy & Date Semantics (M4: Dates)

### 2.1. Dual-Authority Modes
Each business profile maintains an authoritative configuration:
*   `data_mode`: `MONTHLY_SNAPSHOT` (Default legacy mode) or `TRANSACTION_LEDGER` (Phase 2 ledger mode).
*   `ledger_cutover_month`: The first calendar month (`YYYY-MM`) governed by the transaction ledger.

```
Months < ledger_cutover_month        ──>  MONTHLY_SNAPSHOT Authority (Immutable legacy data)
Months >= ledger_cutover_month       ──>  TRANSACTION_LEDGER Authority (Derived from posted events)
```

### 2.2. Opening Date vs. Cutover Month (Proposed Policy)
*   **Opening Date:** The effective calendar date (`YYYY-MM-DD`) assigned to an account's baseline opening balance.
*   **Cutover Month:** The start of the calendar month (`YYYY-MM-01`) where transactions begin driving the monthly record.
*   **Alignment Requirement:** Account `openingDate` must be on or before the first day of `ledger_cutover_month`. Setting an opening date within an active ledger month is treated as a starting balance adjustment effective on that date.
*   **Historical Preservation:** Months strictly prior to `ledger_cutover_month` are completely immutable. Ledger transactions cannot be posted with an effective date prior to `ledger_cutover_month`.

---

## 3. Field Authority Matrix & Declarations Maintenance (M8: Declarations)

To prevent locking users out of necessary health score inputs while preserving ledger integrity, `MonthlyRecord` fields are strictly partitioned:

| `MonthlyRecord` Field | Authority in Ledger Mode | Editable via Direct API? | Derivation Source |
| :--- | :--- | :--- | :--- |
| `revenue` | **LEDGER** | **NO** (Read-Only) | `MonthlyAggregationService` from posted revenue events |
| `operatingExpenses` | **LEDGER** | **NO** (Read-Only) | `MonthlyAggregationService` from posted operating expense events |
| `cogs` | **LEDGER** | **NO** (Read-Only) | Approved COGS events from Fatima (F08) *(Wave 4)* |
| `cashInflow` | **LEDGER** | **NO** (Read-Only) | Account movements (`INCREASE`, non-transfer, non-opening) |
| `cashOutflow` | **LEDGER** | **NO** (Read-Only) | Account movements (`DECREASE`, non-transfer) |
| `cashBalanceEom` | **LEDGER** | **NO** (Read-Only) | Sum of active account balances as of month-end |
| `receivablesOutstanding`| **LEDGER** | **NO** (Read-Only) | Unsettled receivables from ObligationService (F03) *(Wave 2)* |
| `payablesOutstanding` | **LEDGER** | **NO** (Read-Only) | Unsettled payables from ObligationService (F03) *(Wave 2)* |
| `inventoryValue` | **DECLARATION** | **YES** (Via PATCH) | Supplemental user declaration until F08 is enabled |
| `loanOutstanding` | **DECLARATION** | **YES** (Via PATCH) | Supplemental user declaration |
| `interestExpense` | **DECLARATION** | **YES** (Via PATCH) | Supplemental user declaration |
| `financingType` | **DECLARATION** | **YES** (Via PATCH) | Supplemental user declaration (`none`, `conventional`, `islamic`) |

### Declarations PATCH Semantics (`PATCH /api/records/monthly/{month}/declarations`)
*   **Presence-Aware:** Omitted fields in PATCH payload are preserved unchanged. Explicit `null` clears the value.
*   **Validation:** Negative numbers for loan outstanding or interest expense are rejected with HTTP 400.
*   **Compliance Status:** Compliance declarations (tax registration, audit status) remain stored on `BusinessProfile` in accordance with baseline MVP design; they are not duplicated in monthly records.

---

## 4. Idempotent Monthly Materialization (M7: Monthly Mapping)

### 4.1. Account Inclusion & Historical Carry-Forward
*   **Active vs. Archived Accounts:** All accounts that were `ACTIVE` during any portion of month $M$ are included in month-end cash calculation.
*   **Foreign Currency Base Valuation:** Accounts held in foreign currencies (e.g. `USD`, `AED`) are converted to base currency (`PKR`) using the locked exchange rate effective as of the last calendar day of the month.
*   **Forward Propagation (M5):** When a transaction is backdated into month $M$, the closing cash balance (`cashBalanceEom`) is recomputed for month $M$ and automatically carried forward to all subsequent months $M+1, M+2, \dots$ up to the current month.

### 4.2. Derived Negative Value Rejection (M6)
*   If a backdated refund, cancellation, or reversal causes derived monthly revenue or cash inflow to drop below `0.00`:
    *   The posting transaction is rejected atomically with HTTP `422 Unprocessable Entity` (`ERROR_DERIVED_NEGATIVE_UNSUPPORTED`).
    *   The system will **never** silently clamp negative derived numbers to `0.00`.

---

## 5. OCR & Document Review in Ledger Mode (M9)

*   **Proposed Policy:** Unconfirmed OCR extractions and review drafts never create financial actuals.
*   **Confirm-Once Integration:** In ledger mode, clicking "Confirm Document" does **not** update `MonthlyRecord` directly. Instead:
    1.  It creates a posted `FinancialTransaction` linked to `source_document_id`.
    2.  `MonthlyAggregationService` materializes the transaction into `MonthlyRecord`.
    3.  This preserves atomic confirm-once semantics and document provenance.

---

## 6. Open Cross-Domain Decisions (Pending Fatima Review)

1.  **Revenue Accrual Policy (F04):** Does an invoice issued in F04 feed revenue to C02 immediately upon issuance, or upon cash collection? *(Recommended: Accrual upon issuance)*.
2.  **COGS Journal Frequency (F08):** Does inventory valuation post continuous COGS movements, or an end-of-month aggregated figure?
