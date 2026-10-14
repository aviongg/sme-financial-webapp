# ADR 001: Ledger Authority, Month Cutover & Opening Balance Semantics (Draft)

**Document ID:** `ADR_001_LEDGER_AUTHORITY_AND_OPENING_BALANCES_DRAFT`  
**Version:** `1.1`  
**Date:** 14 October 2026  
**Status:** `DRAFT_PENDING_REVIEW`  
**Author:** Antigravity (Developer A Backend)  
**Required Reviewers:** Muhammad Suleman Adil (Project Lead), Fatima (Developer B & AI), Codex (Developer A Frontend)  

---

## 1. Revision History & Codex Review Disposition

| Version | Date | Author | Summary of Changes |
| :--- | :--- | :--- | :--- |
| 1.0 | 2026-10-14 | Antigravity | Initial proposal for ledger authority, opening balance neutrality, and locking. |
| 1.1 | 2026-10-14 | Antigravity | **Addressed Codex Mutation Findings M1, M4–M8:** Formally removed dual-counting opening formula (M1); distinguished opening date from cutover date (M4); documented forward cash carry-forward propagation (M5); formalized domain rejection of derived negative values (M6); detailed hybrid maintenance for optional declarations (M8); replaced "Approved Rule" with "Proposed Policy". |

---

## 2. Context & Problem Statement

In the completed FinSight MVP, financial data is stored as monthly aggregate records (`MonthlyRecord`), with users entering summary figures for revenue, cash flow, expenses, and cash balance. In Phase 2, FinSight introduces detailed financial accounts (`financial_accounts`) and an event-driven transaction ledger (`financial_transactions`).

This architectural transition introduces critical questions:
1.  How do we transition an existing business from manual monthly snapshots to transaction-backed aggregation without corrupting historical records?
2.  How should opening account balances be treated so they establish cash without falsely inflating revenue or monthly cash inflow?
3.  How can non-derivable optional declarations (e.g. loan outstanding, tax compliance declarations) continue to be maintained once ledger mode is active?
4.  How are concurrent postings, backdated entries, and multi-month cash propagation handled to guarantee data integrity?

---

## 3. Existing Verified Behavior (MVP Baseline)

*   `MonthlyRecord` stores a single record per `(businessId, month)` containing `revenue`, `operatingExpenses`, `cashInflow`, `cashOutflow`, and `cashBalanceEom`.
*   All monetary fields have database constraints requiring values $\ge 0.00$.
*   Saving a `MonthlyRecord` automatically invokes `recalculateAffectedScores`, updating up to 6 chronological months of health scores and explanations.
*   Document confirmation applies incremental additions directly to `MonthlyRecord` fields.

---

## 4. Recommended Proposed Policies

### 4.1. Month-Boundary Cutover with Dual-Authority Modes
*   Introduce `data_mode` (`MONTHLY_SNAPSHOT` vs `TRANSACTION_LEDGER`) and `ledger_cutover_month` (e.g., `2026-11`) on the business profile.
*   **Historical Invariant:** Months strictly prior to `ledger_cutover_month` are governed by `MONTHLY_SNAPSHOT`. They remain immutable manual snapshots.
*   **Ledger Invariant:** Months $\ge \text{ledger\_cutover\_month}$ are governed by `TRANSACTION_LEDGER`. Their core `MonthlyRecord` values are deterministically derived by `MonthlyAggregationService`.
*   Cutover operates strictly on month boundaries (`YYYY-MM-01`). Mid-month cutover is disallowed.

### 4.2. Opening Balance Semantics (M1: Single Authority)
*   When creating or initializing a financial account, an `openingBalance` and `openingDate` are specified.
*   **Revenue & Cash Flow Neutrality:** Opening balances increase account starting cash and contribute to ending cash balance (`cashBalanceEom`), but **contribute 0.00 to period `revenue` and 0.00 to period `cashInflow`**.
*   **Single Master:** Account opening balance is stored once in `financial_accounts`. The cached `currentBalance` equals opening balance plus sum of subsequent posted movements. No duplicate initialization movement is generated.

### 4.3. Multi-Month Cash Balance Propagation (M5: Propagation)
*   When a backdated transaction is posted into month $M$:
    *   Month $M$'s ending cash (`cashBalanceEom`) is recomputed.
    *   This cash balance delta is carried forward to all subsequent months $M+1, M+2, \dots$ up to the current active month.
    *   *Distinction:* Health score recalculation spans a rolling 6-month window (`recalculateAffectedScores`), whereas cash balance carry-forward propagates indefinitely across all subsequent months.

### 4.4. Hybrid Maintenance for Non-Derivable Declarations (M8: Declarations)
*   In ledger mode, core financial inputs (`revenue`, `operatingExpenses`, `cashInflow`, `cashOutflow`, `cashBalanceEom`) are locked against direct manual editing.
*   Optional fields not derivable from ledger transactions (e.g., `loanOutstanding`, `interestExpense`, `financingType`, compliance declarations) remain editable via a dedicated endpoint (`PATCH /api/records/monthly/{month}/declarations`).
*   Missing optional fields remain explicitly `null` (unknown), allowing `ScoringService` to normalize weights properly rather than fabricating zero.

### 4.5. Atomic Rejection of Derived Negative Values (M6: Negative Compatibility)
*   If a backdated reversal or refund causes total derived monthly revenue or cash inflow to drop below `0.00`:
    *   The posting is rejected atomically with HTTP `422 Unprocessable Entity` (`ERROR_DERIVED_NEGATIVE_UNSUPPORTED`).
    *   Silent clamping of negative derived figures to `0.00` is strictly **forbidden**, as it hides financial discrepancies and breaks ledger reconciliation.

---

## 5. Alternatives Considered

| Alternative | Evaluation & Trade-offs | Reason for Rejection |
| :--- | :--- | :--- |
| **1. Immediate Global Migration to Full Double-Entry** | Require all historical months to be backfilled with synthetic debits and credits. | High risk of data corruption; full double-entry accounting is explicitly deferred in Phase 2 scope. |
| **2. Mid-Month Cutover** | Allow cutover on any arbitrary day (e.g. 15 October), summing partial manual and partial ledger data. | Impossible to audit reliably; creates split-authority records and reconciliation discrepancies. |
| **3. Permanent Total Lock on MonthlyRecord** | Disallow all manual edits to `MonthlyRecord` once ledger mode is enabled. | Leaves optional compliance and financing declarations stranded, degrading health score accuracy. |
| **4. Silent Zero-Clamping for Negative Derived Totals** | If refunds or reversals exceed monthly income, clamp derived monthly revenue to zero. | Hides financial reality and creates invisible ledger reconciliation discrepancies. Domain errors or administrative adjustments must be surfaced explicitly. |

---

## 6. Unresolved Decisions & Cross-Team Review

The following items require cross-review before implementation:

1.  **Invoice Issuance vs. Revenue Recognition (with Fatima):**
    *   *Question:* When an invoice is issued in Fatima's F04 module, is revenue recognized immediately (accrual basis), or only upon cash settlement (cash basis)?
    *   *Recommendation:* Accrual for revenue upon invoice issuance; cash inflow recognized only upon settlement.
2.  **Inventory Valuation & COGS Integration (with Fatima):**
    *   *Question:* Does F08 post real-time COGS transactions per order, or an end-of-month inventory adjustment journal?
3.  **Module Registry Identifiers (with Fatima):**
    *   *Question:* What is the formal capability string key for accounting in Fatima's C03 Module Resolver?

---

## 7. Consequences & Implementation Impact

### Backend Impact
*   Requires `MonthlyAggregationService` to idempotently recompute monthly totals and propagate cash forward.
*   Requires guarding existing `POST /api/records/monthly` to reject direct edits for ledger-governed months.
*   Requires pessimistic locking and durable idempotency storage.

### Frontend Impact
*   Codex must render ledger-derived monthly records as read-only with a visual badge (`Derived from Ledger`).
*   The frontend must provide distinct input flows for Account Opening Balances vs. Direct Revenue Receipts.
*   Optional declaration inputs must remain accessible via the dedicated supplemental endpoint.
