# FinSight Phase 2 — Monthly Ledger Cutover Phase A Architecture & Specification
## Canonical Ledger Monthly Projection, Financial Source Reconciliation and Cutover Readiness (C02 Phase A)

**Document Version:** `1.1`
**Date:** 29 October 2026
**Status:** `INTEGRATION_VERIFIED (Phase A Read-Only Foundation & Cutover Policy Approved for Checkpoint)`
**Domain Authority:** Developer A Backend (Lead Financial Systems Architect & Engineer)
**Target Release:** FinSight Phase 2
**Database Migration Status:** **0 Flyway Migrations in Phase A** (Flyway head remains `V29__create_recurring_transactions.sql`; `V30` reserved for Developer B F05; Phase B cutover migration reserved as `V31+`)

---

## 1. Executive Summary & Objective

In FinSight MVP, monthly financial data was recorded through coarse monthly declarations (`MonthlyRecord`), storing high-level summary figures such as `revenue`, `cashInflow`, `cashOutflow`, `operatingExpenses`, `accountsReceivable`, and `accountsPayable`. These inputs directly drove the canonical `health-score-v1` algorithm (30% Cash Flow Stability, 25% Profitability, 20% Repayment, 15% Trend, 10% Compliance).

Phase 2 introduces granular, event-driven, double-entry-aligned accounting primitives:
*   Multi-currency financial accounts (`FinancialAccount`) and immutable directional movements (`AccountMovement`).
*   Transaction posting (`FinancialTransaction`) with direct receipts, expense payments, internal transfers, and compensating reversals (`TransactionReversal`).
*   Authoritative receivables and payables obligations (`FinancialObligation`), payment settlements, and multi-obligation allocations (`PaymentAllocation`).
*   Customer and supplier advances (`CounterpartyAdvance`), advance applications, and application reversals.
*   Persistent record drafts (`RecordDraft`) and scheduled recurring occurrence projections (`RecurringRuleOccurrence`).

**Task A-C02-MONTHLY-LEDGER-CUTOVER-PHASE-A** establishes the read-only projection engine and source-of-truth reconciliation bridge without mutating production data or performing premature live score cutover.

### Key Deliverables Completed:
1.  **Exhaustive 14-Metric Source-of-Truth Mapping:** Formal matrix specifying whether each financial metric is ledger-authoritative, accrual-based, point-in-time, supplemental, or unintegrated.
2.  **Earned Revenue Provenance & Accrual Engine:** Implemented strict separation between verified direct sale receipts, approved invoice-backed earned revenue (`CUSTOMER_INVOICE`), provisional unprovenanced receivable candidates (`MANUAL_DECLARATION`), and excluded balance-sheet items (`OPENING_BALANCE`, `APPROVED_ADJUSTMENT`).
3.  **Deterministic Projection Engine (`MonthlyLedgerProjectionService`):** Computes monthly cash flow, recognized revenues/expenses, multi-currency opening/closing positions, and obligation aging strictly from canonical posted movements. Accounts for unclassified cash transactions with partial completeness flags.
4.  **Shadow Comparison Engine (`compareWithMonthlyRecord`):** Computes side-by-side reconciliation between historical `MonthlyRecord` entries and ledger projections, with explicit recognition basis classifications.
5.  **Strict Profitability Discipline (No Zero Substitution):** Rejects zero substitution for unknown COGS. Marks COGS, Gross Profit, Operating Profit, and Net Profit as strictly `CapabilityStatus.UNAVAILABLE` with clear diagnostic measure `revenueLessKnownOperatingExpensesBeforeCogs`.
6.  **Read-Only REST Endpoints:** Exposes `GET /api/records/monthly/projection` and `GET /api/records/monthly/comparison` protected by server-side business context and `FINANCIAL_DATA_READ`.
7.  **Verified PostgreSQL Integration Suite:** 9 integration tests covering Fixtures A–G, real `MonthlyRecord` comparison, and 12-table SHA-256 value-level snapshots proving zero database mutations.
8.  **Production Cutover Policy Proposal (Phase B):** Actionable recommendations for cutover granularity, historical immutability, missing COGS strategy, and migration sequencing.

---

## 2. Financial Source-of-Truth Reconciliation Matrix

The transition from user-declared monthly summaries to ledger-derived accounting exposes fundamental differences in accounting definitions. The matrix below defines the authoritative source, basis, and reconciliation rules for all 14 financial metrics:

| Metric | Historical `MonthlyRecord` Source | Canonical Phase 2 Ledger Source | Recognition Basis | Reconciliation / Variance Rule |
| :--- | :--- | :--- | :--- | :--- |
| **Cash Inflow** | Self-reported monthly cash deposits | Sum of `DIRECT_SALE_RECEIPT` + `RECEIVABLE_COLLECTION` + Cash Advances + Unclassified Inflows (net of reversals) | `CASH_FLOW` | Excludes internal account transfers. Mismatches represent unrecorded cash transactions or untracked bank deposits. |
| **Cash Outflow** | Self-reported monthly cash disbursements | Sum of `OPERATING_EXPENSE_PAYMENT` + `PAYABLE_SETTLEMENT` + Supplier Advance Outflows + Unclassified Outflows (net of reversals) | `CASH_FLOW` | Excludes internal account transfers. Mismatches represent untracked cash expenditures or manual adjustments. |
| **Net Cash Flow** | `cashInflow - cashOutflow` | `totalCashInflow - totalCashOutflow` | `CASH_FLOW` | Pure mathematical subtraction of validated operational flows. |
| **Opening Cash Balance** | Not tracked (only current bank balance recorded) | Opening balance of accounts as of day 1 of month + net posted movements prior to month start | `POINT_IN_TIME_BALANCE` | Point-in-time cash position. Pre-opening accounts return 0.00 until their opening date. |
| **Closing Cash Balance** | Self-reported bank/cash balance at time of entry | Opening cash balance as of day 1 + net operational movements in month + net starting balance adjustments | `POINT_IN_TIME_BALANCE` | Exact point-in-time position as of end of month. Holds: $\text{Opening} + \text{Adj} + \text{Inflows} - \text{Outflows} = \text{Closing}$. |
| **Accounts Receivable** | Self-reported outstanding customer balance | Total remaining unsettled balance on `RECEIVABLE` obligations issued $\le$ month end, net of settlements and applied customer advances | `POINT_IN_TIME_BALANCE` | Ledger uses aging engine as of month-end date. Differences arise when customers pay after month-end or historical declarations included unbilled estimates. |
| **Accounts Payable** | Self-reported outstanding supplier balance | Total remaining unsettled balance on `PAYABLE` obligations issued $\le$ month end, net of settlements and applied supplier advances | `POINT_IN_TIME_BALANCE` | Ledger uses aging engine as of month-end date. Differences arise when suppliers are paid after month-end or disputed bills are excluded. |
| **Customer Advances** | Not tracked in MVP | Sum of active unapplied customer advances (`CounterpartyAdvance` with direction `RECEIVABLE`) | `POINT_IN_TIME_BALANCE` | Liability on balance sheet (unearned revenue). Collected in cash but not yet recognized as revenue. |
| **Supplier Advances** | Not tracked in MVP | Sum of active unapplied supplier advances (`CounterpartyAdvance` with direction `PAYABLE`) | `POINT_IN_TIME_BALANCE` | Asset on balance sheet (prepaid expense). Disbursed in cash but not yet recognized as expense. |
| **Revenue** | Self-reported monthly sales revenue | **Three-Tier Provenance Model:**<br>1. *Verified Direct Sales:* Cash sales receipts.<br>2. *Authoritative Accrual Revenue:* Direct sales + confirmed customer invoices (`CUSTOMER_INVOICE`).<br>3. *Provisional Candidates:* Unprovenanced manual obligations (`MANUAL_DECLARATION`). | `ACCRUAL_ACCOUNTING` (Primary)<br>`CASH_FLOW` (Secondary) | MVP conflated cash receipts with revenue. The ledger cleanly separates cash collected from earned revenue upon invoice issuance. Manual unprovenanced obligations are isolated as provisional candidates. |
| **Cost of Goods Sold (COGS)** | Self-reported monthly direct cost | **Status: UNAVAILABLE** (`CapabilityStatus.UNAVAILABLE`). Owned by Developer B (Fatima F08). **Zero substitution is strictly rejected.** | `UNINTEGRATED_MODULE` | Neither calculated nor fabricated as zero. Explicit machine reason returned until F08 is integrated into ledger. |
| **Operating Expenses (Opex)** | Self-reported monthly overhead | Net `OPERATING_EXPENSE_PAYMENT` transactions + confirmed supplier bills (`SUPPLIER_BILL`) | `CASH_FLOW` / `ACCRUAL_ACCOUNTING` | Excludes unpaid bills lacking bill provenance. Accrual opex includes confirmed bills issued in month. |
| **Gross Profit** | `revenue - cogs` | **Status: UNAVAILABLE** (`CapabilityStatus.UNAVAILABLE`). Dependent on COGS. | `UNINTEGRATED_MODULE` | Cannot be computed until COGS (F08) is integrated into ledger. Never substituted with revenue. |
| **Diagnostic Operating Measure** | N/A | `revenueLessKnownOperatingExpensesBeforeCogs` = Authoritative Accrual Revenue − Authoritative Accrual Opex | `ACCRUAL_ACCOUNTING` (Diagnostic) | Explicitly labeled as preliminary diagnostic value excluding COGS, financing, and tax. Not labeled EBIT, EBITDA, or Operating Profit. |

---

## 3. Projection Engine Architecture (`MonthlyLedgerProjectionService`)

The projection engine is implemented in [`MonthlyLedgerProjectionService.java`](file:///c:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/records/projection/service/MonthlyLedgerProjectionService.java).

### Architectural Invariants:
1.  **Asia/Karachi Calendar Boundaries:** Month boundaries are computed using `YearMonth.parse(monthStr)`:
    *   `startDate = ym.atDay(1)` (e.g. `2026-10-01`)
    *   `endDate = ym.atEndOfMonth()` (e.g. `2026-10-31`)
    *   `asOfDate` allows retrospective point-in-time calculation (clamped to `endDate`).
2.  **Compensating Reversal Netting:** Reversal transactions are identified by `reversedTransactionId != null`. Gross amounts and reversal amounts are aggregated separately:
    $$\text{Net Operating Outflows} = \text{Gross Opex} - \text{Reversed Opex}$$
    $$\text{Net Direct Sales} = \text{Gross Direct Sales} - \text{Reversed Direct Sales}$$
3.  **Transfer Neutrality:** Transactions with `eventType == INTERNAL_TRANSFER` post dual account movements (one DECREASE on source, one INCREASE on destination). The projection engine verifies transfer neutrality:
    $$\text{Transfers In} - \text{Transfers Out} = 0.00 \quad (\text{Net Cash Flow Impact} = 0)$$
    Individual accounts reflect transfer liquidity shifts, while total business operational cash flow is unaffected.
4.  **Multi-Currency Isolation:** Each account is evaluated in its native ISO currency with currency-specific scale (PKR: 2, USD: 2, JPY: 0, BHD: 3). Foreign currency accounts are aggregated per-currency into `currencySummaries`. **No unapproved synthetic FX blending or conversion is performed.**
5.  **Exclusion of Unposted Estimates:** Persistent drafts (`RecordDraft` with `status == DRAFT`) and scheduled recurring occurrences (`RecurringRuleOccurrence` with `status IN (SCHEDULED, DRAFT_READY)`) are aggregated into `MonthlyDraftSummary` with `isEstimate: true`. They are strictly excluded from actual cash flows, recognized figures, and closing cash balances.
6.  **Unclassified Cash Completeness:** Any posted cash transaction with null or unrecognized `eventType` is captured as `unclassifiedCashInflows` or `unclassifiedCashOutflows`. It is fully included in account cash reconciliation, flagged with warnings, and sets projection completeness to `DataCompleteness.PARTIAL`.
7.  **Traceability & Audit Evidence:** The response includes structured `evidence` items referencing every financial transaction and financial account contributing to the projection.

---

## 4. Shadow Comparison Engine & Taxonomy

The shadow comparison endpoint compares the historical `MonthlyRecord` against the ledger projection for the same month:

### Comparison Status Taxonomy:
*   `MATCH`: Historical declared value equals the ledger projected value (e.g. exact cash inflow).
*   `DIFFERENCE`: Both sources exist but have non-zero variance. The response details `recordValue`, `ledgerValue`, `difference`, and `explanation`.
*   `NOT_SUPPORTED_IN_LEDGER`: Metric declared in historical record cannot yet be derived from ledger (e.g. COGS pending Fatima F08).
*   `NOT_PRESENT_IN_RECORD`: Metric is authoritatively computed by ledger but was not tracked in MVP monthly record (e.g. Customer/Supplier Advances, Net Operating Working Capital).

### Recognition Basis Taxonomy:
*   `CASH_FLOW`: Direct cash settlement or bank movement.
*   `ACCRUAL_ACCOUNTING`: Obligation issued or accrued independent of cash receipt/disbursement.
*   `POINT_IN_TIME_BALANCE`: Point-in-time snapshot as of end of calendar month.
*   `SUPPLEMENTAL_DECLARATION`: Declarative metric not derivable from posted double-entry events.
*   `UNINTEGRATED_MODULE`: Functional domain owned by external module (e.g. Fatima F08 Inventory/COGS).

---

## 5. REST API Specification

### 1. Monthly Ledger Projection
*   **Path:** `GET /api/records/monthly/projection`
*   **Query Parameters:**
    *   `month` (Required): Target month in `YYYY-MM` format (e.g. `2026-10`).
    *   `asOfDate` (Optional): Historical point-in-time cutoff in `YYYY-MM-DD` format (defaults to end of month).
*   **Authorization:** Active session, active business context, `FINANCIAL_DATA_READ` permission.
*   **Response Payload:** [`MonthlyLedgerProjectionResponse`](file:///c:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/records/projection/dto/MonthlyLedgerProjectionResponse.java)
    *   `cashFlow`: [`MonthlyOperatingCashFlow`](file:///c:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/records/projection/dto/MonthlyOperatingCashFlow.java) (inflows, outflows, unclassified flows, transfers, net operational cash)
    *   `recognizedFigures`: [`MonthlyRecognizedFigures`](file:///c:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/records/projection/dto/MonthlyRecognizedFigures.java) (verified direct sales, approved invoices, provisional candidates, authoritative accrual revenue/opex, diagnostic pre-COGS measure, COGS/profit availability)
    *   `obligationPositions`: [`MonthlyObligationPositions`](file:///c:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/records/projection/dto/MonthlyObligationPositions.java) (unsettled AR/AP, customer/supplier advances, working capital)
    *   `accountPositions`: List of [`AccountMonthlyCashPosition`](file:///c:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/records/projection/dto/AccountMonthlyCashPosition.java)
    *   `currencySummaries`: List of [`CurrencyMonthlyCashSummary`](file:///c:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/records/projection/dto/CurrencyMonthlyCashSummary.java)
    *   `draftSummary`: [`MonthlyDraftSummary`](file:///c:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/records/projection/dto/MonthlyDraftSummary.java)
    *   `evidence`: List of [`FinancialEvidenceReference`](file:///c:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/records/projection/dto/FinancialEvidenceReference.java)

### 2. Monthly Shadow Comparison
*   **Path:** `GET /api/records/monthly/comparison`
*   **Query Parameters:** Same as projection (`month`, optional `asOfDate`).
*   **Authorization:** Active session, active business context, `FINANCIAL_DATA_READ` permission.
*   **Response Payload:** [`MonthlyShadowComparisonResponse`](file:///c:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/records/projection/dto/MonthlyShadowComparisonResponse.java)
    *   `hasMonthlyRecord`: Boolean indicating if historical record exists for the month.
    *   `metrics`: Keyed map of [`MonthlyMetricComparison`](file:///c:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/records/projection/dto/MonthlyMetricComparison.java) for all 14 metrics.
    *   `projection`: Full underlying ledger projection response.
    *   `readiness`: Assessment of whether variance is within acceptable tolerance for production cutover.

---

## 6. Integration Verification & Evidence (Fixtures A–G & Invariants)

Execution against disposable PostgreSQL 14.22 with Flyway V29 schema in [`MonthlyLedgerProjectionPostgreSqlIT.java`](file:///c:/Users/PC/Projects/sme-financial-webapp/backend/src/test/java/com/app/sme_health_backend/records/projection/MonthlyLedgerProjectionPostgreSqlIT.java):

```
[INFO] Running Monthly Ledger Projection Real PostgreSQL & Authenticated HTTP Integration Tests (C02 Phase A)
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 19.97 s
```

### Verified Fixtures:
1.  **Fixture A (Basic Monthly Cash Flow):**
    *   Setup: Account opened on 2026-09-01 with 100k PKR. Transactions in October: +30k direct sale, -8k opex, 10k internal transfer between accounts.
    *   Verification: Inflows = 30k, Outflows = 8k, Net Cash = 22k, Transfers = 10k (Net 0k), Closing Cash = 122,000.00 PKR.
2.  **Fixture B (Reversals & Correction Handling):**
    *   Setup: Initial 8k opex payment, followed by full compensating reversal of 8k.
    *   Verification: Gross opex = 8k, Reversed opex = 8k, Net opex = 0.00, Total outflows = 0.00, Closing Cash = 130,000.00 PKR.
3.  **Fixture C (Excluded Drafts & Recurring Occurrences):**
    *   Setup: 1 unposted manual draft of 50k PKR + 1 scheduled recurring rule of 25k generating a due occurrence draft.
    *   Verification: Actual cash inflows = 0.00, Actual cash outflows = 0.00, Actual closing balance = 100,000.00 PKR. Draft summary reports 75,000.00 PKR as an unposted estimate (`isEstimate: true`).
4.  **Fixture D (Receivables, Payables, Advances, and Accrual vs Cash Revenue):**
    *   Setup: 30k direct cash sale + 50k verified customer invoice (`CUSTOMER_INVOICE`) + 20k partial cash settlement + 10k customer cash advance + 5k advance applied to invoice.
    *   Verification: Cash Inflow = 60k (30k direct + 20k settlement + 10k advance). Cash Revenue = 30k (direct sales only). Accrual Revenue = 80k (30k direct sales + 50k invoice). Outstanding AR as of Oct 31 = 25,000.00 PKR. Unapplied customer advance = 5,000.00 PKR.
5.  **Fixture E (Multi-Currency Isolation):**
    *   Setup: PKR account with 100k balance + USD account with 5k balance.
    *   Verification: Two distinct `CurrencyMonthlyCashSummary` records returned (PKR 100,000.00 and USD 5,000.00). Zero unapproved blending or synthetic FX conversion.
6.  **Fixture F (Historical Boundary Integrity):**
    *   Setup: Transactions on 2026-08-31 (+15k), 2026-09-15 (+30k), and 2026-10-01 (+20k).
    *   Verification: September projection opening balance = 115,000.00 PKR. September cash inflows = 30,000.00 PKR. September closing balance = 145,000.00 PKR. Prior and subsequent month transactions do not pollute September flows.
7.  **Fixture G (Non-Revenue Obligations & Provenance Uncertainty):**
    *   Setup: 30k direct cash receipt + 50k verified invoice (`CUSTOMER_INVOICE`) + 20k manual declaration (`MANUAL_DECLARATION`) + 100k opening balance receivable (`OPENING_BALANCE`).
    *   Verification: Verified direct sales = 30k, Invoice-backed earned revenue = 50k, Authoritative accrual revenue = 80k. Provisional candidates = 20k, Provisional total = 100k. Authoritative revenue completeness = `PARTIAL`. Total balance sheet AR = 170k (capturing all 3 open debts while excluding non-sales from earned revenue).
8.  **Shadow Comparison with Real MonthlyRecord:**
    *   Verification: Matches `revenue` (30k), detects difference on `cashOutflow` (10k declared vs 8k ledger), flags `cogs` as `NOT_SUPPORTED_IN_LEDGER`, and flags `customerAdvances` as `NOT_PRESENT_IN_RECORD`.
9.  **Read-Only Database Invariant (SHA-256 Snapshots):**
    *   Verification: Full value-level SHA-256 digest computed across 12 core tables (`monthly_records`, `financial_accounts`, `financial_transactions`, `account_movements`, `financial_obligations`, `payment_allocations`, `counterparty_advances`, `record_drafts`, `recurring_rules`, `recurring_rule_occurrences`, `counterparties`, `security_audit_events`) before and after executing projection and shadow comparison.
    *   Result: `sha256Before == sha256After` — strictly 0 database mutations occurred.

---

## 7. Final Phase B Production Cutover Policy Recommendations

The findings from Phase A demonstrate that a blunt, platform-wide cutover would disrupt historical health scores and produce accounting discrepancies. The following 7 finalized policies govern Phase B:

### Policy 1: Granular Per-Business Cutover Date (`cutover_month`)
*   Add a nullable `ledger_cutover_month` (`VARCHAR(7)`) column to the business configuration table.
*   Prior to `ledger_cutover_month`, `health-score-v1` continues consuming historical `monthly_records`.
*   Starting from `ledger_cutover_month` onwards, health score calculation consumes ledger-projected figures.
*   Enables SMEs to cut over at their own pace once opening balances and bank feeds are reconciled.

### Policy 2: Legacy History & Score Immutability
*   Historical `monthly_records` and previously computed `health_score_records` must **never** be retroactively overwritten, recalculated, or backfilled.
*   Preserves audit compliance and historical trend stability. Any subsequent restatement requires a separately authorized, audited administrative workflow.

### Policy 3: Monthly Source Precedence Post-Cutover
*   Post-cutover, canonical posted transactions and obligations are authoritative.
*   The Records workspace must disable manual numeric editing of cash inflow, outflow, revenue, and receivables for post-cutover months.
*   Missing components must not be silently pulled from legacy records or replaced with synthetic defaults.

### Policy 4: Unintegrated COGS Strategy (Fatima F08 Dependency)
*   **Zero substitution is strictly rejected.** Unknown COGS is not zero COGS.
*   Until Developer B's F08 (Inventory Valuation & COGS) is integrated into ledger movements:
    *   *Option A (Recommended):* Implement a designated supplemental monthly COGS declaration (`MonthlyRecordSupplement` PATCH endpoint) requiring explicit source provenance, period, currency, user authorization, and audit logging.
    *   *Option B:* Mark profitability-dependent metrics (`cogs`, `grossProfit`, `operatingProfit`, `netProfit`) as `CapabilityStatus.UNAVAILABLE`, preserving supported cash-flow and liquidity metrics without distorting profitability scores.

### Policy 5: Comprehensive Activation Readiness Assessment
*   Do not require exactly zero variance between legacy `MonthlyRecord` amounts and ledger projections, because different recognition bases (cash vs accrual) create legitimate, expected differences.
*   Instead, define a formal 9-point cutover readiness evaluation:
    1.  *Account Balance Verification:* All bank and cash accounts reconciled with verified opening balances.
    2.  *Transaction Coverage:* Active transactions recorded across the entire target month without gaps.
    3.  *Bank Reconciliation Status:* Bank statement feeds imported and reconciled via F12A engine.
    4.  *Zero Unclassified Events:* `unclassifiedCashInflows` and `unclassifiedCashOutflows` equal 0.00.
    5.  *Obligation & Advance Provenance:* Invoices and bills carry verified source provenance (`CUSTOMER_INVOICE`, `SUPPLIER_BILL`).
    6.  *Score-Input Completeness:* Cash flow stability and repayment inputs complete.
    7.  *Explicit Handling of Unsupported Metrics:* Acknowledged status for unintegrated COGS/profitability.
    8.  *Explicit Owner Acknowledgement:* SME Owner/Accountant formally confirms ledger cutover via UI dialog.
    9.  *Historical Data Protection:* Pre-cutover records sealed and locked from mutation.

### Policy 6: Late Changes, Backdated Reversals, and Closed-Month Corrections
*   Discrepancies identified during shadow comparison must **not** be resolved by directly updating database rows.
*   Corrections must follow accounting discipline: post compensating transactions or use the existing `TransactionReversal` mechanism, preserving audit provenance.
*   Backdated entries posted to closed historical months update the historical ledger projection deterministically, but do not automatically recalculate closed historical health scores unless restatement is explicitly triggered.

### Policy 7: Migration Sequencing (V30 vs. V31+)
*   Developer B (Fatima) has globally reserved **`V30`** for `F05 Project Planning & Budgeting (Phase A)`.
*   Developer A has authored **0 migrations** in C02 Phase A.
*   The Phase B cutover migration (adding `ledger_cutover_month` to business configuration) is reserved as **`V31+`**, strictly contingent upon Developer B merging V30.
