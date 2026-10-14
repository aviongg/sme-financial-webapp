# Antigravity Task Handoff: A-W1-01-BE-READ

**Task ID:** `A-W1-01-BE-READ`  
**Task Title:** Implement the financial accounts read backend alongside Codex  
**Author:** Antigravity (Developer A Backend & Checkpoint Git Coordinator)  
**Date:** 14 October 2026  
**Canonical Branch:** `dev/suleman`  
**Baseline Commit SHA:** `b51e27696cccc59b94aa9d9c9388ad0faa5b4176`  
**Status:** `COMPLETED_BOUNDED_SLICE` (Currently `PAUSED`; zero Git mutations performed)  

---

## 1. Branch, Working Tree & Edited Paths

Both assistants share the existing local checkout on `dev/suleman`. No branches, worktrees, or clones were created, and no Git mutations (`git add`, `git commit`, `git push`, etc.) were executed while Codex works on `A-W1-01-FE-READ`.

### 1.1. Newly Authored Backend Files
1.  [`backend/src/main/resources/db/migration/V18__create_financial_accounts.sql`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/resources/db/migration/V18__create_financial_accounts.sql) — Flyway migration establishing the `financial_accounts` table, constraints, indexes, and runtime DML grants.
2.  [`backend/src/main/java/com/app/sme_health_backend/accounting/model/AccountType.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/model/AccountType.java) — Enum (`CASH`, `BANK`, `WALLET`).
3.  [`backend/src/main/java/com/app/sme_health_backend/accounting/model/AccountStatus.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/model/AccountStatus.java) — Enum (`ACTIVE`, `INACTIVE`, `ARCHIVED`).
4.  [`backend/src/main/java/com/app/sme_health_backend/accounting/model/BalanceStatus.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/model/BalanceStatus.java) — Enum (`AVAILABLE`, `UNAVAILABLE`).
5.  [`backend/src/main/java/com/app/sme_health_backend/accounting/model/CurrencyRegistry.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/model/CurrencyRegistry.java) — Currency metadata registry (10 currencies), exact scale formatting, 12-integer-digit capacity bound validation, overflow reason mapping, and account identifier masking.
6.  [`backend/src/main/java/com/app/sme_health_backend/accounting/entity/FinancialAccount.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/entity/FinancialAccount.java) — JPA Entity mapping `financial_accounts`.
7.  [`backend/src/main/java/com/app/sme_health_backend/accounting/exception/AccountValidationException.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/exception/AccountValidationException.java) — Structured validation exception carrying parameter-level errors map.
8.  [`backend/src/main/java/com/app/sme_health_backend/accounting/dto/AccountListItemResponse.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/dto/AccountListItemResponse.java) — List view DTO conforming strictly to v1.2 (`notes` omitted).
9.  [`backend/src/main/java/com/app/sme_health_backend/accounting/dto/AccountDetailResponse.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/dto/AccountDetailResponse.java) — Detail view DTO conforming strictly to v1.2 (`notes` included).
10. [`backend/src/main/java/com/app/sme_health_backend/accounting/dto/AccountListResponse.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/dto/AccountListResponse.java) — Paginated response envelope (`items`, `page`, `size`, `totalElements`, `totalPages`).
11. [`backend/src/main/java/com/app/sme_health_backend/accounting/repository/FinancialAccountRepository.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/repository/FinancialAccountRepository.java) — Tenant-scoped JPA repository with deterministic ordering (`createdAt ASC, id ASC`).
12. [`backend/src/main/java/com/app/sme_health_backend/accounting/service/AccountReadService.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/service/AccountReadService.java) — Service implementing repeatable-read snapshot isolation (`@Transactional(isolation = Isolation.REPEATABLE_READ)`), `Asia/Karachi` accounting cutoff, pre-opening canonical zero handling, and balance evaluation.
13. [`backend/src/main/java/com/app/sme_health_backend/accounting/controller/AccountController.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/controller/AccountController.java) — REST controller exposing `GET /api/finance/accounts` and `GET /api/finance/accounts/{id}` with input validation and role authorization.
14. [`backend/src/test/java/com/app/sme_health_backend/accounting/CurrencyRegistryTest.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/test/java/com/app/sme_health_backend/accounting/CurrencyRegistryTest.java) — 20 unit tests covering scales, canonical zeros, capacity bounds, precision overflows, and masking.
15. [`backend/src/test/java/com/app/sme_health_backend/accounting/AccountReadServiceTest.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/test/java/com/app/sme_health_backend/accounting/AccountReadServiceTest.java) — 7 unit tests covering cutoff semantics, future opening zeros, overflow states, and pagination preservation.
16. [`backend/src/test/java/com/app/sme_health_backend/accounting/AccountControllerTest.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/test/java/com/app/sme_health_backend/accounting/AccountControllerTest.java) — 12 WebMvc tests covering role matrix (`OWNER`, `ACCOUNTANT`, `MANAGER`, `VIEWER`), 400 validation error formatting, 403, 404, and 409 responses.
17. [`backend/src/test/java/com/app/sme_health_backend/accounting/FinancialAccountPostgreSqlIT.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/test/java/com/app/sme_health_backend/accounting/FinancialAccountPostgreSqlIT.java) — 6 integration tests against embedded disposable PostgreSQL validating Flyway V18 schema, foreign keys, unique constraints, tenant isolation, and `finsight_app` DML privileges.

### 1.2. Shared Backend Files Modified
1.  [`backend/src/main/java/com/app/sme_health_backend/identity/model/BusinessPermission.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/identity/model/BusinessPermission.java) — Added `FINANCIAL_ACCOUNT_READ` to the permission enum.
2.  [`backend/src/main/java/com/app/sme_health_backend/identity/service/BusinessAuthorizationService.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/identity/service/BusinessAuthorizationService.java) — Added `FINANCIAL_ACCOUNT_READ` to the role-permission sets for `ACCOUNTANT`, `MANAGER`, and `VIEWER` (`OWNER` receives all by default).
3.  [`backend/src/main/java/com/app/sme_health_backend/shared/exception/GlobalExceptionHandler.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/shared/exception/GlobalExceptionHandler.java) — Added `@ExceptionHandler(AccountValidationException.class)` mapping validation failures to HTTP 400 Bad Request with standardized `errors` map.
4.  [`backend/src/test/java/com/app/sme_health_backend/identity/service/BusinessAuthorizationServiceTest.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/test/java/com/app/sme_health_backend/identity/service/BusinessAuthorizationServiceTest.java) — Added explicit assertions verifying `FINANCIAL_ACCOUNT_READ` on `ACCOUNTANT`, `MANAGER`, and `VIEWER`.

### 1.3. Preserved Files & Untracked State
*   `backend/.cp.txt`: Preserved untouched.
*   Frontend files: All files created/edited by Codex in `frontend/**` (including `frontend/src/app/finance/`, `frontend/src/components/finance/`, `frontend/src/lib/api/dev-a/`, `frontend/src/lib/utils/decimal-display.ts`, `frontend/tests/accounts-read.test.mjs`) remain completely untouched.

---

## 2. Contract v1.2 Compatibility Assessment

The implementation matches `docs/phase2/contracts/ACCOUNTS_API_DRAFT.md` (v1.2) without discrepancy:

1.  **Endpoints:**
    *   `GET /api/finance/accounts`: Implemented with status (default `ACTIVE`), accountType, currency, page (default 0), and size (default 50) query parameters.
    *   `GET /api/finance/accounts/{id}`: Implemented with UUID validation and tenant isolation.
2.  **Temporal Semantics:**
    *   `observationAt`: Populated with UTC ISO-8601 timestamp truncated to seconds (e.g. `"2026-10-14T13:30:00Z"`).
    *   `effectiveCutoffDate`: Evaluated as `LocalDate.now(ZoneId.of("Asia/Karachi"))`.
3.  **Balance Derivation (Opening-Only Limitation):**
    *   **Limitation Explicitly Stated:** Because transaction posting and movement tables do not yet exist, `currentBalance` is derived exclusively from the persisted `opening_balance` and `opening_date`. No synthetic production ledger entries or hardcoded figures are inserted.
    *   If `openingDate <= effectiveCutoffDate`: `currentBalance` equals formatted opening balance string, `balanceStatus = AVAILABLE`, `balanceUnavailableReason = null`.
    *   If `openingDate > effectiveCutoffDate`: `currentBalance` equals canonical zero string (e.g. `"0.00"` for PKR, `"0"` for JPY), `balanceStatus = AVAILABLE`, `balanceUnavailableReason = null`.
    *   If balance magnitude exceeds 12 integer digits: `currentBalance = null`, `balanceStatus = UNAVAILABLE`, `balanceUnavailableReason = "CAPACITY_OVERFLOW"`.
    *   If fractional precision exceeds declared scale: `currentBalance = null`, `balanceStatus = UNAVAILABLE`, `balanceUnavailableReason = "PRECISION_OVERFLOW"`.
    *   If currency is unsupported: `currentBalance = null`, `balanceStatus = UNAVAILABLE`, `balanceUnavailableReason = "UNSUPPORTED_CURRENCY"`.
4.  **Pagination Preservation:**
    *   Requested `page` and `size` parameters are preserved in response envelopes for both empty results (`totalElements: 0, totalPages: 0`) and out-of-range requests.
5.  **Deterministic Ordering:**
    *   Queries enforce deterministic ordering: `ORDER BY a.createdAt ASC, a.id ASC`.
6.  **Identifier Masking:**
    *   `accountIdentifier` is masked as `****` + last 4 characters. Raw identifiers are never returned.
7.  **Error Envelopes:**
    *   Validation errors conform to `{ timestamp, status: 400, error: "Validation Failed", message: "...", errors: { ... } }`, directly parsed by `frontend/src/lib/api/client.ts`.
    *   Cross-business or non-existent account IDs return HTTP 404 with message `"Financial account not found"`.
    *   Unauthorized callers without `FINANCIAL_ACCOUNT_READ` receive HTTP 403 Forbidden.
    *   Missing active business context returns HTTP 409 `active_business_required`.

---

## 3. Remote Collision Check & Migration Evidence

*   **Remote Inspection:** Executed read-only check against GitHub API (`https://api.github.com/repos/aviongg/sme-financial-webapp/contents/backend/src/main/resources/db/migration`):
    *   `origin/main` (`b51e27696cccc59b94aa9d9c9388ad0faa5b4176`): migrations end at `V17`.
    *   `origin/dev/fatima` (`60f570c53fe0e35248544d24f4d3ac55459564ad`): migrations end at `V17`.
    *   **Collision Result:** `V18` is unreserved on all published branches.
    *   **Limitation:** Unpushed local commits on Fatima's workstation cannot be inspected remotely.
*   **Authored Migration:** `V18__create_financial_accounts.sql` applied successfully and tested against real PostgreSQL 14 in test fixtures.
*   **User Decisions (`SULEMAN_AUTHORIZED`):**
    *   Grant `FINANCIAL_ACCOUNT_READ` to `OWNER`, `ACCOUNTANT`, `MANAGER`, and `VIEWER`.
    *   Direct `/finance/accounts` route access without C03 module resolver or global navigation.
    *   Reserve V18 for financial accounts.
    *   Fatima has not yet been notified; these remain bounded to Dev A scope.

---

## 4. Test Verification Evidence

All 45 tests across unit, MockMvc, and PostgreSQL integration suites passed cleanly:

```bash
# Test execution command:
mvnw.cmd test -Dtest=CurrencyRegistryTest,AccountReadServiceTest,AccountControllerTest,FinancialAccountPostgreSqlIT
```

**Results:**
*   `com.app.sme_health_backend.accounting.CurrencyRegistryTest`: 20 runs, 0 failures, 0 errors, 0 skipped.
*   `com.app.sme_health_backend.accounting.AccountReadServiceTest`: 7 runs, 0 failures, 0 errors, 0 skipped.
*   `com.app.sme_health_backend.accounting.AccountControllerTest`: 12 runs, 0 failures, 0 errors, 0 skipped.
*   `com.app.sme_health_backend.accounting.FinancialAccountPostgreSqlIT`: 6 runs, 0 failures, 0 errors, 0 skipped (disposable PostgreSQL with Flyway V18).
*   **Total Suite:** 45 tests executed, 0 failures, 0 errors.

**Relevant Regression Suite:**
```bash
mvnw.cmd test -Dtest=BusinessAuthorizationServiceTest
```
*   `com.app.sme_health_backend.identity.service.BusinessAuthorizationServiceTest`: 7 runs, 0 failures, 0 errors.

---

## 5. Remaining Integration Checks & Consumer Status

1.  **Live-Browser Verification:** Real browser journey checks against Next.js frontend (`A-W1-01-FE-READ`) remain pending Codex's frontend completion.
2.  **Namespace Reconciliation:** Fatima's draft documents proposed `/api/v2`, whereas Codex and backend use `/api/finance/accounts`. Preserved as an A/B integration discussion item; no duplicate endpoints or aliases introduced.
3.  **Posting & Mutations:** Account creation (`POST`), movement posting, cutover, and balance adjustments are deferred to subsequent mutation slices (`A-W1-02-BE-POSTING` / C01).
4.  **Baseline Findings:** Baseline issues R01–R03 (reported in Fatima's handoff) were not modified or closed.

---

## 6. Assistant Execution State

*   **Current State:** **`PAUSED`**
*   **Git Status:** All new and modified files remain unstaged and uncommitted. No Git mutations performed. Ready for Git coordinator checkpoint review when Codex pauses.
