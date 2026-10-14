# Financial Accounts Read Checkpoint

**Task:** `A-W1-01-CHECKPOINT`  
**Date:** 14 October 2026 (Asia/Karachi)  
**Status:** **ACCOUNTS_READ_CHECKPOINT_RECORDED — PAUSED**  
**Role:** Antigravity (Developer A Backend & Checkpoint Git Coordinator)  
**Active Development Branch:** `dev/suleman`  
**Baseline Commit:** `b51e27696cccc59b94aa9d9c9388ad0faa5b4176` (PR #3 merge commit)  
**Contract Version:** `ACCOUNTS_API_DRAFT.md` **v1.3**  

---

## 1. Accepted Scope

This checkpoint accepts and commits the bounded Financial Accounts Read capability across backend, persistence, frontend, contracts, and tests:

1. **Financial Accounts Persistence (V18):**
   - Migration `V18__create_financial_accounts.sql` introducing `financial_accounts` table with UUID PK, business tenant FK, `account_type` (`CASH`, `BANK`, `WALLET`), `currency` (3-char ISO), `currency_decimals` (INT), `opening_balance` (`NUMERIC(19,4)`), `opening_date` (DATE), `status` (`ACTIVE`, `INACTIVE`, `ARCHIVED`), institution name, masked account identifier, notes, and timestamp columns.
   - Indices on `(business_id, status)` and `(business_id, created_at ASC, id ASC)`.
   - DML grants to `finsight_app` runtime role; DDL owned by `finsight_migrator`.
2. **Backend Read Endpoints:**
   - `GET /api/finance/accounts` (deterministic pagination, filtering by `status`, `accountType`, `currency`, preserved requested pagination on empty/out-of-range).
   - `GET /api/finance/accounts/{id}` (detail read, cross-business 404 non-disclosing protection).
3. **Role Grants:**
   - Server-enforced `FINANCIAL_ACCOUNT_READ` permission mapped to `OWNER`, `ACCOUNTANT`, `MANAGER`, and `VIEWER`. `SALESPERSON` denied.
4. **Exact Decimal Display & Nullable Opening Invariants (Contract v1.3 / Option A):**
   - Exact scale mappings (PKR/USD/EUR/GBP/AED/SAR: 2; JPY: 0; BHD/KWD/OMR: 3).
   - `openingBalance` is nullable (`string | null`).
   - When opening balance is unrepresentable (precision overflow, capacity overflow, or unsupported currency metadata), both `openingBalance` and `currentBalance` evaluate to `null` with `balanceStatus = UNAVAILABLE`. Never round, truncate, fabricate zero, or leak malformed amounts.
   - Valid pre-opening accounts (`openingDate > effectiveCutoffDate`) emit planned `openingBalance` and `AVAILABLE` `currentBalance` of canonical zero.
5. **Frontend Accounts View:**
   - Next.js App Router route `/finance/accounts`.
   - Bilingual (English & Urdu RTL) accounts list and detail Sheet.
   - Exact decimal formatting without Number conversion; masked identifiers; safe invalid-data and unavailable states.
   - Demo mode preview with zero `/api/` network requests.

### Excluded Scope (Non-Accepted)
*   Account creation (`POST`), account editing (`PUT`/`PATCH`), and account archiving/deletion.
*   Transaction posting, double-entry movements, cash transfers, and transaction ledger.
*   Balance adjustments, reconciliation engine, cutover controls, and monthly projection feeds.
*   Global sidebar navigation integration and C03 module resolver keys.

---

## 2. Verification Evidence Summary

| Test Layer | Runner / Command | Result | Evidence Notes |
| :--- | :--- | :--- | :--- |
| **Backend Unit & IT** | `.\mvnw.cmd test "-Dtest=CurrencyRegistryTest,AccountReadServiceTest,AccountControllerTest,FinancialAccountPostgreSqlIT"` | **76 / 76 PASSED** | Embedded PostgreSQL 14.22 + Flyway V18. Zero failures/errors/skips. |
| **Backend Authorization** | `.\mvnw.cmd test "-Dtest=BusinessAuthorizationServiceTest"` | **7 / 7 PASSED** | Validated `FINANCIAL_ACCOUNT_READ` grants for all 4 roles. |
| **Frontend Targeted Unit** | `node --test tests/accounts-read.test.mjs` | **24 / 24 PASSED** | Contract v1.3 discriminated type parsing, nullable opening balance, display helpers. |
| **Frontend Full Suite** | `pnpm test` | **60 / 60 PASSED** | 24 accounts-read tests + 36 baseline MVP tests. |
| **Type Check & Lint** | `tsc --noEmit`, `pnpm run lint` | **PASSED** | 0 TypeScript errors, 0 ESLint errors (1 existing SessionProvider baseline warning). |
| **Real Browser Integration** | `node tests/accounts-live.integration.mjs` | **EXIT 0 (12/12 PASSED)** | Real Spring Boot (8180) + Next.js (3180) + PostgreSQL 14.22 + Playwright/Edge. 0 blockers. Re-tested successfully following initial run exit 1 (BE-01/BE-02). |
| **Demo Mode Isolation** | `node tests/accounts-live.integration.mjs --demo` | **EXIT 0** | Verified EN/UR preview, zero private API requests. |

---

## 3. Staged Path Inventory

The following explicit paths are reviewed and staged for the checkpoint commit:

### Documentation, Contracts & Instructions
*   `AGENTS.md`
*   `backend/AGENTS.md`
*   `frontend/AGENTS.md`
*   `docs/PHASE2_IMPLEMENTATION_STATE.md`
*   `docs/phase2/adr/ADR_001_LEDGER_AUTHORITY_AND_OPENING_BALANCES_DRAFT.md`
*   `docs/phase2/contracts/ACCOUNTS_API_DRAFT.md`
*   `docs/phase2/contracts/C01_POSTING_DRAFT.md`
*   `docs/phase2/contracts/C02_MONTHLY_PROJECTION_DRAFT.md`
*   `docs/phase2/handoffs/ANTIGRAVITY_W0_02.md`
*   `docs/phase2/handoffs/ANTIGRAVITY_W0_03.md`
*   `docs/phase2/handoffs/ANTIGRAVITY_W0_04.md`
*   `docs/phase2/handoffs/ANTIGRAVITY_W1_01_BE_READ.md`
*   `docs/phase2/handoffs/ANTIGRAVITY_W1_01_BE_PARITY_FIX.md`
*   `docs/phase2/handoffs/ANTIGRAVITY_W1_01_BE_NULLABLE_OPENING.md`
*   `docs/phase2/handoffs/CODEX_W1_01_FE_READ.md`
*   `docs/phase2/handoffs/CODEX_W1_01_INTEGRATION.md`
*   `docs/phase2/handoffs/CODEX_W1_01_V13_RETEST.md`
*   `docs/phase2/handoffs/ACCOUNTS_READ_CHECKPOINT.md`

### Backend Persistence, Domain, API & Tests
*   `backend/src/main/resources/db/migration/V18__create_financial_accounts.sql`
*   `backend/src/main/java/com/app/sme_health_backend/identity/model/BusinessPermission.java`
*   `backend/src/main/java/com/app/sme_health_backend/identity/service/BusinessAuthorizationService.java`
*   `backend/src/main/java/com/app/sme_health_backend/shared/exception/GlobalExceptionHandler.java`
*   `backend/src/main/java/com/app/sme_health_backend/accounting/controller/AccountController.java`
*   `backend/src/main/java/com/app/sme_health_backend/accounting/dto/AccountDetailResponse.java`
*   `backend/src/main/java/com/app/sme_health_backend/accounting/dto/AccountListItemResponse.java`
*   `backend/src/main/java/com/app/sme_health_backend/accounting/dto/AccountListResponse.java`
*   `backend/src/main/java/com/app/sme_health_backend/accounting/entity/FinancialAccount.java`
*   `backend/src/main/java/com/app/sme_health_backend/accounting/exception/AccountValidationException.java`
*   `backend/src/main/java/com/app/sme_health_backend/accounting/model/AccountStatus.java`
*   `backend/src/main/java/com/app/sme_health_backend/accounting/model/AccountType.java`
*   `backend/src/main/java/com/app/sme_health_backend/accounting/model/BalanceStatus.java`
*   `backend/src/main/java/com/app/sme_health_backend/accounting/model/CurrencyRegistry.java`
*   `backend/src/main/java/com/app/sme_health_backend/accounting/repository/FinancialAccountRepository.java`
*   `backend/src/main/java/com/app/sme_health_backend/accounting/service/AccountReadService.java`
*   `backend/src/test/java/com/app/sme_health_backend/identity/service/BusinessAuthorizationServiceTest.java`
*   `backend/src/test/java/com/app/sme_health_backend/accounting/AccountControllerTest.java`
*   `backend/src/test/java/com/app/sme_health_backend/accounting/AccountReadServiceTest.java`
*   `backend/src/test/java/com/app/sme_health_backend/accounting/CurrencyRegistryTest.java`
*   `backend/src/test/java/com/app/sme_health_backend/accounting/FinancialAccountPostgreSqlIT.java`

### Frontend Application, Components, Adapters & Tests
*   `frontend/src/app/finance/accounts/page.tsx`
*   `frontend/src/components/finance/accounts/AccountDetail.tsx`
*   `frontend/src/components/finance/accounts/AccountMoneyValue.tsx`
*   `frontend/src/components/finance/accounts/AccountsList.tsx`
*   `frontend/src/components/finance/accounts/account-copy.ts`
*   `frontend/src/lib/api/dev-a/account-contracts.ts`
*   `frontend/src/lib/api/dev-a/accounts.ts`
*   `frontend/src/lib/utils/decimal-display.ts`
*   `frontend/tests/accounts-live.integration.mjs`
*   `frontend/tests/accounts-read.test.mjs`

### Preserved Untracked Artifacts (Not Staged)
*   `backend/.cp.txt`

---

## 4. Remaining Limitations & Outstanding Gates

1. **Opening-Only Limitation:** Backend balances reflect persisted opening balance (or pre-opening canonical zero). No transaction posting, movements, or ledger reconciliation.
2. **Fatima Migration Notification:** Migration V18 is reserved and verified locally. Formal notification to Fatima remains pending.
3. **Production Gates:** Container image builds, PostgreSQL 17 TLS/HBA, external provider deliveries, and baseline R01–R03 remain outstanding.
4. **Git Remote State:** Checkpoint is committed locally to branch `dev/suleman`. No remote push was performed.

Antigravity is **PAUSED**.
