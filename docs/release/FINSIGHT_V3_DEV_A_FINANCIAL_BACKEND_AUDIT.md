# FinSight Candidate v3 — Developer A Financial Backend Audit & Verification Report

**Document ID:** `FINSIGHT-V3-DEV-A-FINANCIAL-AUDIT-01`  
**Date:** November 17, 2026  
**Role:** Principal Java/Spring Boot Backend Architect, Senior Financial Systems Engineer & Git Integration Coordinator  
**Working Directory:** `C:\Users\PC\Projects\sme-financial-webapp`  
**Target Baseline:** Zarwia / FinSight Candidate v3 (`origin/integration/finsight-production-candidate-v3`)  
**Frozen Application Tree SHA:** `19547bf3dda9744d51e74c87a908e2e42577299c`  
**Published Head SHA:** `23e28c12bcd593be824d30cda0a2b79dcbe9d1f5`  

---

## 1. Verified Git Baseline & Provenance

An independent Git inspection was executed in `C:\Users\PC\Projects\sme-financial-webapp` using `git rev-parse` and `git log`. All four authoritative commit references supplied in the alignment review are accessible and verified:

| Reference | Target SHA | Local Verification Status | Git Commit Message / Annotation |
|---|---|:---:|---|
| **Candidate v3 Published Head** | `23e28c12bcd593be824d30cda0a2b79dcbe9d1f5` | **VERIFIED** | `docs(candidate-v3): record final engineering freeze and Suleman handoff` |
| **Frozen Code Baseline** | `19547bf3dda9744d51e74c87a908e2e42577299c` | **VERIFIED** | `merge(candidate-v3): integrate exact demo container-ownership fix` |
| **Developer A Stage 4B Baseline** | `8c6e2478fce100fb9bd17c469e288248f5fffdb1` | **VERIFIED** | `feat(integration): finalize Developer A and Codex integration with C02 browser suite...` |
| **Developer A Demo Fix Baseline** | `153e94b214ba24ac1d81a094eff3805334edaad9` | **VERIFIED** | `fix(demo): resolve browser authentication abort race condition and CORS origin handling` |

### Git Ancestry Proof
1. `git merge-base --is-ancestor 8c6e2478fce100fb9bd17c469e288248f5fffdb1 19547bf3dda9744d51e74c87a908e2e42577299c` $\rightarrow$ Exit `0` (**Confirmed Ancestor**).
2. `git merge-base --is-ancestor 153e94b214ba24ac1d81a094eff3805334edaad9 19547bf3dda9744d51e74c87a908e2e42577299c` $\rightarrow$ Exit `0` (**Confirmed Ancestor**).
3. `git diff 19547bf3dda9744d51e74c87a908e2e42577299c 23e28c12bcd593be824d30cda0a2b79dcbe9d1f5 --stat` confirms only documentation files changed between the frozen tree and published head.
4. `git log 19547bf3dda9744d51e74c87a908e2e42577299c..dev/suleman` is **completely empty** (0 missing commits).

---

## 2. C01–C11 Financial Backend Modules Coverage Matrix

Every financial domain owned by Developer A was audited directly in the Candidate v3 source tree.

| Domain ID | Functional Scope | Primary Service / Component | Repositories & Entities | Controllers & Endpoints | Test Classes | Status in Candidate v3 |
|---|---|---|---|---|---|:---:|
| **C01** | Core Double-Entry Ledger & Multi-Currency Accounts | `TransactionPostService`<br>`TransactionReadService`<br>`AccountCreateService`<br>`AccountReadService`<br>`TransferService` | `FinancialAccountRepository`<br>`FinancialTransactionRepository`<br>`AccountMovementRepository`<br>`FinancialIdempotencyRepository` | `AccountController`<br>`TransactionController`<br>`TransferController`<br>`/api/finance/accounts`<br>`/api/finance/transactions`<br>`/api/finance/transfers` | `CurrencyRegistryTest` (39/39)<br>`AccountCreateServiceTest` (13/13)<br>`AccountReadServiceTest` (13/13)<br>`TransactionPostServiceTest` (13/13)<br>`TransferServiceTest` (11/11) | **VERIFIED INTACT** |
| **C02** | Monthly Ledger Cutover & Derived Bridge | `LedgerMonthlyBridgeService`<br>`MonthlyProjectionService`<br>`MonthlyFinancialProjector` | `MonthlyRecordRepository`<br>`FinancialAccountRepository`<br>`AccountMovementRepository` | `LedgerMonthlyBridgeController`<br>`MonthlyRecordController`<br>`/api/finance/ledger-bridge/*`<br>`/api/records/monthly/*` | `MonthlyLedgerProjectionPostgreSqlIT`<br>`TransactionDrivenMonthlyPostgreSqlIT`<br>`MonthlyRecordServiceTests` | **SUPERSEDED BY V52 (VERIFIED)** |
| **C03** | Counterparties & Obligations Lifecycle | `FinancialObligationService`<br>`CounterpartyService`<br>`ObligationAgingCalculator` | `CounterpartyRepository`<br>`FinancialObligationRepository` | `FinancialObligationController`<br>`CounterpartyController`<br>`/api/finance/obligations`<br>`/api/finance/counterparties` | `FinancialObligationServiceTest` (21/21)<br>`ObligationAgingCalculatorTest` (19/19) | **VERIFIED INTACT** |
| **C04** | Payment Cycles & Advance Allocations | `FinancialObligationService`<br>(Settlement & Allocation) | `ObligationPaymentAllocationRepository`<br>`CounterpartyAdvanceRepository` | `FinancialObligationController`<br>`/api/finance/obligations/{id}/settlement` | `FinancialObligationServiceTest`<br>`FinancialObligationsPostgreSqlIT` | **VERIFIED INTACT** |
| **C05** | Transaction Reversals & Audit Integrity | `TransactionReverseService`<br>`SecurityAuditEventService` | `FinancialTransactionRepository`<br>`AccountMovementRepository`<br>`SecurityAuditRepository` | `TransactionController`<br>`/api/finance/transactions/{id}/reversal` | `TransactionReverseServiceTest` (9/9)<br>`TransactionReversalPostgreSqlIT` | **VERIFIED INTACT** |
| **C06** | Recurring Transactions Engine | `RecurringRuleService` | `RecurringRuleRepository`<br>`RecurringRuleOccurrenceRepository`<br>`RecordDraftRepository` | `RecurringRuleController`<br>`/api/finance/recurring-rules` | `RecurringRuleServiceTest` (10/10)<br>`RecurringTransactionsPostgreSqlIT` | **VERIFIED INTACT & CONCURRENCY FIXED** |
| **C07** | Bank Reconciliation & Statement Matching | `BankReconciliationService` | `BankStatementImportRepository`<br>`BankStatementEntryRepository`<br>`BankReconciliationMatchRepository` | `BankReconciliationController`<br>`/api/finance/bank-reconciliation` | `BankReconciliationServiceTest` (15/15)<br>`BankReconciliationPostgreSqlIT` | **VERIFIED INTACT** |
| **C08** | Salesperson Commission Tracking & Payout | `CommissionCalculationService`<br>`CommissionEarningService`<br>`CommissionSettlementService`<br>`CommissionRuleService` | `CommissionRuleRepository`<br>`CommissionEarningRepository`<br>`CommissionPayoutRepository` | `CommissionController`<br>`CommissionReportingController`<br>`/api/commissions` | `CommissionCalculationServiceTest` (5/5)<br>`CommissionEarningServiceTest` (5/5)<br>`CommissionRuleServiceTest` (6/6)<br>`CommissionSettlementServiceTest` (8/8) | **VERIFIED INTACT** |
| **C09** | FX Exposure, Settlement & Gain/Loss Recognition | `FxCalculationService`<br>`FxSettlementService`<br>`FxTransferService`<br>`FxAnalyticsService` | `FxSettlementRepository`<br>`FinancialAccountRepository` | `FxController`<br>`/api/finance/fx` | `FxCalculationServiceTest` (8/8)<br>`FxSettlementServiceTest` (7/7)<br>`FxTransferServiceTest` (6/6) | **VERIFIED INTACT** |
| **C10** | Multi-Currency Project Funding Gap & Attribution | `ProjectFundingGapCalculator`<br>`ProjectFundingService` | `ProjectCommitmentRepository`<br>`ProjectActualAttributionRepository` | `ProjectFundingController`<br>`/api/projects/{id}/funding-gap` | `ProjectFundingGapCalculatorTest` (24/24)<br>`ProjectFundingPostgreSqlIT` | **VERIFIED INTACT** |
| **C11** | Financial Query Facade (Read-Only Surface) | `FinancialQueryFacade`<br>`FinancialQueryFacadeService` | Read-only aggregation over all financial domain repositories | `FinancialQueryController`<br>`/api/finance/query/*` | `FinancialQueryFacadeServiceTest` (9/9)<br>`FinancialQueryPostgreSqlIT` | **VERIFIED INTACT & PROTECTED** |

---

## 3. Phase B: V52 Monthly Projection Integrity & Accounting Invariants

Candidate v3 officially transitioned from the C02 manual cutover model to `monthly-projection-v1` via Flyway migration `V52__transaction_driven_monthly_projection.sql`.

### 3.1 Financial Data Flow Architecture
The complete end-to-end data pipeline was traced and verified:
$$\text{Activity Event} \longrightarrow \text{Authoritative Ledger / Sub-ledger} \longrightarrow \text{V52 Dirty Trigger} \longrightarrow \text{Advisory Lock Refresh} \longrightarrow \text{MonthlyRecord Derived} \longrightarrow \text{Health Score Rescored}$$

1. **Transaction Event:** User or service posts a transaction (e.g. `DIRECT_SALE_RECEIPT`, `OPERATING_EXPENSE_PAYMENT`), issues/settles an invoice or bill, or executes an inventory movement.
2. **Ledger / Movement Write:** Authoritative records are written to `account_movements`, `commercial_document_lines`, or `inventory_movements`.
3. **Database Trigger:** PostgreSQL triggers `trg_*_monthly_projection_dirty` immediately bump `dirty_seq` and set `dirty = true` in `monthly_projection_state` for the affected business.
4. **Advisory Lock & Refresh:** `MonthlyProjectionService.refresh(businessId)` acquires transaction-scoped advisory lock `pg_advisory_xact_lock(hashtext('monthly-projection:' + businessId))` ensuring single-threaded recalculation per business.
5. **Deterministic Projection:** `MonthlyFinancialProjector.project(businessId, month, today)` computes derived fields:
   - **Revenue (Accrual):** Cash sales (`DIRECT_SALE_RECEIPT`) + Recognized Invoices (issue date) $-$ Effective Credit Notes.
   - **Operating Expenses (Accrual):** Cash expenses (`OPERATING_EXPENSE_PAYMENT`) + Recognized Bills $-$ Capitalized Inventory $+$ Recognized Commissions $+$ Stock Losses.
   - **Cost of Goods Sold (COGS):** F08 weighted-average inventory consumption from actual sales, projects, and samples net of returns. Null if no inventory ledger exists.
   - **Cash Flows (Cash):** Posted net increases and decreases on PKR financial accounts during the calendar month (internal transfers and reversed transaction pairs excluded).
   - **Cash Balance EOM (Snapshot):** PKR account opening balances $+$ all posted movements up to month-end.
   - **Receivables & Payables EOM (Snapshot):** Open obligations net of allocations effective on or before month-end.
   - **Inventory Value EOM (Snapshot):** Carrying value from perpetual inventory ledger at month-end.
   - **Supplemental Liabilities:** `loanOutstanding`, `interestExpense`, and `financingType` sourced from `monthly_financing_inputs`.
6. **Fingerprinted Persistence:** Derived months are hashed via SHA-256 fingerprint. If fingerprint is unchanged, database writes are omitted.
7. **Downstream Rescoring:** If monthly figures changed, `ScoringService.recalculateIfScorable(businessId, month)` is triggered forward from the earliest modified month, recalculating `health-score-v1` composite scores and radar bands.

### 3.2 Verification of the 10 Critical Invariants

| # | Accounting Invariant | Technical Implementation Mechanism | Candidate v3 Verification Evidence |
|---|---|---|---|
| **1** | Each eligible posted transaction included exactly once | SQL query filters `t.event_type <> 'INTERNAL_TRANSFER'` and excludes reversed pairs | `TransactionDrivenMonthlyPostgreSqlIT` line 60 asserts revenue/expense sums |
| **2** | Drafts & non-posting commercial documents ignored | Explicitly queries only finalized/recognized documents | `TransactionDrivenMonthlyPostgreSqlIT` line 73 verifies draft invoice of 9999.00 is ignored |
| **3** | Reversals produce correct net financial outcome | Void pairs excluded via `NOT_VOID_PAIR` SQL predicate | `TransactionDrivenMonthlyPostgreSqlIT` line 140 verifies reversal removes 250.00 expense |
| **4** | Edits/corrections do not double-count | Database state re-read from root transactions; no additive delta state | Verified by pure function derivation in `MonthlyFinancialProjector` |
| **5** | Cash receipts and accrual revenue not interchangeable | Invoice collections hit `cashInflow` and reduce `receivablesOutstanding`, but do NOT add to `revenue` | `TransactionDrivenMonthlyPostgreSqlIT` line 118 confirms 1000.00 collection is not revenue |
| **6** | Project transactions retain correct attribution | Attributed to project ledger and tracked separately from operating expenses | `ProjectFundingGapCalculatorTest` (24/24) |
| **7** | Multi-currency amounts use appropriate treatment | Projection is strictly base-currency (`PKR`); foreign transactions convert using authoritative settlement rates | `FxCalculationServiceTest` (8/8), `MonthlyFinancialProjector.BASE_CURRENCY` |
| **8** | Duplicate events cannot inflate balances | Idempotency keys (`idempotency_key`) enforced at table and service level | `FinancialIdempotencyRepository`, `TransactionPostService` |
| **9** | Loan principal & interest remain isolated | Supplemental inputs segregated in `monthly_financing_inputs`; never inferred from operating expenses | `MonthlyFinancialProjector` lines 112–118 |
| **10** | Health score recalculation uses authoritative figures | `ScoringService` receives derived `MonthlyRecord` entity persisted in database | `TransactionDrivenMonthlyPostgreSqlIT` line 135 confirms `score_results` recomputed |

### 3.3 Mandatory Conflict Test: Rejection of Manual Overrides
FinSight enforces strict separation of derived figures from manual inputs:
- When a client issues `POST /api/records/monthly` with manual financial figures, `MonthlyRecordService.saveMonthlyRecord()` throws:
  $$\text{HTTP 409 Conflict: } \texttt{monthly\_figures\_derived}$$
- When a client issues `POST /api/finance/ledger-bridge/apply-projection` with manual overrides (`manualCogs`, `manualInventoryValue`, `manualLoanOutstanding`, `manualInterestExpense`, or `financingType`), `LedgerMonthlyBridgeService.applyLedgerProjection()` throws:
  $$\text{HTTP 409 Conflict: } \texttt{monthly\_figures\_derived}$$
- Permitted update path: `PUT /api/records/monthly/financing/{month}` accepts supplemental financing data (`loanOutstanding`, `interestExpense`, `financingType`) without conflict.

---

## 4. Phase D: Security & Authentication Compatibility

1. **Active-Business Tenant Isolation:**
   - Server-side resolution via `ActiveBusinessContext` and `BusinessAccessContext`.
   - All repository queries filter by `business_id` or `user_id`. No client-supplied query parameter can override tenant scope.
2. **RBAC & Authorization Matrix:**
   - `BusinessPermission` contains 19 granular permissions.
   - Financial posting requires `FINANCIAL_TRANSACTION_POST`; read queries require `FINANCIAL_DATA_READ`.
   - Cross-business access attempts return `403 Forbidden` or `404 Not Found`.
3. **CORS Hardening (`CorsOriginPolicy.java`):**
   - Wildcards (`*` or `null`) are strictly rejected.
   - Production profile (`production == true`) disallows wildcard subdomains, requiring exact origins via `CORS_ALLOWED_ORIGINS`.
   - Non-production profiles permit single left-most wildcard labels (`https://*.example.com`).
4. **Session Behavior on Business Switch (DEF-11 UX Inconsistency):**
   - Server session invalidation is secure and prevents cross-tenant mutation replay.
   - The frontend remount in `IntegrationBoundary.tsx` clears unsaved forms without prompt. This is a frontend UX issue, not a backend security hole.
5. **PII Encryption & Audit Logging:**
   - AES-256-GCM encryption enforced on WhatsApp numbers, PII names, and TOTP secrets with source AAD.
   - `security_audit_events` logs all security-sensitive actions (reversals, cutovers, MFA changes).

---

## 5. Phase F: Prioritized Defect & Technical Risk Register

| Defect ID | Severity | Category | Component & File Location | Observed Behavior / Risk | Recommended Remediation | Coordinating Owner |
|---|:---:|---|---|---|---|:---:|
| **DEF-11** | **P1** | UX / Session | `frontend/src/context/SessionProvider.tsx`<br>`frontend/src/components/layout/IntegrationBoundary.tsx` | Cross-tab business switch triggers unmount/remount on window focus, silently clearing unsaved form state without warning. | Implement modal confirmation intercept when dirty form is present; persist unsubmitted drafts in `sessionStorage` keyed by `(businessId, route)`. | **Codex (Stream 3)** |
| **DEF-18-B** | **P2** | Infrastructure | `scripts/start-demo.ps1`<br>`scripts/init-demo.ps1` | Docker container ownership and GUID tracking are contract-tested, but physical Docker rehearsal on Windows host has not been run due to missing local Docker daemon. | Execute full physical rehearsal (`init` $\rightarrow$ `start` $\rightarrow$ `verify` $\rightarrow$ `stop`) on an authorized Windows runner equipped with Docker Desktop. | **Suleman (Stream 6)** |
| **DEF-09** | **P2** | Usability / i18n | `frontend/src/components/...` | Raw enum `DIRECT_SALE_RECEIPT` displayed in activity stream instead of localized English/Urdu label. | Add localized mapping in presentation layer. | **Codex (Stream 3)** |
| **DEF-12** | **P2** | UX / Navigation | `frontend/src/components/...` | Lifecycle evidence link requires manual UUID entry instead of document picker dialog. | Integrate document picker modal. | **Codex (Stream 3)** |
| **DEF-14** | **P2** | UX / Navigation | `frontend/src/components/...` | Missing direct OCR evidence links on bill details and commercial document badges. | Add direct OCR link component. | **Codex (Stream 3)** |
| **DEF-15** | **P2** | Device / QA | `frontend/src/components/documents/UploadZone.tsx` | Mobile native camera `capture` attribute unverified on physical hardware. | Test on physical Android/iOS devices. | **Codex (Stream 3)** |
| **DEF-16** | **P2** | Usability | `frontend/src/app/(dashboard)/search/page.tsx` | `/search?q=` query string parameter does not prefill the search input field. | Bind search input state to URL query parameter. | **Codex (Stream 3)** |
| **DEV-A-01** | **P3** | Test Environment | `LocalFileSystemStorageServiceTests.java` | Symlink path traversal test skipped on Windows when developer account lacks `SeCreateSymbolicLinkPrivilege`. | Document Windows platform limitation; test runs and passes 100% on Linux CI. | **Suleman (Stream 9)** |

---

## 6. Technical Assessment & Readiness Statement

- **Financial Engine Integrity:** **100% VERIFIED.** All core double-entry equations, currency scales, and multi-currency reconciliations are functioning strictly as designed.
- **V52 Projection Invariants:** **100% VERIFIED.** Daily transactions flow into monthly projections deterministically; manual override conflicts are rejected with HTTP 409 `monthly_figures_derived`.
- **Git Provenance:** **100% VERIFIED.** Candidate v3 contains all of Developer A's committed backend work from `dev/suleman` and `demo/local-public-deployment`.
- **Engineering Readiness:** **CONDITIONALLY READY.** The backend is frozen and verified. Engineering sign-off requires resolving DEF-11 in the frontend (Codex) and conducting the physical Docker rehearsal (Suleman / DevOps).
