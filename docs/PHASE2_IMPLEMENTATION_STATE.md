# FinSight Phase 2 Implementation State & Dependency Register

**Version:** `1.9`
**Last Updated:** 15 October 2026
**Canonical Branch:** `dev/suleman`
**Current HEAD SHA:** `68d2995ca3548e57256b0be5f8a44322e11d8f7c`
**Baseline Release:** PR #3 integrated into `main` (Flyway V1–V17)  

---

## 1. Environment & Baseline Status

| Item | Value | Evidence / Verification |
| :--- | :--- | :--- |
| **Active Branch** | `dev/suleman` | Verified via `git branch --show-current` |
| **Commit SHA** | `68d2995ca3548e57256b0be5f8a44322e11d8f7c` | Baseline commit prior to `A-W1-03-BE-TRANSACTIONS` |
| **Flyway Head** | **V20** | `backend/src/main/resources/db/migration/V20__create_financial_transactions.sql` (V20 authored, verified & committed) |
| **Untracked Artifacts** | `backend/.cp.txt` | Maven classpath artifact (13,680 bytes); preserved untouched |
| **Phase 2 Implementation** | **Transactions Backend, Period Summaries, Connected Records & Funding Gap Calculator** | `POST /api/finance/transactions`, `GET /api/finance/transactions`, `GET /api/finance/transactions/{id}`, `GET /api/finance/transactions/summary` implemented & verified; account reads incorporating movements; central Records workspace; F07 pure funding gap calculator |
| **Blueprint Availability** | **Missing** | Master Phase 2 Technical Integration Blueprint not present locally |
| **Onboarding Audits** | **Completed** | Antigravity Backend Baseline Audit & Codex Frontend Baseline Audit completed |
| **Accounts Read Status** | **INTEGRATION_VERIFIED (Bounded Slice)** | Verified across Spring Boot, PostgreSQL V18, Next.js, and Edge |
| **Accounts Create Status** | **INTEGRATION_VERIFIED (Bounded Slice)** | Contract v1.1, V19 authored, 105 BE tests, 23 FE creation / 83 total FE tests, 21-check real live harness exit 0 |
| **Records, Accounts & Transactions Status** | **INTEGRATION_VERIFIED (Bounded Slice)** | Real Spring Boot (port 8180) + Next.js (port 3180) + PostgreSQL 14.22 (V20) + Playwright/Edge; 33 passing check groups, exit 0, 0 blockers |
| **F07 Funding Gap Calculator Status** | **UNIT_VERIFIED (Bounded Pure Calculator)** | 24/24 unit tests passed both in isolated execution and via project Maven runner `mvnw.cmd test -Dtest=ProjectFundingGapCalculatorTest`; application/API integration pending |

---

## 2. Active Tasks & Allowed Paths

*   **Developer A Backend (Antigravity):**
    *   **Task ID:** `A-RECORDS-01-BE` (Reliable Transaction Foundation & Records Summaries)
    *   **Status:** `INTEGRATION_VERIFIED — CHECKPOINT_COMMITTED`
    *   **Backend Paths:**
        *   `backend/src/main/resources/db/migration/V18__create_financial_accounts.sql`
        *   `backend/src/main/resources/db/migration/V19__create_financial_idempotency.sql`
        *   `backend/src/main/resources/db/migration/V20__create_financial_transactions.sql`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/**`
        *   `backend/src/test/java/com/app/sme_health_backend/accounting/**`
    *   **Shared Backend Files Edited:**
        *   `backend/src/main/java/com/app/sme_health_backend/identity/model/BusinessPermission.java` (added `FINANCIAL_TRANSACTION_READ`, `FINANCIAL_TRANSACTION_POST`)
        *   `backend/src/main/java/com/app/sme_health_backend/identity/service/BusinessAuthorizationService.java` (mapped `FINANCIAL_TRANSACTION_POST` to `OWNER`, `ACCOUNTANT` only; `FINANCIAL_TRANSACTION_READ` to all four roles)
        *   `backend/src/test/java/com/app/sme_health_backend/identity/service/BusinessAuthorizationServiceTest.java` (updated role test assertions)
        *   `backend/src/main/java/com/app/sme_health_backend/shared/exception/GlobalExceptionHandler.java` (handles `TransactionValidationException`)
    *   **Documentation Paths:**
        *   `docs/PHASE2_IMPLEMENTATION_STATE.md`
        *   `docs/phase2/contracts/C01_POSTING_DRAFT.md` (v2.0 CONTRACT_READY)
        *   `docs/phase2/handoffs/TRANSACTIONS.md` (Summary v1.0 CONTRACT_READY & RUNTIME_READY / INTEGRATION_VERIFIED)

*   **Developer A Frontend (Codex):**
    *   **Task ID:** `A-RECORDS-01-FE` (Central Records Workspace & Frontend Integration)
    *   **Status:** `INTEGRATION_VERIFIED — CHECKPOINT_COMMITTED`
    *   **Frontend Paths:**
        *   `frontend/src/app/records/**`
        *   `frontend/src/app/finance/accounts/**`
        *   `frontend/src/app/finance/transactions/**`
        *   `frontend/src/components/records/**`
        *   `frontend/src/components/finance/**`
        *   `frontend/src/components/layout/**`
        *   `frontend/src/components/integration/**`
        *   `frontend/src/lib/api/dev-a/**`
        *   `frontend/src/lib/utils/amount-input.ts`
        *   `frontend/tests/**`

*   **Developer A Pure Calculator (Antigravity):**
    *   **Task ID:** `A-F07-CALCULATOR` (Independent Project Funding-Gap Calculation)
    *   **Status:** `UNIT_VERIFIED — CHECKPOINT_COMMITTED`
    *   **Paths Owned:**
        *   `backend/src/main/java/com/app/sme_health_backend/liquidity/**`
        *   `backend/src/test/java/com/app/sme_health_backend/liquidity/**`
        *   `docs/phase2/FUNDING_GAP_CALCULATION.md`
    *   **Scope Note:** Pure in-memory calculation foundation for F07; application/API/persistence integration remains pending.

---

## 3. Integration & Unit Verification Evidence Summary

| Layer / Gate | Command / Target | Result | Evidence Details |
| :--- | :--- | :--- | :--- |
| **Real Browser Integration Harness (Records, Accounts & Transactions)** | `pnpm exec node tests/accounts-live.integration.mjs` | **EXIT 0 (33 grouped checks, 0 blockers)** | Real Spring Boot (port 8180) + Next.js (port 3180) + PostgreSQL 14.22 (Flyway V20) + Playwright/Edge. 33 grouped checks passed; blockers=[]; integrated=true. Verified account read/create/post, current-only capacity overflow rendering, summary aggregation (`4501.06 / 0.00 / 4501.06`, count 3), natural/Arabic/Urdu inputs, concurrency, idempotent replay, CSRF, multi-tenant isolation, and untouched monthly records/score fingerprints. |
| **Frontend Targeted Unit & Total Suites** | `pnpm exec node --test tests/records.test.mjs`, `pnpm test` | **12 / 12 PASSED** (records), **121 / 121 PASSED** (total frontend) | All unit, contract, and regression suites passed with zero failures or skips. Tested natural/grouped/Arabic/Urdu inputs, HALF_UP rounding, date boundary presets, safe return URLs, canonical summary echoing, and frozen retry identity. |
| **Type Check & Lint** | `tsc --noEmit`, `pnpm run lint` | **PASSED** | 0 TypeScript errors, 0 ESLint errors (1 existing baseline warning in SessionProvider.tsx:204). |
| **Demo Mode Isolation** | `node tests/accounts-live.integration.mjs --demo` | **EXIT 0** | Verified English/Urdu unavailable preview with zero `/api/` network requests (zero private requests). |
| **Baseline Data & Score Integrity** | Pre/Post creation & posting snapshot check | **VERIFIED UNCHANGED** | Row-count and MD5 content fingerprints of `monthly_records`, `score_results`, `insights`, and `recommendations` remained identical after all creation and posting mutations. |
| **Backend Comprehensive Suite (Accounts + Transactions + Summaries)** | `mvn test "-Dtest=TransactionControllerTest,TransactionPostServiceTest,FinancialTransactionPostgreSqlIT,FinancialAccountPostgreSqlIT,AccountReadServiceTest,AccountCreateServiceTest,AccountControllerTest,CurrencyRegistryTest,BusinessAuthorizationServiceTest"` | **140 / 140 PASSED** | TransactionControllerTest (11), TransactionPostServiceTest (13), FinancialTransactionPostgreSqlIT (11), FinancialAccountPostgreSqlIT (7), AccountReadServiceTest (18), AccountCreateServiceTest (15), AccountControllerTest (19), CurrencyRegistryTest (39), BusinessAuthorizationServiceTest (7). Disposable PostgreSQL 14.22 + Flyway V18 + V19 + V20. |
| **Maven Project Runner (Liquidity Calculator)** | `mvnw.cmd test -Dtest=ProjectFundingGapCalculatorTest` | **24 / 24 PASSED** (0 failures, 0 errors, 14.0s) | Verified reference fixture (50k gap, peak 11-01, recovery 11-04), zero events/opening, sufficient funds, non-recovery, temporary recovery, final-day recovery, same-day netting, repeated peaks, order invariance, large decimal accuracy, invalid input rejections, and immutability. |
| **Backend Authorization** | `mvn test -Dtest=BusinessAuthorizationServiceTest` | **7 / 7 PASSED** | OWNER, ACCOUNTANT, MANAGER, VIEWER permission grants verified for `FINANCIAL_ACCOUNT_READ`, `FINANCIAL_ACCOUNT_CREATE`, `FINANCIAL_TRANSACTION_READ`, and `FINANCIAL_TRANSACTION_POST`. |

### Limitations & Outstanding Gates
1. **Creation & Posting Scope Bounded:** Initial transactions support `DIRECT_SALE_RECEIPT` and `OPERATING_EXPENSE_PAYMENT` on PKR accounts only. Transfers, reversals, FX conversions, AR/AP, customer advances, supplier settlements, and commissions are excluded.
2. **Opening & Movement Calculation:** Balances reflect persisted opening amounts plus sum of posted movements $\le$ cutoff date. Current-only capacity overflow preserves valid opening per authorized v1.3 invariant.
3. **Monthly Snapshots Preserved:** Monthly health scores, `MonthlyRecord` summaries, and recommendations are untouched in this slice. Existing snapshots remain authoritative for monthly intelligence; ledger cutover remains pending.
4. **F07 Calculator Scope:** Pure in-memory calculation foundation only. Spring registration, HTTP REST endpoints, persistence entities, and background jobs remain pending.
5. **Migration Notification:** V18, V19, and V20 are authored and verified locally on `dev/suleman`. Formal cross-team notification to Fatima remains pending.
6. **Production Gates:** PostgreSQL 17 TLS/HBA, Docker production container builds, external Google Vision OCR, WhatsApp API, baseline items R01–R03, and cross-team integration gates remain open.

---

## 4. Project Lead Decisions (`SULEMAN_AUTHORIZED`)

The following decisions were authorized by project lead Muhammad Suleman Adil:
1.  **Permission Grants:** `FINANCIAL_ACCOUNT_READ` and `FINANCIAL_TRANSACTION_READ` granted to `OWNER`, `ACCOUNTANT`, `MANAGER`, and `VIEWER`. `FINANCIAL_ACCOUNT_CREATE` and `FINANCIAL_TRANSACTION_POST` granted strictly to `OWNER` and `ACCOUNTANT` only (`MANAGER` and `VIEWER` are denied creation/posting with 403 Forbidden). Future role `SALESPERSON` receives no grants.
2.  **Direct Route Access:** Direct `/finance/accounts` and `/finance/transactions` route access permitted under standard session/business/permission checks.
3.  **Module Resolver Scope:** No C03 module resolver, module keys, or global navigation links in this initial slice.
4.  **V18, V19 & V20 Reservations:** Flyway `V18` (financial accounts), `V19` (financial idempotency records), and `V20` (financial transactions & movements) reserved for Dev A following clean collision checks against remote `main` and `dev/fatima`.
5.  **Durable Idempotency Specification:** Account creation and transaction posting require `Idempotency-Key` header with atomic SHA-256 payload digest matching. Duplicate names return 409 Conflict.
6.  **Raw Identifier Omission:** `accountIdentifier` input omitted in V1 wire creation to prevent raw unencrypted banking identifiers from entering storage or logs.

*Cross-Team Notification Note:* Fatima has not yet been notified of these decisions. Her formal approval has not been fabricated, and these decisions apply strictly to the accounts and transaction boundary.

---

## 5. Published Remote Context & Collision Check

A fresh, read-only remote inspection was conducted against GitHub without fetching or modifying the shared working tree:
*   `origin/main` commit: `b51e27696cccc59b94aa9d9c9388ad0faa5b4176` (Flyway head: `V17`).
*   `origin/dev/fatima` commit: `60f570c53fe0e35248544d24f4d3ac55459564ad` (Dated 10 Oct 2026; Flyway head: `V17`).
*   **Collision Result:** `V18`, `V19`, and `V20` confirmed unreserved on remote `main` and `dev/fatima`.
*   **Limitation:** Unpushed local work on Fatima's machine cannot be inspected via GitHub API; this limitation is formally recorded.
*   **Namespace Reconciliation Item:** Fatima's published draft conventions propose `/api/v2`, whereas Contract v1.2 preserves `/api/finance/accounts` and `/api/finance/transactions` as currently consumed by Codex. In this bounded slice, existing paths are preserved. Namespace reconciliation is recorded as an A/B integration item.

---

## 6. Baseline Findings Register (From Fatima Handoff)

The following findings reported in Fatima's published handoff are recorded for tracking (not modified or touched in this task):
*   **R01 (Document Security IT Flake):** Two full integration runs failed `DocumentSecurityPostgreSqlIT` (`expected processing_interrupted, actual unavailable`); isolated class execution passed.
*   **R02 (Same-Month Document Duplication):** Suspected risk of same-month document contribution or source duplication.
*   **R03 (Audit Identity Confusion):** Suspected audit actor vs. business identity confusion in security audit events (resolved in Phase 2 accounting and transactions by explicitly passing distinct `context.userId()` as actor and `context.businessId()` as business).

---

## 7. Migration Reservation Register

| Version | Scope / Description | Assigned Owner | Global Status |
| :--- | :--- | :--- | :--- |
| **V1–V17** | Completed MVP schema & provenance triggers | Core / Baseline | **COMMITTED & LOCKED** |
| **V18** | Financial Accounts Schema (`V18__create_financial_accounts.sql`) | Antigravity (Dev A) | **COMMITTED (LOCAL `8070af5`)** |
| **V19** | Financial Idempotency Records (`V19__create_financial_idempotency.sql`) | Antigravity (Dev A) | **COMMITTED (LOCAL `68d2995`)** |
| **V20** | Financial Transactions & Account Movements (`V20__create_financial_transactions.sql`) | Antigravity (Dev A) | **COMMITTED (LOCAL)** |
| **V21** | Counterparties & Obligations Schema | Antigravity (Proposed) | **UNRESERVED** |

---

## 8. Retained Later-Policy Issues (From Codex Review)

*   **M1 (Opening Authority):** Single opening balance authority; cached balance equals opening balance plus sum of subsequent movements.
*   **M2 (Source Identity):** Event UUID vs. content SHA-256 fingerprint.
*   **M3 (Idempotency):** Durable uniqueness table and replay authorization checks.
*   **M4 (Date Invariants):** Account opening date must be $\le$ cutover month start date.
*   **M5 (Cash Propagation):** Backdated cash movements propagate month-end cash forward indefinitely.
*   **M6 (Negative Compatibility):** Derived negative totals return atomic domain error, never silent clamping.
*   **M7 (Monthly Mapping):** Historical account inclusion and month-end foreign currency base valuation.
*   **M8 (Declarations Matrix):** Hybrid maintenance of non-derivable optional declarations via PATCH.
*   **M9 (OCR Posting):** Confirm-once creates posted transactions; unconfirmed drafts never post actuals.
*   **M10 (DTO Consistency):** Consistent enum naming (`INCREASE`/`DECREASE`) and non-cash recognition.
