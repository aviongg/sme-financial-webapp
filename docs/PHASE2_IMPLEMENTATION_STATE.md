# FinSight Phase 2 Implementation State & Dependency Register

**Version:** `1.6`
**Last Updated:** 14 October 2026
**Canonical Branch:** `dev/suleman`
**Current HEAD SHA:** `8070af595ed48a8b88fe6fc67d05ac0fc86cd97b`
**Baseline Release:** PR #3 integrated into `main` (Flyway V1–V17)  

---

## 1. Environment & Baseline Status

| Item | Value | Evidence / Verification |
| :--- | :--- | :--- |
| **Active Branch** | `dev/suleman` | Verified via `git branch --show-current` |
| **Commit SHA** | `8070af595ed48a8b88fe6fc67d05ac0fc86cd97b` | Baseline commit prior to `A-W1-02-CHECKPOINT` |
| **Flyway Head** | **V19** | `backend/src/main/resources/db/migration/V19__create_financial_idempotency.sql` (V19 authored & verified) |
| **Untracked Artifacts** | `backend/.cp.txt` | Maven classpath artifact (13,680 bytes); preserved untouched |
| **Phase 2 Implementation** | **Accounts Read & Create (Integration Verified)** | `GET /api/finance/accounts`, `GET /api/finance/accounts/{id}`, `POST /api/finance/accounts` implemented & verified |
| **Blueprint Availability** | **Missing** | Master Phase 2 Technical Integration Blueprint not present locally |
| **Onboarding Audits** | **Completed** | Antigravity Backend Baseline Audit & Codex Frontend Baseline Audit completed |
| **Accounts Read Status** | **INTEGRATION_VERIFIED (Bounded Slice)** | Verified across Spring Boot, PostgreSQL V18, Next.js, and Edge |
| **Accounts Create Status** | **INTEGRATION_VERIFIED (Bounded Slice)** | Contract v1.1, V19 authored, 105 BE tests, 23 FE creation / 83 total FE tests, 21-check real live harness exit 0 |

---

## 2. Active Tasks & Allowed Paths

*   **Developer A Backend (Antigravity):**
    *   **Task ID:** `A-W1-02-CHECKPOINT` (Financial Account Creation & Opening Setup Checkpoint)
    *   **Status:** `INTEGRATION_VERIFIED — PAUSED`
    *   **Backend Paths:**
        *   `backend/src/main/resources/db/migration/V18__create_financial_accounts.sql`
        *   `backend/src/main/resources/db/migration/V19__create_financial_idempotency.sql`
        *   `backend/src/main/java/com/app/sme_health_backend/accounting/**`
        *   `backend/src/test/java/com/app/sme_health_backend/accounting/**`
    *   **Shared Backend Files Edited:**
        *   `backend/src/main/java/com/app/sme_health_backend/identity/model/BusinessPermission.java` (added `FINANCIAL_ACCOUNT_READ`, `FINANCIAL_ACCOUNT_CREATE`)
        *   `backend/src/main/java/com/app/sme_health_backend/identity/service/BusinessAuthorizationService.java` (mapped `FINANCIAL_ACCOUNT_CREATE` to `OWNER`, `ACCOUNTANT` only)
        *   `backend/src/main/java/com/app/sme_health_backend/shared/exception/GlobalExceptionHandler.java` (handles `AccountValidationException` 400, `DuplicateResourceException` 409)
        *   `backend/src/test/java/com/app/sme_health_backend/identity/service/BusinessAuthorizationServiceTest.java` (updated role test assertions)
    *   **Documentation Paths:**
        *   `docs/PHASE2_IMPLEMENTATION_STATE.md`
        *   `docs/phase2/contracts/ACCOUNT_CREATE_V1.md`
        *   `docs/phase2/handoffs/ACCOUNT_CREATE_CONTRACT_READY.md`
        *   `docs/phase2/handoffs/ANTIGRAVITY_W1_02_BE_CREATE.md`

*   **Developer A Frontend (Codex):**
    *   **Task ID:** `A-W1-02-FE-CREATE` (Account Creation UI & Combined Integration Harness)
    *   **Status:** `INTEGRATION_VERIFIED — PAUSED`
    *   **Frontend Paths:**
        *   `frontend/src/app/finance/accounts/page.tsx`
        *   `frontend/src/components/finance/accounts/**`
        *   `frontend/src/lib/api/dev-a/**`
        *   `frontend/src/lib/utils/decimal-display.ts`
        *   `frontend/tests/accounts-read.test.mjs`
        *   `frontend/tests/accounts-create.test.mjs`
        *   `frontend/tests/accounts-live.integration.mjs`
        *   `docs/phase2/handoffs/CODEX_W1_02_FE_CREATE.md`

---

## 3. Integration & Unit Verification Evidence Summary

| Layer / Gate | Command / Target | Result | Evidence Details |
| :--- | :--- | :--- | :--- |
| **Backend Accounts Suite (Read & Create)** | `mvn test -Dtest=AccountCreateServiceTest,AccountControllerTest,CurrencyRegistryTest,AccountReadServiceTest,FinancialAccountPostgreSqlIT,BusinessAuthorizationServiceTest` | **105 / 105 PASSED** | CurrencyRegistryTest (39), AccountReadServiceTest (13), AccountCreateServiceTest (13), AccountControllerTest (18), FinancialAccountPostgreSqlIT (15), BusinessAuthorizationServiceTest (7). Embedded PostgreSQL 14.22 + Flyway V18 + V19. |
| **Backend Authorization** | `mvn test -Dtest=BusinessAuthorizationServiceTest` | **7 / 7 PASSED** | OWNER, ACCOUNTANT, MANAGER, VIEWER permission grants verified for both `FINANCIAL_ACCOUNT_READ` and `FINANCIAL_ACCOUNT_CREATE`. |
| **Frontend Targeted Unit (Create)** | `pnpm exec node --test tests/accounts-create.test.mjs` | **23 / 23 PASSED** | Scale/capacity/sign/date validation, request allowlisting, adapter CSRF/header behavior, duplicate and field errors, role presentation, double clicks, retry/key preservation, conflict handling. |
| **Frontend Full Suite** | `pnpm test` | **83 / 83 PASSED** | 23 accounts-create + 24 accounts-read + 36 baseline MVP tests (zero failures, zero skips). |
| **Type Check & Lint** | `tsc --noEmit`, `pnpm run lint` | **PASSED** | 0 TypeScript errors, 0 ESLint errors (1 existing baseline warning in SessionProvider.tsx:204). |
| **Real Browser Integration Harness** | `node tests/accounts-live.integration.mjs` | **EXIT 0 (21 grouped checks)** | Real Spring Boot (port 8180) + Next.js (port 3180) + PostgreSQL 14.22 + Playwright/Edge. 21 grouped checks passed; blockers=[]; integrated=true. Verified OWNER/ACCOUNTANT creation, MANAGER/VIEWER denial, CSRF enforcement, duplicate detection, identical replay, 409 altered key conflict, business isolation. |
| **Concurrent Same-Key Execution** | Live harness parallel POSTs | **VERIFIED (8 -> 1)** | Eight concurrent same-key requests created exactly one account; all returned 201 with identical body and resource ID. |
| **Demo Mode Isolation** | `node tests/accounts-live.integration.mjs --demo` | **EXIT 0** | Verified English/Urdu unavailable preview with zero `/api/` network requests (zero private requests). |
| **Baseline Data & Score Integrity** | Pre/Post creation snapshot check | **VERIFIED UNCHANGED** | Row-count and content fingerprints of `monthly_records`, `score_results`, `insights`, and `recommendations` remained identical after all creation mutations and expiry journeys. |

### Limitations & Outstanding Gates
1. **Creation Scope Bounded:** Account creation establishes the immutable account baseline with durable idempotency. No transaction movement posting, double-counting, ledger cutover, or account editing/deletion.
2. **Opening-Only Calculation:** Balances reflect only persisted opening amounts or canonical zero for future-dated accounts; ledger posting integration remains in subsequent tasks.
3. **Migration Notification:** V18 and V19 are authored and verified locally on `dev/suleman`. Formal cross-team notification to Fatima remains pending.
4. **Production Gates:** PostgreSQL 17 TLS/HBA, Docker production container builds, external Google Vision OCR, WhatsApp API, and baseline items R01–R03 remain outstanding.

---

## 4. Project Lead Decisions (`SULEMAN_AUTHORIZED`)

The following decisions were authorized by project lead Muhammad Suleman Adil:
1.  **Permission Grants:** `FINANCIAL_ACCOUNT_READ` granted to `OWNER`, `ACCOUNTANT`, `MANAGER`, and `VIEWER`. `FINANCIAL_ACCOUNT_CREATE` granted strictly to `OWNER` and `ACCOUNTANT` only (`MANAGER` and `VIEWER` are denied creation with 403 Forbidden). Future role `SALESPERSON` receives no grants.
2.  **Direct Route Access:** Direct `/finance/accounts` route access permitted under standard session/business/permission checks.
3.  **Module Resolver Scope:** No C03 module resolver, module keys, or global navigation links in this initial slice.
4.  **V18 & V19 Reservations:** Flyway `V18` (financial accounts) and `V19` (financial idempotency records) reserved for Dev A following clean collision checks against remote `main` and `dev/fatima`.
5.  **Durable Idempotency Specification:** Account creation requires `Idempotency-Key` header with atomic SHA-256 payload digest matching. Duplicate names return 409 Conflict.
6.  **Raw Identifier Omission:** `accountIdentifier` input omitted in V1 wire creation to prevent raw unencrypted banking identifiers from entering storage or logs.

*Cross-Team Notification Note:* Fatima has not yet been notified of these decisions. Her formal approval has not been fabricated, and these decisions apply strictly to the accounts read/create boundary.

---

## 5. Published Remote Context & Collision Check

A fresh, read-only remote inspection was conducted against GitHub without fetching or modifying the shared working tree:
*   `origin/main` commit: `b51e27696cccc59b94aa9d9c9388ad0faa5b4176` (Flyway head: `V17`).
*   `origin/dev/fatima` commit: `60f570c53fe0e35248544d24f4d3ac55459564ad` (Dated 10 Oct 2026; Flyway head: `V17`).
*   **Collision Result:** `V18` and `V19` confirmed unreserved on remote `main` and `dev/fatima`.
*   **Limitation:** Unpushed local work on Fatima's machine cannot be inspected via GitHub API; this limitation is formally recorded.
*   **Namespace Reconciliation Item:** Fatima's published draft conventions propose `/api/v2`, whereas Contract v1.2 preserves `/api/finance/accounts` as currently consumed by Codex. In this bounded slice, `/api/finance/accounts` is preserved. Namespace reconciliation is recorded as an A/B integration item.

---

## 6. Baseline Findings Register (From Fatima Handoff)

The following findings reported in Fatima's published handoff are recorded for tracking (not modified or touched in this task):
*   **R01 (Document Security IT Flake):** Two full integration runs failed `DocumentSecurityPostgreSqlIT` (`expected processing_interrupted, actual unavailable`); isolated class execution passed.
*   **R02 (Same-Month Document Duplication):** Suspected risk of same-month document contribution or source duplication.
*   **R03 (Audit Identity Confusion):** Suspected audit actor vs. business identity confusion in security audit events.

---

## 7. Migration Reservation Register

| Version | Scope / Description | Assigned Owner | Global Status |
| :--- | :--- | :--- | :--- |
| **V1–V17** | Completed MVP schema & provenance triggers | Core / Baseline | **COMMITTED & LOCKED** |
| **V18** | Financial Accounts Schema (`V18__create_financial_accounts.sql`) | Antigravity (Dev A) | **COMMITTED (LOCAL `8070af5`)** |
| **V19** | Financial Idempotency Records (`V19__create_financial_idempotency.sql`) | Antigravity (Dev A) | **AUTHORED & INTEGRATION_VERIFIED (LOCAL)** |
| **V20** | Counterparties & Obligations Schema | Antigravity (Proposed) | **UNRESERVED** |

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
