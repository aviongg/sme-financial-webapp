# Backend Nullable Opening Balance Handoff (Option A)

**Task:** `A-W1-01-BE-NULLABLE-OPENING`  
**Date:** 14 October 2026 (Asia/Karachi)  
**Status:** **BE_NULLABLE_OPENING_COMPLETED — PAUSED**  
**Role:** Antigravity (Developer A Backend & Git Coordinator)  
**Active Development Branch:** `dev/suleman`  
**Shared Checkout:** `C:\Users\PC\Projects\sme-financial-webapp`  
**Baseline Commit:** `b51e27696cccc59b94aa9d9c9388ad0faa5b4176`  
**Contract Revision:** [`docs/phase2/contracts/ACCOUNTS_API_DRAFT.md`](file:///c:/Users/PC/Projects/sme-financial-webapp/docs/phase2/contracts/ACCOUNTS_API_DRAFT.md) bumped to **v1.3**  

---

## 1. Executive Summary

Following project lead authorization (`SULEMAN_AUTHORIZED`), **Option A** has been formally adopted and implemented:
*   `openingBalance` is now nullable (`string | null`).
*   When opening data cannot be safely represented (precision overflow, capacity overflow, or unsupported currency metadata), both `openingBalance` and `currentBalance` evaluate to `null` with `balanceStatus = UNAVAILABLE`.
*   The backend never exposes malformed amounts, rounds/truncates excess precision, fabricates zero, or repairs stored metadata silently.
*   When opening data is valid but only current balance fails integrity reconciliation (`INTEGRITY_CHECK_FAILED`), the valid `openingBalance` string is preserved while `currentBalance` is `null`.
*   Valid future-opening accounts (`openingDate > effectiveCutoffDate`) retain their valid `openingBalance` string with an `AVAILABLE` `currentBalance` of canonical zero. Corrupt future accounts evaluate to `UNAVAILABLE` with null balances.
*   The accounts wire specification has been formally bumped from **v1.2 to v1.3**. This is an **explicit consumer-impacting wire contract change** requiring Codex frontend adaptation.

---

## 2. Invariants Implemented (`AccountReadService.java`)

```java
// Rule 1: AVAILABLE
// Valid currency/scale metadata; openingBalance and currentBalance are non-null exact currency amounts; reason = null.

// Rule 2: Invalid opening precision/capacity or currency/scale metadata
// openingBalance = null; currentBalance = null; balanceStatus = UNAVAILABLE.
// Exact reason: PRECISION_OVERFLOW, CAPACITY_OVERFLOW, or UNSUPPORTED_CURRENCY.

// Rule 3: Current balance integrity failure only (opening valid)
// openingBalance preserved as valid exact string; currentBalance = null; reason = INTEGRITY_CHECK_FAILED.

// Rule 4: Future opening date (openingDate > effectiveCutoffDate)
// Valid account: openingBalance = exact string, currentBalance = canonical zero ("0.00", "0", "0.000"), AVAILABLE.
// Corrupt account: openingBalance = null, currentBalance = null, UNAVAILABLE.
```

---

## 3. Changed Paths

1. `backend/src/main/java/com/app/sme_health_backend/accounting/service/AccountReadService.java`
   - Updated `computeDerivedBalance()` to emit `openingBalance: null` when opening data overflows or metadata is invalid.
2. `backend/src/test/java/com/app/sme_health_backend/accounting/AccountReadServiceTest.java`
   - Updated assertions across 6 test methods to assert `openingBalance: null` on capacity overflow, precision overflow, and currency metadata mismatch.
3. `backend/src/test/java/com/app/sme_health_backend/accounting/AccountControllerTest.java`
   - Added tests for `getAccountDetailUnavailableOpeningAndCurrent` (both null) and `getAccountDetailUnavailableCurrentOnly` (opening preserved, current null).
4. `backend/src/test/java/com/app/sme_health_backend/accounting/FinancialAccountPostgreSqlIT.java`
   - Updated real PostgreSQL tests to assert `openingBalance: null` on defective metadata, excess precision (`1.0001`), and capacity overflow.
5. `docs/phase2/contracts/ACCOUNTS_API_DRAFT.md`
   - Bumped to version **1.3**.
   - Updated Sections 2, 4, 5, and 9 documenting `openingBalance: string | null`, normative examples, and overflow/integrity invariants.
6. `docs/PHASE2_IMPLEMENTATION_STATE.md`
   - Bumped to version **1.4**.
   - Recorded Option A decision, task state, and contract v1.3 bump.
7. `docs/phase2/handoffs/ANTIGRAVITY_W1_01_BE_NULLABLE_OPENING.md` (this handoff document).

---

## 4. Fresh Test Results

Executed locally in `backend/` using the Maven wrapper:

```powershell
.\mvnw.cmd test "-Dtest=CurrencyRegistryTest,AccountReadServiceTest,AccountControllerTest,FinancialAccountPostgreSqlIT"
```

**Result:** `BUILD SUCCESS`  
**Tests run:** **76**  
**Failures:** **0**  
**Errors:** **0**  
**Skipped:** **0**  
**Execution Time:** 29.997s  

*Breakdown:*
*   `CurrencyRegistryTest`: 39 tests (all passed)
*   `AccountReadServiceTest`: 13 tests (all passed)
*   `AccountControllerTest`: 14 tests (all passed)
*   `FinancialAccountPostgreSqlIT`: 10 tests (all passed, backed by embedded PostgreSQL 14.22 with V1–V18 migrations)

---

## 5. Coordination, Blockers & State Confirmations

*   **Remaining Blocker for Integration:** Codex must adapt `frontend/src/lib/api/dev-a/account-contracts.ts` to consume Contract v1.3 (allowing `openingBalance: string | null` when `balanceStatus === 'UNAVAILABLE'`) and rerun the live browser integration harness (`accounts-live.integration.mjs`).
*   **Live Integration Verdict:** Live integration has **not** passed yet; the previous harness run exited 1 on BE-01 and BE-02. Codex will rerun after adapting the frontend validator.
*   **Frontend Files:** Untouched. No frontend code or test was edited.
*   **Git Mutations:** Zero Git mutations performed. Clean index, no staged files, no branch switching.
*   **Flyway Migrations:** V18 migration preserved untouched; no schema rewrite.
*   **Processes & Ports:** Integration ports 3180 and 8180 remain free.

Antigravity is **PAUSED**.
