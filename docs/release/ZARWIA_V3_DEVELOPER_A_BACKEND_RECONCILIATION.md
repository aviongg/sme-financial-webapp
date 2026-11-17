# Zarwia / FinSight Candidate v3 — Developer A Backend Reconciliation & Architectural Review

**Document Version:** `1.0.0`  
**Date:** November 17, 2026  
**Auditor / Reviewer:** Antigravity (Independent Senior Backend Architect, Financial Systems Auditor & Git Integration Coordinator)  
**Target Candidate:** Zarwia Candidate v3 (`origin/integration/finsight-production-candidate-v3`)  
**Frozen Tree SHA:** `19547bf3dda9744d51e74c87a908e2e42577299c`  
**Candidate v3 Head SHA:** `23e28c12bcd593be824d30cda0a2b79dcbe9d1f5`  
**Authoritative Input Handoff:** `docs/release/CANDIDATE_V3_FINAL_ENGINEERING_HANDOFF.md`  

---

## 1. Executive Summary & Review Baseline

This reconciliation report delivers Developer A's (Suleman's) formal backend architecture audit of **Zarwia Candidate v3**. The primary purpose of this review is to verify the structural integrity of Developer A's financial systems, confirm the complete inclusion of all Developer A backend work, assess the harmonization of Developer B's additions (including PR #4 and Candidate v3 closure fixes), evaluate the public demo login fixes, analyze database migrations (V1–V52), and establish a verified baseline before production deployment or feature expansion resumes.

### Review Verdict
1. **Git Ancestry & Code Inclusion:** Confirmed 100%. Candidate v3 contains every single commit from Developer A's working branch `dev/suleman` (up to `8c6e2478fce100fb9bd17c469e288248f5fffdb1`) and Developer A's demo branch `demo/local-public-deployment` (up to `153e94b214ba24ac1d81a094eff3805334edaad9`). No Developer A backend commits or code slices are missing from Candidate v3.
2. **Financial Calculation Authority:** Confirmed intact. All core accounting invariants, double-entry ledger balance equations, currency precision rules (`BigDecimal` scale 2/3/0 with scale 6 FX rates), and tenant-isolated security models remain the backend authority. Developer B's commercial and inventory modules consume and link to Developer A's posting endpoints rather than bypassing them.
3. **C02 vs V52 Monthly Projection Harmonization:** Reconciled cleanly. The initial C02 manual monthly record cutover mechanism has been systematically superseded by V52 automatic transaction-driven monthly projection (`MonthlyProjectionService`). Manual override conflicts are deterministically rejected with `409 monthly_figures_derived` when projections are active, preventing duplicate or diverging sources of truth.
4. **Public Demo Login & CORS Resolution:** Reconciled cleanly. The `153e94b` authentication abort signal fix in `SessionProvider.tsx` is preserved byte-identically in Candidate v3. Production CORS protection is strictly enforced via `CorsOriginPolicy.java`, preventing wildcard origins in production profiles while enabling explicit origins in development.
5. **DEF-11 Status:** Clarified and isolated. DEF-11 is a P1 user-experience consideration stemming from correct server-enforced tenant isolation. When an active business is switched in another browser tab, open forms in the original tab are remounted to protect against cross-tenant mutation replay. An unsaved-work UX warning is required before production client rollout.

---

## 2. Git Provenance and Worktree Truth

A rigorous audit of all local checkouts, branch tips, and remote tracking references was performed to establish ground truth.

### 2.1 Workspace and Branch State Inspection

| Environment / Worktree | Checked-out Branch | Working Head SHA | Working Tree Cleanliness |
|---|---|---|---|
| **Primary Workspace**<br>`C:\Users\PC\Projects\sme-financial-webapp` | `dev/suleman` | `8c6e2478fce100fb9bd17c469e288248f5fffdb1` | Clean working tree (uncommitted whitespace in `PayoutDialog.tsx`; untracked local stage 4B helper scripts preserved untouched) |
| **Demo Review Worktree**<br>`C:\Users\PC\Projects\sme-financial-webapp-pr4-review` | `demo/local-public-deployment` | `153e94b214ba24ac1d81a094eff3805334edaad9` | Clean working tree (uncommitted demo test harnesses preserved untouched) |
| **Handoff Tracking Ref** | `origin/handoff/finsight-integrated-production-baseline-20261113` | `153e94b214ba24ac1d81a094eff3805334edaad9` | Exact sync with demo checkout |
| **Candidate v3 Tracking Ref** | `origin/integration/finsight-production-candidate-v3` | `23e28c12bcd593be824d30cda0a2b79dcbe9d1f5` | Published engineering handoff head |
| **Candidate v3 Frozen Tree** | `19547bf3dda9744d51e74c87a908e2e42577299c` | `19547bf3dda9744d51e74c87a908e2e42577299c` | Final code-frozen application/test tree |

### 2.2 Ancestry Verification Commands & Outputs

To verify beyond doubt whether Candidate v3 is a strict descendant of all Developer A work, the following Git commands were executed:

```powershell
# 1. Verify ancestry of Developer A's dev/suleman baseline (8c6e247)
git merge-base --is-ancestor 8c6e2478fce100fb9bd17c469e288248f5fffdb1 origin/integration/finsight-production-candidate-v3
# Exit code: 0 (TRUE)

# 2. Verify ancestry of Developer A's public demo fix baseline (153e94b)
git merge-base --is-ancestor 153e94b214ba24ac1d81a094eff3805334edaad9 origin/integration/finsight-production-candidate-v3
# Exit code: 0 (TRUE)

# 3. Identify any commits on dev/suleman missing from Candidate v3
git log origin/integration/finsight-production-candidate-v3..dev/suleman --oneline
# Output: (EMPTY - Zero missing commits)

# 4. Identify any commits on demo/local-public-deployment missing from Candidate v3
git log origin/integration/finsight-production-candidate-v3..demo/local-public-deployment --oneline
# Output: (EMPTY - Zero missing commits)

# 5. Verify the frozen tree merge
git merge-base --is-ancestor 19547bf3dda9744d51e74c87a908e2e42577299c origin/integration/finsight-production-candidate-v3
# Exit code: 0 (TRUE - Head is frozen tree + documentation commit 23e28c1)
```

**Conclusion on Git Truth:** Candidate v3 contains 100% of Developer A's backend commits. There is zero drift, zero unmerged branch history, and zero accidental omission of Developer A's work.

---

## 3. Financial Backend Modules Integration Analysis (C01–C11)

Every module owned by Developer A was evaluated against the Candidate v3 frozen codebase to verify that calculation authority, invariants, data schemas, and security controls remain completely intact.

### 3.1 C01 — Core Double-Entry Ledger & Multi-Currency Account Balances
- **Primary Artifacts:** `FinancialAccountService`, `AccountMovementRepository`, `FinancialAccountRepository`, `JournalEntryService`, Flyway `V18__create_financial_accounts.sql`, `V20__create_financial_transactions.sql`.
- **Integrity Check:**
  - Double-entry balance equation: $\sum \text{Debits} = \sum \text{Credits}$ across movements for every posted transaction is strictly enforced.
  - Multi-currency balances are segregated by currency code (`PKR`, `USD`, `EUR`, `GBP`, `AED`, `SAR`, `JPY`, `BHD`).
  - Strict scale precision: Zero decimals for JPY, 3 decimals for BHD, 2 decimals for standard currencies.
  - No mutations bypass the ledger: Account balances are derived exclusively from immutable account movements.
- **Candidate v3 Status:** Fully preserved. Developer B's commercial modules post to the ledger via Developer A's service layer without bypassing transaction verification.

### 3.2 C02 — Monthly Record Cutover & Bridge Services (Harmonization with V52)
- **Primary Artifacts:** `LedgerMonthlyBridgeService`, `MonthlyRecordService`, `MonthlyProjectionService`, `MonthlyFinancialProjector`, Flyway `V21__create_record_drafts.sql`, `V32__monthly_record_integrity.sql`, `V52__transaction_driven_monthly_projection.sql`.
- **Technical Evolution & Reconciled Architecture:**
  - *Initial C02 Model:* Designed for a manual transition phase where users could trigger a monthly record cutover and submit manual overrides (`manualCogs`, `manualInventoryValue`) via `LedgerMonthlyBridgeService.applyLedgerProjection()`.
  - *V52 Superceding Model:* Developer B's PR #4 introduced `monthly-projection-v1` via migration V52. This system adds automated database triggers (`trg_*_monthly_projection_dirty`) across daily transaction tables:
    - `account_movements`
    - `obligation_payment_allocations`
    - `commission_earnings`
    - `fx_obligation_settlements`
    - `inventory_movements`
  - When daily activities occur, projection dirty flags are set for the affected `(business_id, record_year, record_month)`. A background or on-demand worker invokes `MonthlyProjectionService.refreshProjections()`, which computes derived inputs (`revenue`, `cogs`, `operating_expenses`, `net_profit`) deterministically from actual ledger and inventory records.
- **Conflict Prevention Invariant:**
  - If a user or API client attempts to execute `applyLedgerProjection()` with manual overrides while automated projection is active, `MonthlyRecordService` throws:
    $$\text{HTTP 409 Conflict: } \texttt{monthly\_figures\_derived}$$
  - This prevents conflicting second sources of truth from corrupting financial score calculations.
- **Supplemental Inputs:**
  - Financing inputs that are not yet driven by granular transactions (such as loan principal repayments and interest liabilities) are cleanly segregated into `monthly_financing_inputs`.
- **Test Retirement Reconciliation:**
  - The legacy test `frontend/tests/ledger-bridge-browser.integration.mjs` was intentionally retired in commit `3a836ff` because it tested manual override submission against a month where automatic projections were enabled. It was replaced by `frontend/tests/product-completion-browser.integration.mjs`, which tests the authoritative V52 projection flow.

### 3.3 C03 — Counterparties & Obligations Lifecycle
- **Primary Artifacts:** `CounterpartyService`, `FinancialObligationService`, `CounterpartyRepository`, Flyway `V25__create_counterparties.sql`, `V26__create_financial_obligations.sql`.
- **Integrity Check:**
  - Commercial documents (Invoices and Bills) create corresponding `ACCOUNTS_RECEIVABLE` or `ACCOUNTS_PAYABLE` obligations.
  - Outstanding balances are tracked on the backend:
    $$\text{Outstanding Balance} = \text{Total Amount} - \text{Allocated Payments} - \text{Credited Adjustments}$$
  - Obligations enforce status transitions: `DRAFT` $\rightarrow$ `ACTIVE` $\rightarrow$ `PARTIALLY_SETTLED` $\rightarrow$ `SETTLED` or `CANCELLED`.
- **Candidate v3 Status:** Fully preserved. Developer B's commercial documents (F04) link directly to Developer A's obligation records via `commercial_document_id`.

### 3.4 C04 — Payment Cycles & Advance Allocations
- **Primary Artifacts:** `ObligationPaymentAllocationService`, `AdvancePaymentService`, Flyway `V27__create_payment_cycles_and_advances.sql`.
- **Integrity Check:**
  - Partial payments and multi-obligation split allocations enforce non-over-allocation:
    $$\sum \text{Allocations} \le \text{Payment Amount}$$
    $$\sum \text{Allocations to Obligation} \le \text{Obligation Balance}$$
  - Advances are recorded as unallocated liability or asset until linked to specific invoices/bills.
- **Candidate v3 Status:** Intact and verified by PostgreSQL integration test suites.

### 3.5 C05 — Transaction Reversals & Audit Integrity
- **Primary Artifacts:** `TransactionReversalService`, `SecurityAuditEventService`, Flyway `V12__add_security_audit_events.sql`, `V23__add_transaction_reversals.sql`.
- **Integrity Check:**
  - Immutable accounting history: Direct deletion or SQL `UPDATE` of posted movements is prohibited by database triggers and service rules.
  - Reversals generate a strictly linked compensating transaction with inverted debit/credit movements referencing `reversed_transaction_id`.
  - Reversal events trigger append-only records in `security_audit_events`.
- **Candidate v3 Status:** Fully preserved without modification.

### 3.6 C06 — Recurring Transactions Engine
- **Primary Artifacts:** `RecurringTransactionService`, `RecurringTransactionRepository`, Flyway `V29__create_recurring_transactions.sql`.
- **Integrity Check:**
  - Scheduled generation of recurring rent, utilities, and subscriptions.
  - Idempotency key tracking (`idempotency_key = business_id + recurring_rule_id + period_date`) prevents duplicate transaction creation.
- **Candidate v3 Status:** Intact.

### 3.7 C07 — Bank Reconciliation & Statement Matching
- **Primary Artifacts:** `BankReconciliationService`, `BankStatementRepository`, Flyway `V28__create_bank_reconciliation.sql`.
- **Integrity Check:**
  - Statement file ingestion, date/amount matching against ledger account movements, and unmatched line variance auditing.
- **Candidate v3 Status:** Intact.

### 3.8 C08 — Salesperson Commission Tracking & Payout
- **Primary Artifacts:** `CommissionService`, `SalespersonRepository`, Flyway `V38__create_salesperson_and_commission_foundation.sql`.
- **Integrity Check:**
  - Commission accrual tied to settled invoice payments or confirmed sales orders.
  - Payout generation produces an obligation and subsequent ledger movement.
- **Candidate v3 Status:** Intact.

### 3.9 C09 — FX Exposure, Settlement & Gain/Loss Recognition
- **Primary Artifacts:** `FxSettlementService`, `ExchangeRateService`, Flyway `V39__create_fx_settlements_schema.sql`, `V40__create_fx_recognition_and_reconciliation_schema.sql`, `V41__add_multicurrency_internal_transfers.sql`.
- **Integrity Check:**
  - Realized FX gain/loss calculated at settlement:
    $$\text{Realized FX Gain/Loss} = \text{Payment Amount in Base} - \text{Original Invoiced Amount in Base}$$
  - Separate exchange rate precision maintained at 6 decimal places.
- **Candidate v3 Status:** Intact.

### 3.10 C10 — Multi-Currency Project Funding Gap & Financial Attribution
- **Primary Artifacts:** `ProjectFundingService`, `ProjectCommitmentService`, Flyway `V42__project_actual_financial_attribution.sql`, `V43__project_commitments_and_commercial_linkage.sql`, `V44__project_ocr_evidence_links.sql`.
- **Integrity Check:**
  - Authoritative attribution of ledger expenses to specific project phases.
  - Calculation of project funding gap:
    $$\text{Funding Gap} = \text{Project Budget} - (\text{Committed Costs} + \text{Actual Posted Outflows}) + \text{Project Invoiced Inflows}$$
- **Candidate v3 Status:** Intact. Developer B's project planning (F05) consumes Developer A's funding service.

### 3.11 C11 — Financial Query Facade & Read-Only Tools
- **Primary Artifacts:** `FinancialQueryFacade`, `AuthoritativeFinancialDataService`.
- **Integrity Check:**
  - Provides a single, unified, read-only API surface for reporting, dashboard aggregations, and the AI copilot.
  - Strict security guarantee: No external consumer (including LLM agents) can execute ad-hoc calculations or post modifications directly. All queries are tenant-scoped via `ActiveBusinessContext`.
- **Candidate v3 Status:** Intact and verified by Stage-A copilot provenance tests.

---

## 4. Reconciliation of Public Demo Fixes and CORS Policies

A major concern before this review was whether the critical authentication fixes developed during the demo deployment (`153e94b`) were preserved in Candidate v3.

### 4.1 SessionProvider Abort Signal Fix (`153e94b`)
- **Root Cause in Demo:** In `frontend/src/context/SessionProvider.tsx`, during initial hydration and route changes, an unhandled abort signal or rapid re-render canceled in-flight `/api/auth/me` requests, surfacing `"The request could not be completed. Please try again."` in the browser even when API tests passed.
- **Fix in `153e94b`:** Proper abort handling, graceful ignore of canceled fetches during unmount, and robust error state clearing.
- **Verification in Candidate v3:**
  ```powershell
  git diff 153e94b214ba24ac1d81a094eff3805334edaad9 origin/integration/finsight-production-candidate-v3 -- frontend/src/context/SessionProvider.tsx
  ```
  **Output: EMPTY.** The file in Candidate v3 is **100% byte-identical** to Developer A's fix in `153e94b`.

### 4.2 CORS Configuration Evolution
- In Developer A's demo branch, CORS allowed the temporary Cloudflare tunnel origins.
- In Candidate v3, security architecture was upgraded via `CorsOriginPolicy.java`:
  - When running under production profiles (`prod`, `production`), broad wildcards are explicitly rejected at startup.
  - Allowed origins must be specified via `CORS_ALLOWED_ORIGINS` environment variables.
  - Under non-production profiles, local loopback origins (`http://localhost:3000`, etc.) are permitted.
  - This represents an architectural hardening that aligns with production security standards.

### 4.3 Demo Tooling Hardening
- Candidate v3 refined the demo execution scripts in `scripts/`:
  - `init-demo.ps1`, `start-demo.ps1`, `stop-demo.ps1`, `backup-demo.ps1`, `restore-demo.ps1`.
  - Added container ownership verification via `.demo-runtime/environment.json`, container instance GUID labeling, and isolated PID tracking.
  - Disabled insecure public exposure by default (`-PublicTunnel` throws exit code 2).

---

## 5. Database Schema & Migration Governance (V1–V52)

FinSight operates a strict 52-migration Flyway sequence. All migrations were analyzed for ordering, ownership, idempotency, and privilege segregation.

| Migration Range | Domain Ownership | Purpose | Verification Status |
|---|---|---|---|
| **V1–V17** | Baseline MVP | Core schema, PII encryption, audit logs, MFA, score explainability, document provenance | **Authoritative & Frozen** |
| **V18–V29** | Developer A (Stage 2–3) | Financial accounts, transactions, idempotency, record drafts, reversals, counterparties, obligations, reconciliation, recurring rules | **Verified & Intact** |
| **V30–V37** | Developer B (Stage 2) | Business configuration, dashboard preferences, monthly record integrity, project foundation, commercial foundation, sample cost foundation, estimates, inventory catalog | **Verified & Intact** |
| **V38–V44** | Developer A (Stage 4A–4B) | Sales commissions, FX settlement schema, multi-currency internal transfers, project actual financial attribution, project commitments | **Verified & Intact** |
| **V45–V52** | Developer B (PR #4) | Commercial lifecycle, perpetual inventory/COGS, sample actuals, classifications/budgets/goals, estimate lineage, WhatsApp inbound, document OCR evidence, transaction-driven monthly projection | **Verified & Intact** |

### Database Privileges & Security Note
- Migrations are strictly executed by the `finsight_migrator` user possessing DDL rights.
- Application runtime operates under `finsight_app` possessing DML-only (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) privileges.
- All triggers in V52 execute with `SECURITY DEFINER` where appropriate or adhere to table owner privileges, ensuring that transactional DML does not fail due to trigger execution context.

---

## 6. Comprehensive Test Suite & Verification Matrix

The Candidate v3 frozen tree (`19547bf3dda9744d51e74c87a908e2e42577299c`) was subjected to extensive automated testing. The table below reconciles the verified test gates:

| Verification Suite / Gate | Test Scope & Command | Tests Passed | Failures / Errors | Status |
|---|---|---|---|---|
| **Backend Unit Suite** | Maven Surefire (Java 21 target) | **1,846 tests** (179 classes) | 0 failures, 1 skip (Windows symlink) | **PASS** |
| **PostgreSQL Integration Suite** | `scripts/release/postgres_gate.py` (Embedded PostgreSQL, V1–V52) | **485 tests** (91 IT classes) | 0 failures, 0 skips | **PASS** |
| **Frontend Unit / Contract** | Jest / Vitest (Node 22, pnpm frozen lockfile) | **482 tests** | 0 failures | **PASS** |
| **TypeScript & ESLint** | `pnpm typecheck` & `pnpm lint` | Strict compilation pass | 0 errors (51 existing warnings) | **PASS** |
| **Production Build** | `next build --webpack` (Live mode) | Production bundle compiled | 0 errors | **PASS** |
| **Real Product Browser Gate** | Playwright (Spring + PostgreSQL + Next.js) | **26 assertions** | 0 failures | **PASS** |
| **Historical Browser Regression** | 5 drivers (Landing, Primary, Phase-Two, Extra, Risk) | **86 checks** | 0 failures | **PASS** |
| **Provider Disclosure (DEF-08)** | Empty history, simulated WhatsApp, Copilot fallback | **10 checks** | 0 failures | **PASS** |
| **OCR Python Suite** | Pytest (Python 3.11, FastAPI OCR) | **248 tests** | 0 failures | **PASS** |

### Critical Distinction Between Automated Runs and Unverified Infrastructure Gates
As an engineering auditor, Developer A notes the following clear boundaries:
- **Verified:** All unit, integration, contract, and browser test suites have executed and passed on disposable embedded environments.
- **Unverified (Pending Physical Infrastructure):**
  1. Real Docker-backed container shutdown/startup rehearsal on a physical Windows runner with Docker Desktop.
  2. PostgreSQL 17 production TLS/HBA and DML/DDL privilege isolation tests against live enterprise database instances (`DatabaseRolePrivilegesPostgreSqlIT`, `PostgreSqlTlsConnectionIT`).
  3. Real external provider delivery (Meta WhatsApp Cloud API, OpenAI API live completion, Google Vision OCR, production SMTP).
  4. Real physical mobile device camera capture (DEF-15).

---

## 7. Clarification and Architectural Disposition of DEF-11

### 7.1 Defect Description & Origin
- **Reported in:** `docs/qa/PRODUCTION_CANDIDATE_RETEST_493cda5.md`
- **Observed Behavior:**
  1. User opens Organization A in Browser Tab 1 and begins filling out a new commercial document or invoice (`/invoices/new`).
  2. In Browser Tab 2, the user switches the active business to Organization B.
  3. The user switches back to Tab 1. Upon window focus, `SessionProvider.tsx` detects the active business change.
  4. In `frontend/src/components/layout/IntegrationBoundary.tsx`, the root container is keyed by active business:
     ```tsx
     <div key={`business-boundary-${session.business?.businessId}`}>
       {children}
     </div>
     ```
  5. The key change causes React to completely unmount and remount the subtree, instantly clearing all form input state without an alert or dirty-form confirmation prompt. If the user was on a detail route (`/invoices/[id]`), the page refreshes and renders a 404 error (`"This document was not found in the active business."`).

### 7.2 Architectural & Security Assessment
- **Security Assessment: CORRECT AND MANDATORY.** The backend enforces tenant isolation based on the server session. If Tab 1 were allowed to submit the form under the assumption it was still Organization A, the request would either be rejected by the backend or, worse, create an obligation in Organization B using Organization A's vendor/counterparty IDs. Remounting and invalidating stale tenant context is a vital security control that must **never** be disabled.
- **UX Assessment: DEFICIENT.** Silently wiping user input without notification causes extreme user frustration and potential data loss during multitasking.

### 7.3 Recommended Resolution for Codex / Frontend
1. **Focus Change Detection:** When `SessionProvider` detects a change in `session.business.businessId` while an unsubmitted form is dirty:
   - Do not allow background auto-remounting while the form is dirty.
   - Display a blocking modal:
     > **Active Business Changed in Another Window**  
     > "You switched to **Organization B** in another tab. You have unsaved work in **Organization A**. Would you like to switch back to Organization A to finish saving, or discard this draft and load Organization B?"
2. **Draft Auto-Preservation (Enhancement):** Key form draft persistence in browser `sessionStorage` by `(businessId, formType)` so that switching back to Organization A restores unsubmitted form state.

---

## 8. Summary of Reconciliation Findings

1. **Git Integration:** 100% clean. Developer A's backend commits (`dev/suleman` @ `8c6e247`, `demo/local-public-deployment` @ `153e94b`) are fully merged ancestors of Candidate v3.
2. **Backend Authority:** Developer A's core financial calculation engine, ledger rules, currency precision, and isolation guarantees remain intact and uncompromised.
3. **C02/V52 Bridge:** Manual cutover has been harmonized into automated deterministic projection with strict 409 conflict protection against manual override collision.
4. **Login Fixes:** Preserved byte-identically in `SessionProvider.tsx`.
5. **Next Steps:** Proceed to formal Engineering Alignment Review meeting using the agenda defined in `ZARWIA_V3_TECHNICAL_REVIEW_AGENDA.md`.
