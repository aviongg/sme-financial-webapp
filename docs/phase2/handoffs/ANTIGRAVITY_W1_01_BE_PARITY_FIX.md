# Backend Parity Fix Handoff (BE-01 & BE-02)

**Task:** `A-W1-01-BE-PARITY-FIX`  
**Date:** 14 October 2026 (Asia/Karachi)  
**Status:** **BE_PARITY_FIX_COMPLETED — PAUSED**  
**Role:** Antigravity (Developer A Backend & Git Coordinator)  
**Active Development Branch:** `dev/suleman`  
**Shared Checkout:** `C:\Users\PC\Projects\sme-financial-webapp`  
**Baseline Commit:** `b51e27696cccc59b94aa9d9c9388ad0faa5b4176`  

---

## 1. Executive Summary

This task resolves the backend defects identified by Codex during live browser integration (`CODEX_W1_01_INTEGRATION.md`):
1. **BE-01 (Currency Metadata Mismatch):** Fixed. Persisted accounts declaring a scale inconsistent with the supported registry (e.g. `currency = 'PKR'` with `currencyDecimals = 3`) are now rejected before serialization. They return `balanceStatus = UNAVAILABLE` with `balanceUnavailableReason = "UNSUPPORTED_CURRENCY"`, `currentBalance = null`, and `openingBalance` formatted in canonical decimal syntax without applying untrusted metadata. Both list and detail endpoints, as well as future-opening paths, enforce this check.
2. **BE-02 (Invalid Opening Value in Unavailable Response):** Addressed. Contract v1.2 was inspected; it does **not** specify a mechanism for representing an unrepresentable `openingBalance`. As required, the precise schema gap is reported below with proposed minimal contract amendments. No silent wire schema changes, nullability relaxations, or frontend validator weakenings were made. Every exposed monetary field is strictly validated; overflow accounts emit `balanceStatus = UNAVAILABLE` (`PRECISION_OVERFLOW` / `CAPACITY_OVERFLOW`) with `currentBalance = null`, preserving the raw canonical decimal string while the contract decision is pending.

**Zero Git mutations** were executed. Frontend files and Flyway migrations (V18) remain completely untouched.

---

## 2. Root Cause Analysis & Implemented Behavior

### 2.1. Defect 1: Stored Currency Scale Mismatch (BE-01)

#### Root Cause
- In [`CurrencyRegistry.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/model/CurrencyRegistry.java), `evaluateBalance` previously checked only the currency code string (`isSupported(currencyCode)`) and looked up its registry scale. The persisted `currencyDecimals` from the entity was ignored during balance evaluation.
- In [`AccountReadService.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/service/AccountReadService.java), `computeDerivedBalance()` formatted the balance using the registry's looked-up scale (scale 2 for PKR) and reported `balanceStatus = AVAILABLE`, while the DTO passed through the entity's stored `currencyDecimals = 3`.
- This violated wire contract v1.2 and the frontend validator (`frontend/src/lib/api/dev-a/account-contracts.ts:50-60`), which requires that when `currencyScales[currency] !== currencyDecimals`, the account must be evaluated as unsupported currency metadata (`balanceStatus = UNAVAILABLE`, `balanceUnavailableReason = "UNSUPPORTED_CURRENCY"`, `currentBalance = null`).

#### Implemented Behavior
1. **Registry Verification:** Added `CurrencyRegistry.isSupported(String currencyCode, Integer currencyDecimals)` which validates both currency existence and scale equality against `CURRENCY_SCALES`.
2. **Evaluator Overload:** Overloaded `CurrencyRegistry.evaluateBalance(BigDecimal amount, String currencyCode, Integer currencyDecimals)`. If `!isSupported(currencyCode, currencyDecimals)`, it immediately returns `BalanceEvaluation.unavailable("UNSUPPORTED_CURRENCY")`.
3. **Canonical Decimal Formatting:** Added `CurrencyRegistry.formatCanonicalDecimal(BigDecimal amount)` to ensure raw strings never contain exponent notation, leading plus signs, or negative zero (`amount.abs()` on zero).
4. **Service-Level Enforcement:** In `AccountReadService.computeDerivedBalance()`:
   - Validating currency metadata agreement is now the **first** operation.
   - If inconsistent, the service immediately returns `balanceStatus = UNAVAILABLE`, `balanceUnavailableReason = "UNSUPPORTED_CURRENCY"`, `currentBalance = null`, and `openingBalance = formatCanonicalDecimal(openingBalance)`.
   - This holds regardless of whether `openingDate <= effectiveCutoffDate` or `openingDate > effectiveCutoffDate`. Pre-opening accounts with defective currency metadata never claim `AVAILABLE` or `0.00`.
   - Fully satisfies frontend parser: `ensure(supported || a.balanceUnavailableReason === 'UNSUPPORTED_CURRENCY')` and `ensure(isCanonicalDecimalSyntax(a.openingBalance))`.

---

### 2.2. Defect 2: Invalid Opening Value in Unavailable Response (BE-02)

#### Root Cause
- When an account is seeded with an opening balance exceeding declared scale (e.g. `openingBalance = 1.0001` for PKR scale 2), PostgreSQL `NUMERIC(19,4)` preserves all 4 decimals.
- `evalOpening` detected precision overflow, setting `currentBalance = null` and `balanceUnavailableReason = "PRECISION_OVERFLOW"`.
- However, the wire response emitted `openingBalance: "1.0001"`. In contract v1.2, `openingBalance` is typed as a required `string` matching `validCurrencyAmount` (exact declared scale). The strict consumer validator rejected the response, causing the UI to show an invalid data error.

#### Contract Inspection & Schema Gap
- **Inspection Finding:** Contract v1.2 does **not** permit a valid representation for an invalid `openingBalance`.
  - Sections 5.1 and 5.2 specify `openingBalance` as a non-null exact decimal string.
  - Section 4 defines `balanceStatus` and `balanceUnavailableReason` exclusively for `currentBalance` (`currentBalance: null`).
  - There is no `openingBalanceStatus`, no `openingBalanceUnavailableReason`, and `openingBalance` cannot be `null`.
- **Schema Gap:** When persisted financial data contains a corrupted or over-precision opening balance, the wire protocol has no defined way to convey an unavailable opening balance without either:
  1. Violating the exact scale regex on `openingBalance`.
  2. Rounding/truncating the value (strictly prohibited by financial discipline).
  3. Inventing an unapproved field or making `openingBalance` nullable.

#### Proposed Smallest Contract Changes (Decision Pending)
We submit the following options for team and lead sign-off:

*   **Option A (Recommended — Nullable `openingBalance` on overflow):**
    Amend contract v1.3 wire schema:
    ```typescript
    openingBalance: string | null;
    ```
    When `balanceStatus === 'UNAVAILABLE'` and the opening balance itself causes `PRECISION_OVERFLOW` or `CAPACITY_OVERFLOW`, emit `openingBalance: null`. If opening balance is valid but ledger movements cause overflow, emit valid `openingBalance` string and `currentBalance: null`.
*   **Option B (Permit canonical decimal syntax on `openingBalance` when `UNAVAILABLE`):**
    In `account-contracts.ts` line 59:
    ```typescript
    if (supported && a.balanceStatus === 'AVAILABLE') ensure(validCurrencyAmount(a.openingBalance, a.currency, a.currencyDecimals as number));
    else ensure(isCanonicalDecimalSyntax(a.openingBalance));
    ```
    This allows the backend to return the raw persisted plain string (`"1.0001"`) for audit inspection while `currentBalance` is `null` and `balanceStatus` is `UNAVAILABLE`.
*   **Option C (HTTP 422 / 500 Data Integrity Error):**
    If an account's persisted opening balance cannot be formatted at its declared scale, return HTTP `422 Unprocessable Entity` or `500 Data Integrity Error` instead of a 200 OK DTO with invalid monetary fields.

#### Current Behavior Maintained
While the contract decision is pending:
- Did not make `openingBalance` nullable in code or contract.
- Did not weaken frontend validation.
- All monetary fields are checked before claiming `AVAILABLE`.
- Fallback uses canonical plain string without rounding or truncation.

---

## 3. Files Modified

All edits are strictly within permitted backend accounting and test paths:

1. `backend/src/main/java/com/app/sme_health_backend/accounting/model/CurrencyRegistry.java`
   - Added `isSupported(String currencyCode, Integer currencyDecimals)`.
   - Added `formatCanonicalDecimal(BigDecimal amount)`.
   - Overloaded `evaluateBalance(BigDecimal amount, String currencyCode, Integer currencyDecimals)`.
2. `backend/src/main/java/com/app/sme_health_backend/accounting/service/AccountReadService.java`
   - Updated `computeDerivedBalance` to validate currency metadata against registry before serialization.
   - Enforced `UNAVAILABLE` (`UNSUPPORTED_CURRENCY`) for mismatched metadata across list, detail, and pre-opening dates.
3. `backend/src/test/java/com/app/sme_health_backend/accounting/CurrencyRegistryTest.java`
   - Added parameterized tests for `isSupported(code, decimals)` across all 10 currencies and scale mismatches.
   - Added tests for `formatCanonicalDecimal` and 3-arg `evaluateBalance`.
4. `backend/src/test/java/com/app/sme_health_backend/accounting/AccountReadServiceTest.java`
   - Added tests for currency metadata mismatch (PKR scale 3), future-opening with metadata mismatch, future-opening with precision overflow, excess precision opening amount (`1.0001`), valid BHD 3-decimal, valid JPY 0-decimal, and negative balances.
5. `backend/src/test/java/com/app/sme_health_backend/accounting/AccountControllerTest.java`
   - Added test for UNAVAILABLE balance detail response matching wire contract serialization.
6. `backend/src/test/java/com/app/sme_health_backend/accounting/FinancialAccountPostgreSqlIT.java`
   - Added real PostgreSQL tests:
     - `persistedDefectiveMetadataReturnsUnavailableWithUnsupportedCurrency`: verified detail and list endpoints return `UNAVAILABLE` with `UNSUPPORTED_CURRENCY` and `currentBalance = null`.
     - `persistedExcessPrecisionPreservedAndEvaluatedAsPrecisionOverflow`: verified PostgreSQL `NUMERIC(19,4)` preserves `1.0001` and service reports `PRECISION_OVERFLOW`.
     - `persistedCapacityOverflowEvaluatedAsCapacityOverflow`: verified 13-digit balance returns `CAPACITY_OVERFLOW`.
     - `persistedExactZeroTwoThreeDecimalsAndNegativeValues`: verified JPY (`999999999999`), BHD (`1250.375`), negative PKR (`-2500.50`), and future PKR (`500.00` with `0.00` current balance) in PostgreSQL.
7. `docs/phase2/handoffs/ANTIGRAVITY_W1_01_BE_PARITY_FIX.md` (this handoff document).

---

## 4. Verification Evidence

### Fresh Local Test Execution

Executed synchronously in `backend/` using project Maven wrapper:

#### Run 1: Accounting Core & PostgreSQL Integration Tests
```powershell
.\mvnw.cmd test "-Dtest=CurrencyRegistryTest,AccountReadServiceTest,AccountControllerTest,FinancialAccountPostgreSqlIT"
```
**Result:** `BUILD SUCCESS`  
**Total Tests Run:** **75**  
**Failures:** **0**  
**Errors:** **0**  
**Skipped:** **0**  
**Time:** 27.925s  

*Breakdown by Suite:*
- `CurrencyRegistryTest`: 39 tests (all passed)
- `AccountReadServiceTest`: 13 tests (all passed)
- `AccountControllerTest`: 13 tests (all passed)
- `FinancialAccountPostgreSqlIT`: 10 tests (all passed, backed by real embedded PostgreSQL 14.22 with V1–V18 Flyway migrations)

#### Run 2: Business Authorization Tests
```powershell
.\mvnw.cmd test "-Dtest=BusinessAuthorizationServiceTest"
```
**Result:** `BUILD SUCCESS`  
**Total Tests Run:** **7**  
**Failures:** **0**  
**Errors:** **0**  
**Skipped:** **0**  
**Time:** 3.968s  

**Cumulative Backend Passing Tests:** **82 / 82**

---

## 5. Coordination & State

- **Frontend Validation:** Not modified. All existing frontend files, validators, and tests remain intact.
- **Git State:** Clean index. No `git add`, `git commit`, `git push`, `git stash`, or branch switching performed.
- **Migrations:** V18 Flyway migration preserved untouched; no schema rewrite.
- **Ports & Harness:** Ports 3180 and 8180 were not occupied. Live browser harness was not run.
- **Integration Verdict:** Withheld (`INTEGRATION_VERIFIED` is **not** marked).
- **Next Step:** Codex to resume and rerun live integration harness against updated backend classes.

Antigravity is **PAUSED**.
