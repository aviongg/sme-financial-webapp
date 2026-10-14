# FinSight Phase 2 Implementation State & Dependency Register

**Version:** `1.4`  
**Last Updated:** 14 October 2026  
**Canonical Branch:** `dev/suleman`  
**Current HEAD SHA:** `b51e27696cccc59b94aa9d9c9388ad0faa5b4176`  
**Baseline Release:** PR #3 integrated into `main` (Flyway V1–V17)  

---

## 1. Environment & Baseline Status

| Item | Value | Evidence / Verification |
| :--- | :--- | :--- |
| **Active Branch** | `dev/suleman` | Verified via `git branch --show-current` |
| **Commit SHA** | `b51e27696cccc59b94aa9d9c9388ad0faa5b4176` | Verified via `git log -1` (PR #3 merge commit) |
| **Flyway Head** | **V18** | `backend/src/main/resources/db/migration/V18__create_financial_accounts.sql` |
| **Untracked Artifacts** | `backend/.cp.txt` | Maven classpath artifact (13,680 bytes); preserved untouched |
| **Phase 2 Implementation** | **Accounts Read (BE & FE)** | `GET /api/finance/accounts` and `GET /api/finance/accounts/{id}` implemented |
| **Blueprint Availability** | **Missing** | Master Phase 2 Technical Integration Blueprint not present locally |
| **Onboarding Audits** | **Completed** | Antigravity Backend Baseline Audit & Codex Frontend Baseline Audit completed |
| **Accounts Read Status** | **INTEGRATION_VERIFIED (Bounded Slice)** | Verified across Spring Boot, PostgreSQL V18, Next.js, and Edge |

---

## 2. Active Tasks & Allowed Paths

*   **Developer A Backend (Antigravity):**
    *   **Task ID:** `A-W1-01-CHECKPOINT` (Record and locally commit verified accounts-read slice)
    *   **Status:** `ACTIVE_CHECKPOINT`
    *   **Backend Paths:**
        *   `backend/src/main/resources/db/migration/V18__create_financial_accounts.sql`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/**`
        *   `backend/src/test/java/com/app/sme_health_backend/accounting/**`
    *   **Shared Backend Files Edited:**
        *   `backend/src/main/java/com/app/sme_health_backend/identity/model/BusinessPermission.java` (added `FINANCIAL_ACCOUNT_READ`)
        *   `backend/src/main/java/com/app/sme_health_backend/identity/service/BusinessAuthorizationService.java` (mapped `FINANCIAL_ACCOUNT_READ` to `OWNER`, `ACCOUNTANT`, `MANAGER`, `VIEWER`)
        *   `backend/src/main/java/com/app/sme_health_backend/shared/exception/GlobalExceptionHandler.java` (added `AccountValidationException` 400 handler)
        *   `backend/src/test/java/com/app/sme_health_backend/identity/service/BusinessAuthorizationServiceTest.java` (updated role test assertions)
    *   **Documentation Paths:**
        *   `docs/PHASE2_IMPLEMENTATION_STATE.md`
        *   `docs/phase2/contracts/ACCOUNTS_API_DRAFT.md` (bumped to v1.3)
        *   `docs/phase2/handoffs/ANTIGRAVITY_W1_01_BE_READ.md`
        *   `docs/phase2/handoffs/ANTIGRAVITY_W1_01_BE_PARITY_FIX.md`
        *   `docs/phase2/handoffs/ANTIGRAVITY_W1_01_BE_NULLABLE_OPENING.md`
        *   `docs/phase2/handoffs/ACCOUNTS_READ_CHECKPOINT.md`

*   **Developer A Frontend (Codex):**
    *   **Task ID:** `A-W1-01-FE-V13-RETEST`
    *   **Status:** `INTEGRATION_VERIFIED — PAUSED`
    *   **Frontend Paths:**
        *   `frontend/src/app/finance/accounts/page.tsx`
        *   `frontend/src/components/finance/accounts/**`
        *   `frontend/src/lib/api/dev-a/**`
        *   `frontend/src/lib/utils/decimal-display.ts`
        *   `frontend/tests/accounts-read.test.mjs`
        *   `frontend/tests/accounts-live.integration.mjs`

---

## 3. Integration Verification Evidence Summary

| Layer / Gate | Command / Target | Result | Evidence Details |
| :--- | :--- | :--- | :--- |
| **Backend Unit & IT** | `mvn test -Dtest=...` | **76 / 76 PASSED** | CurrencyRegistryTest (39), AccountReadServiceTest (13), AccountControllerTest (14), FinancialAccountPostgreSqlIT (10). Embedded PostgreSQL 14.22 + Flyway V18. |
| **Backend Authorization** | `mvn test -Dtest=BusinessAuthorizationServiceTest` | **7 / 7 PASSED** | OWNER, ACCOUNTANT, MANAGER, VIEWER permission grants verified. |
| **Frontend Targeted Unit** | `node --test tests/accounts-read.test.mjs` | **24 / 24 PASSED** | Contract v1.3 discriminated type parsing, nullable opening balance, and display logic. |
| **Frontend Full Suite** | `pnpm test` | **60 / 60 PASSED** | 24 accounts-read tests + 36 baseline MVP tests. |
| **Type Check & Lint** | `tsc --noEmit`, `pnpm run lint` | **PASSED** | 0 TypeScript errors, 0 ESLint errors (1 existing baseline warning in SessionProvider.tsx). |
| **Real Browser Integration** | `node tests/accounts-live.integration.mjs` | **EXIT 0 (12/12 PASSED)** | Real Spring Boot (port 8180) + Next.js (port 3180) + PostgreSQL 14.22 + Playwright/Edge. 0 blockers. Re-tested successfully following initial run exit 1 (BE-01/BE-02). |
| **Demo Mode Isolation** | `node tests/accounts-live.integration.mjs --demo` | **EXIT 0** | Verified English/Urdu unavailable preview with zero `/api/` network requests. |

### Limitations & Outstanding Gates
1. **Opening-Only Limitation:** The verified slice implements financial account read models only (`openingBalance`, pre-opening canonical zero, masked identifiers, metadata). No movement ledger, transaction posting, transfer, reversal, or cutover reconciliation is implemented or accepted.
2. **Current-Only Integrity Isolation:** Covered by isolated unit tests; live test harness exercises opening-only values as no live transactions exist.
3. **Migration Notification:** V18 is authored and verified locally on dev/suleman. Cross-team notification to Fatima remains pending.
4. **Production Gates:** PostgreSQL 17 TLS/HBA, Docker production container builds, external Google Vision OCR, WhatsApp API, and baseline items R01–R03 remain outstanding.

---

## 4. Project Lead Decisions (`SULEMAN_AUTHORIZED`)

The following decisions were authorized by project lead Muhammad Suleman Adil:
1.  **Permission Grants:** `FINANCIAL_ACCOUNT_READ` granted to existing roles: `OWNER`, `ACCOUNTANT`, `MANAGER`, and `VIEWER`. Future role `SALESPERSON` receives no grant.
2.  **Direct Route Access:** Direct `/finance/accounts` route access permitted under standard session/business/permission checks.
3.  **Module Resolver Scope:** No C03 module resolver, module keys, or global navigation links in this initial read slice.
4.  **V18 Reservation:** Flyway `V18` reserved for Dev A financial accounts schema following a clean collision check.
5.  **Option A Adopted (Contract v1.3):** `openingBalance` is nullable (`string | null`). When opening data cannot be safely represented (`PRECISION_OVERFLOW`, `CAPACITY_OVERFLOW`, or `UNSUPPORTED_CURRENCY`), both `openingBalance` and `currentBalance` are emitted as `null` with `balanceStatus = UNAVAILABLE`. If opening data is valid but only current balance fails integrity reconciliation (`INTEGRITY_CHECK_FAILED`), the valid `openingBalance` is preserved. Explicit consumer-impacting wire change requiring Codex adaptation.

*Cross-Team Notification Note:* Fatima has not yet been notified of these decisions. Her formal approval has not been fabricated, and these decisions apply strictly to the accounts read boundary.

---

## 4. Published Remote Context & Collision Check

A fresh, read-only remote inspection was conducted against GitHub without fetching or modifying the shared working tree:
*   `origin/main` commit: `b51e27696cccc59b94aa9d9c9388ad0faa5b4176` (Flyway head: `V17`).
*   `origin/dev/fatima` commit: `60f570c53fe0e35248544d24f4d3ac55459564ad` (Dated 10 Oct 2026; Flyway head: `V17`).
*   **Collision Result:** `V18` was confirmed unreserved on remote `main` and `dev/fatima`.
*   **Limitation:** Unpushed local work on Fatima's machine cannot be inspected via GitHub API; this limitation is formally recorded.
*   **Namespace Reconciliation Item:** Fatima's published draft conventions propose `/api/v2`, whereas Contract v1.2 preserves `/api/finance/accounts` as currently consumed by Codex. In this bounded slice, `/api/finance/accounts` is preserved. Namespace reconciliation is recorded as an A/B integration item.

---

## 5. Baseline Findings Register (From Fatima Handoff)

The following findings reported in Fatima's published handoff are recorded for tracking (not modified or touched in this task):
*   **R01 (Document Security IT Flake):** Two full integration runs failed `DocumentSecurityPostgreSqlIT` (`expected processing_interrupted, actual unavailable`); isolated class execution passed.
*   **R02 (Same-Month Document Duplication):** Suspected risk of same-month document contribution or source duplication.
*   **R03 (Audit Identity Confusion):** Suspected audit actor vs. business identity confusion in security audit events.

---

## 6. Migration Reservation Register

| Version | Scope / Description | Assigned Owner | Global Status |
| :--- | :--- | :--- | :--- |
| **V1–V17** | Completed MVP schema & provenance triggers | Core / Baseline | **COMMITTED & LOCKED** |
| **V18** | Financial Accounts Schema (`V18__create_financial_accounts.sql`) | Antigravity (Dev A) | **AUTHORED & VERIFIED (LOCAL)** |
| **V19** | Business Configuration / Module Profiles | Fatima (Dev B Proposed)| **UNRESERVED** |
| **V20** | Counterparties & Obligations Schema | Antigravity (Proposed) | **UNRESERVED** |

---

## 7. Retained Later-Policy Issues (From Codex Review)

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
