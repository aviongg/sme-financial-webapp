# Financial Transactions & Event Ledger Handoff

**Task:** `A-W1-03-BE-TRANSACTIONS`
**Date:** 14 October 2026 (Asia/Karachi)
**Author:** Antigravity (Developer A Backend Coordinator)
**Target Assistant:** Codex (Developer A Frontend)
**Status:** **BACKEND_VERIFIED — PAUSED**
**Governing Contract:** [`docs/phase2/contracts/C01_POSTING_DRAFT.md`](file:///C:/Users/PC/Projects/sme-financial-webapp/docs/phase2/contracts/C01_POSTING_DRAFT.md) (v2.0)
**Authored Flyway Migration:** `V20__create_financial_transactions.sql`

---

## 1. Contract Announcement (Revision 2.0)

Antigravity has published the authoritative, bounded wire contract for Phase 2 financial transactions in [`C01_POSTING_DRAFT.md`](file:///C:/Users/PC/Projects/sme-financial-webapp/docs/phase2/contracts/C01_POSTING_DRAFT.md) (v2.0).

### Key Contract Specifications:
1. **Endpoint `POST /api/finance/transactions`:**
   - Headers: `Idempotency-Key` (required string 1..100), `X-XSRF-TOKEN` (CSRF), `Content-Type: application/json`.
   - Body: `accountId` (UUID), `eventType` (`DIRECT_SALE_RECEIPT` | `OPERATING_EXPENSE_PAYMENT`), `amount` (exact 2-decimal string `> 0`, max capacity 12 digits), `effectiveDate` (`YYYY-MM-DD`, `openingDate <= effectiveDate <= today(Asia/Karachi)`), `description` (optional string $\le 255$).
   - Response: `201 Created` returning `TransactionDetailResponse` with transaction ID, movement details, actor ID, resulting balance, and ISO timestamps.
   - Idempotent replay: Identical key + hash returns original 201; altered payload returns 409 Conflict.
2. **Endpoint `GET /api/finance/transactions/{id}`:**
   - Returns single `TransactionDetailResponse` (or 404 if not found or outside active business).
3. **Endpoint `GET /api/finance/transactions`:**
   - Paginated list with optional filters: `accountId`, `eventType`, `startDate`, `endDate`, `page`, `size`.
   - Deterministic ordering: `effectiveDate DESC, createdAt DESC, id DESC`.
4. **Permissions:**
   - `FINANCIAL_TRANSACTION_READ`: Granted to `OWNER`, `ACCOUNTANT`, `MANAGER`, and `VIEWER`.
   - `FINANCIAL_TRANSACTION_POST`: Granted strictly to `OWNER` and `ACCOUNTANT` only.
5. **Account Reads Incorporating Movements:**
   - `GET /api/finance/accounts` and `GET /api/finance/accounts/{id}` derive `currentBalance` as `openingBalance + sum(movements <= cutoffDate)`.
   - `BANK`/`WALLET` may be negative; `CASH` rejects outflows that would cause any subsequent chronological balance to fall below zero.
6. **Financial Data Isolation:**
   - Monthly health scores, `MonthlyRecord` summaries, and recommendations are untouched in this slice. Existing snapshots remain authoritative for monthly intelligence.

---

## 2. Implementation Deliverables

### A. Database Migrations
- `V20__create_financial_transactions.sql`:
  - `financial_transactions` table: stores business-scoped transaction events with immutable amounts, direction, currency, actor metadata, and timestamps.
  - `account_movements` table: stores ledger movements linked to transactions and accounts with direction (`INCREASE`/`DECREASE`) and immutable amounts.
  - Check constraints: positive amounts, exact 2-decimal places, max capacity (`< 10^12`), allowed event types (`DIRECT_SALE_RECEIPT`, `OPERATING_EXPENSE_PAYMENT`), and allowed directions.
  - Foreign key and index strategy: business and account composite indexes for query performance and deterministic sorting (`effective_date DESC, created_at DESC, id DESC`).
  - DML permissions granted to `finsight_app`.

### B. Security & Authorization
- Added `FINANCIAL_TRANSACTION_READ` and `FINANCIAL_TRANSACTION_POST` to [`BusinessPermission.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/identity/model/BusinessPermission.java).
- Mapped in [`BusinessAuthorizationService.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/identity/service/BusinessAuthorizationService.java):
  - `OWNER`, `ACCOUNTANT`, `MANAGER`, `VIEWER` -> `FINANCIAL_TRANSACTION_READ`.
  - `OWNER`, `ACCOUNTANT` -> `FINANCIAL_TRANSACTION_POST`.
- Pre-authorized controller methods via `@PreAuthorize("hasAuthority('FINANCIAL_TRANSACTION_POST')")` and `@PreAuthorize("hasAuthority('FINANCIAL_TRANSACTION_READ')")`.
- Verified actor/business security audit event logging (`FINANCIAL_TRANSACTION_POSTED`) recording distinct `actor_user_id` and `business_id` without cross-contamination.

### C. Backend Application Services & Endpoints
- **Entities:** [`FinancialTransaction.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/entity/FinancialTransaction.java), [`AccountMovement.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/entity/AccountMovement.java).
- **Repositories:** [`FinancialTransactionRepository.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/repository/FinancialTransactionRepository.java), [`AccountMovementRepository.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/repository/AccountMovementRepository.java).
- **Pessimistic Locking:** `findForUpdateByIdAndBusinessId` in `FinancialAccountRepository` ensures serializable account movement updates and concurrent balance safety.
- **Posting Service:** [`TransactionPostService.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/service/TransactionPostService.java):
  - Durable idempotency using `financial_idempotency_records` (`operation = "FINANCIAL_TRANSACTION"`).
  - Validation: PKR accounts only, active status, effective date within `[openingDate, today(Karachi)]`, amount positive with scale 2.
  - Cash deficit check: Evaluates complete chronological movement sequence to prevent backdated events from rendering intermediate cash balances negative.
  - Single balance authority: `currentBalance` calculated dynamically from opening balance and effective movements.
- **Read Service:** [`TransactionReadService.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/service/TransactionReadService.java):
  - Filtered transactions with deterministic ordering using Spring Data `JpaSpecificationExecutor`.
  - Batch enrichment of account metadata and movement sums.
- **Account Reads Incorporation:** [`AccountReadService.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/service/AccountReadService.java):
  - Updated both list and detail queries to compute `currentBalance = openingBalance + netMovements <= cutoffDate`.
- **Controller:** [`TransactionController.java`](file:///C:/Users/PC/Projects/sme-financial-webapp/backend/src/main/java/com/app/sme_health_backend/accounting/controller/TransactionController.java).

---

## 3. Verification & Evidence

### Test Execution Summary
All tests executed cleanly with zero failures and zero errors:
- **Comprehensive Accounting Suite:** **131 tests passed** (0 failures, 0 errors, 0 skipped).
- **PostgreSQL Integration Suite (`FinancialTransactionPostgreSqlIT`):** **8/8 passed** on disposable PostgreSQL instance.
- **PostgreSQL Account Integration Suite (`FinancialAccountPostgreSqlIT`):** **7/7 passed**.
- **Transaction Controller Tests (`TransactionControllerTest`):** **8/8 passed**.
- **Transaction Posting Logic Tests (`TransactionPostServiceTest`):** **10/10 passed**.
- **Authorization Service Tests (`BusinessAuthorizationServiceTest`):** **7/7 passed**.

### Verified Scenarios:
1. `postDirectSaleReceipt_persistsAndUpdatesAccountRead`: Verified direct sale receipt posting increases bank balance, creates transaction and movement, updates account read balance, and emits `FINANCIAL_TRANSACTION_POSTED` audit event with distinct actor/business IDs.
2. `postOperatingExpense_decreasesBalanceAndPermitsNegativeBank`: Verified operating expense payment posting decreases bank balance and correctly allows negative bank balance.
3. `sameKeySamePayload_returnsCachedResponse_andAuthorizedReplay`: Verified durable idempotency replay returns original `201 Created` response.
4. `sameKeyDifferingPayload_throwsConflict`: Verified same key with altered payload throws `409 Conflict`.
5. `concurrentPostings_areSerializedByAccountLock`: Verified simultaneous concurrent transactions on the same account serialize safely without lost movements.
6. `cashSequenceDeficit_rejectsBackdatedOutflowCausingIntermediateNegative`: Verified that a backdated outflow causing intermediate cash balance to drop below zero is strictly rejected with a clear chronological deficit error.
7. `postingDoesNotAffectMonthlyRecordsOrScores`: Verified that posting transactions leaves `monthly_records`, `score_results`, `insights`, and `recommendations` strictly untouched.
8. `multiTenantIsolation`: Verified that Business B cannot read or post transactions belonging to Business A.
9. `listTransactions_filteredAndOrdered`: Verified filtered, paginated transaction history with deterministic ordering (`effectiveDate DESC, createdAt DESC, id DESC`).

---

## 4. Current Limitations & Handoff to Codex

1. **Frontend Scope Ready:**
   - Codex is unblocked to build frontend routes (`/finance/transactions`, `/finance/transactions/new`, etc.) and UI against contract `C01_POSTING_DRAFT.md` (v2.0).
2. **Currency & Classification Scope:**
   - Initial posting is bounded to PKR accounts. Non-PKR accounts remain readable but reject posting in this slice.
   - Initial event types are bounded to `DIRECT_SALE_RECEIPT` and `OPERATING_EXPENSE_PAYMENT`.
   - Transfers, reversals, FX conversions, AR/AP, customer advances, supplier settlements, and commissions are explicitly excluded from this slice.
3. **Monthly Financial Snapshots:**
   - Monthly health calculations still draw from existing monthly record snapshots; ledger-mode integration will occur in a later scheduled slice.
4. **State:**
   - Backend development completed and verified.
   - Git working tree remains uncommitted per working rules until an authorized Git checkpoint task.
   - Assistant status: **PAUSED**.

---

## 5. Frontend and Integration — Codex A-W1-03-INTEGRATION

**Date:** 14 October 2026 (Asia/Karachi)
**Verdict:** **BLOCKED_BY_BACKEND_INVARIANTS — PAUSED**. Do not mark INTEGRATION_VERIFIED. Normal bounded journeys passed, but the real harness intentionally exits 1 on the backend failures below. Earlier backend evidence above is preserved and was not rerun by Codex.

**Checkout / branch / HEAD:** `C:\Users\PC\Projects\sme-financial-webapp`, `dev/suleman`, `68d2995ca3548e57256b0be5f8a44322e11d8f7c`. Consumed C01 v2.0 and actual producer DTO/services read-only. POST/list/detail field and enum shapes match; nullable description and omitted nested list movements are handled. No status enum is invented. Existing X-XSRF-TOKEN transport is preserved. Transaction read resultingBalance is a current account read value; it is not presented as a historical running balance.

### Exact changes made by Codex in this integration task

- `frontend/src/components/finance/accounts/AccountsList.tsx`: restore creation focus after the committed DOM enables the trigger.
- `frontend/src/components/finance/transactions/TransactionsList.tsx`: restore posting focus after actual account refresh and trigger remount.
- `frontend/tests/accounts-live.integration.mjs`: preserve account read/create journeys; add transaction HTTP/DB/browser checks and independent localhost review mode. Delayed route callbacks use process timers so closed pages cannot cause an unhandled callback failure.
- `docs/phase2/handoffs/TRANSACTIONS.md`: append this clearly labelled section only.

No frontend financial validator was weakened; no backend, migration, contract, shared-state, auth/transport/navigation, dependency, manifest, lockfile or environment file was edited by Codex. Existing teammate work and backend/.cp.txt were preserved. No Git mutations.

### Fresh commands and results

All pnpm commands used process-only `pnpm_config_verify_deps_before_run=false`.

| Command (frontend/) | Result |
| --- | --- |
| `pnpm exec node --test tests/transactions.test.mjs` | Exit 0; 25 passed |
| `pnpm test` | Exit 0; 108 passed; zero failures/skips |
| `pnpm exec tsc --noEmit --incremental false` | Exit 0 |
| `pnpm run lint` | Exit 0; existing SessionProvider.tsx:204 warning only |
| Live build, demo build, restored live build | All exit 0; existing middleware deprecation notice |
| `node tests/accounts-live.integration.mjs` | Final exit **1**; 28 grouped checks completed, five recorded failures in three categories below; integrated=false |
| `node tests/accounts-live.integration.mjs --demo` | Exit 0; accounts and transactions previews, zero private API requests |
| `node tests/accounts-live.integration.mjs --review` | Exit 0; separate review services remain running by explicit user request |

Actual acceptance environment: real Spring, production Next/Edge, disposable PostgreSQL **14.22**, migration head **V20**, ports 8180/3180. No synthetic API responses, security bypass, persistent user-data changes or external deployment. Backend Surefire reports were inspected only; their recorded results remain prior Antigravity evidence, distinct from this task's fresh real harness. The harness explicitly invokes the compiled application and real migrations rather than assuming an IT filename implies execution.

One initial run exposed the frontend creation-focus timing defect, now fixed and passed. Another run completed ordinary posting but terminated on a closed-page timer in the new expiry route callback; the callback was corrected and the complete combined run then finished normally with exit 1 solely for the recorded producer violations. Failed runs are not represented as passes.

### Real HTTP/database evidence

- Valid CSRF/authorized posting succeeds; absent/invalid CSRF and MANAGER/VIEWER posting return 403 without event/movement/idempotency/audit writes. Foreign account 404; inactive/non-PKR, before-opening/future dates, nonnumeric/zero/negative/excess-precision/excess-capacity amounts reject without writes.
- Eight concurrent identical-key requests return one event ID and one movement ID. Identical replay returns the original body; altered payload returns 409. Eight distinct simultaneous requests preserve all eight contributions: opening 1000.00 becomes 1009.00 including the one same-key event.
- Independent main fixture: opening **100000.00**, OWNER sale **20000.00**, ACCOUNTANT expense **5000.00**, account read **115000.00**. Replay adds nothing. Successful/rejected ordinary reads remain valid AVAILABLE exact strings; original unsupported-metadata/opening-precision fixtures retain valid v1.3 null/null UNAVAILABLE behavior.
- CASH fixture opening 100.00; earlier expense 80.00 and later sale 100.00 yield current 120.00. A backdated expense 30.00 is rejected for an intermediate negative point even though its final balance would remain positive; rejection leaves current 120.00 and all write counts unchanged.
- Mandatory audit rows were checked directly against actual transaction ID, actor user ID and business ID, with exactly one matching audit for replayed events.
- Nonempty monthly record and persisted score were established before testing. Full-row count/fingerprints of monthly_records, score_results, insights and recommendations are identical after transactions/retries and after final expiry. This does not assert insights/recommendations were necessarily nonempty.

### Real browser evidence

All prior account read/create checks passed. OWNER and ACCOUNTANT both exercised supported posting choices; history/account/date/event filters, deterministic pagination/page reset, empty-date range, persisted details, exact PKR amounts, actual account refresh, double submission, keyboard/Sheet focus and obsolete selection cancellation passed. MANAGER/VIEWER read-only presentation passed. English/Urdu cutover notice, RTL/mobile bounds and real committed/lost response recovery with the original key and identical payload passed. Business-switch and session-expiry tests discarded delayed real posting results. Backend outage remained an error, not empty/mock data. Screenshots/results are ignored artifacts in `frontend/test-artifacts/accounts-live/`.

### Backend blockers and requested corrections

1. **BE-TX-CAPACITY — atomicity/capacity:** with opening 999999999999.99, sale 0.01 returns **201** instead of rejecting. Global event/movement/idempotency/audit counts change **11:11:11:11 -> 12:12:12:12**. TransactionPostService.java:291–292 formats the aggregate without validating supported capacity. Validate the aggregate, including both signs, before commit and reject through the stable validation envelope with transaction rollback. Do not clamp or rely on the frontend to calculate a balance.
2. **BE-TX-NULLABILITY — v1.3 account parity:** that overflow read returns openingBalance="999999999999.99", currentBalance=null, UNAVAILABLE/CAPACITY_OVERFLOW. AccountReadService.java:248–253 preserves the opening for this reason; the established v1.3 consumer does not accept this combination. Align the documented/producer nullability with the approved account wire contract, or obtain an explicit revised contract/consumer ownership window. Strict frontend validation remains intact and fails visibly for this response.
3. **BE-TX-SYNTAX — malformed input writes:** actual HTTP accepts **"1e2"**, **"+1.00"** and **"01.00"**, each with 201 and another event/movement/idempotency/audit row. TransactionPostService.java:118 parses BigDecimal before enforcing canonical decimal syntax. Validate canonical positive decimal-string syntax before numeric parsing; do not silently normalize malformed requests. Frontend exact-input checks already reject these forms. These probes were isolated in a separate integrity-test business so ordinary browser journeys could continue.

### Live application left running for user review

The acceptance environment was closed. A **fresh separate isolated PostgreSQL V20 cluster** was then provisioned through normal application APIs and left alive in an independent Java runtime; it does not depend on the acceptance Node process remaining alive. The restored live build is served at **http://127.0.0.1:3180**, with direct accounts and transactions routes. This startup is not financial acceptance.

- Next PID **23052**, port **3180**, bind 127.0.0.1.
- Spring/independent runtime PID **31364**, port **8180**, bind 127.0.0.1.
- Isolated PostgreSQL PID **32024**, port **54550**, binds only 127.0.0.1 and ::1; no public database listener.
- Review business/accounts/events are clearly labelled LOCAL REVIEW. Sample bank opening 100000.00 plus sale 20000.00 minus expense 5000.00 reads 115000.00. Additional JPY/BHD/CASH accounts are present. A separately entered synthetic monthly snapshot and backend-calculated score make the dashboard reviewable; transactions did not generate that snapshot.
- Sign in using the private user-only local credentials file outside the repository: `C:\Users\PC\Documents\Codex\2026-10-14\you-are-joining-finsight-as-the\work\finsight-review-v20\credentials.txt`. No credential values, tokens or MFA secrets are copied into logs, committed files or this handoff. This identity was registered through the normal application; complete any app security/MFA prompts, or enroll MFA in Settings. Users may register their own identity/business normally as well.
- Stop only this environment using `C:\Users\PC\Documents\Codex\2026-10-14\you-are-joining-finsight-as-the\work\finsight-review-v20\stop-review.ps1`. It validates the recorded PID command lines, stops only task-owned Next, and signals the independent Java runtime to close Spring and its PostgreSQL cluster gracefully. Process metadata is in the same private directory, without passwords.
- Both frontend transaction route and backend CSRF endpoint returned HTTP 200 after the setup process exited; listeners/PIDs were independently checked. OCR/FastAPI and external SMTP/Vision/WhatsApp providers were not started/verified; WhatsApp scheduling is disabled in this isolated review setup. No public deployment/tunnel/hosting.

Normal bounded application features are available for exploration, with the capacity/nullability and malformed-HTTP-input blockers above still unresolved. Monthly aggregation/cutover, full F01 and production acceptance remain outside scope. **PAUSED**, leaving only the expressly requested review services running.

---

## 6. Records Period Summary Contract (Revision 1.0 — CONTRACT_READY)

**Task:** `A-RECORDS-01-BE`
**Date:** 14 October 2026 (Asia/Karachi)
**Author:** Antigravity (Developer A Backend Coordinator)
**Target Assistant:** Codex (Developer A Frontend)
**Status:** **CONTRACT_READY** (Runtime implementation in progress)

### 6.1. Endpoint & Parameters
*   **Method & Path:** `GET /api/finance/transactions/summary`
*   **Security & Permissions:**
    *   Requires active session cookie and valid CSRF token.
    *   Pre-authorized with `FINANCIAL_TRANSACTION_READ` (permitted for `OWNER`, `ACCOUNTANT`, `MANAGER`, `VIEWER`).
    *   Server-resolved active business (`ActiveBusinessContext`). No client tenant override.
*   **Route Precedence:** Server registers `/summary` with explicit precedence before `/{id}` (`/{id:[0-9a-fA-F\\-]{36}}`) to guarantee zero route collision.
*   **Query Parameters:**
    *   `accountId` (optional, UUID): Scope summary to a single financial account within the active business. If invalid UUID format, returns HTTP 400 with `errors.accountId`. If valid UUID but account not found or outside active business, returns empty summary.
    *   `eventType` (optional, string): Case-insensitive. Supported values: `DIRECT_SALE_RECEIPT`, `OPERATING_EXPENSE_PAYMENT`. If invalid, returns HTTP 400 with `errors.eventType`.
    *   `startDate` (optional, string `YYYY-MM-DD`): Inclusive start date (Asia/Karachi calendar). Returns HTTP 400 with `errors.startDate` if invalid date format.
    *   `endDate` (optional, string `YYYY-MM-DD`): Inclusive end date (Asia/Karachi calendar). Returns HTTP 400 with `errors.endDate` if invalid date format.
    *   **Range Validation:** If both `startDate` and `endDate` are provided and `startDate > endDate`, returns HTTP 400 with `errors.startDate` ("Start date cannot be after end date").

### 6.2. Date Semantics & Calculation Boundaries
1.  **Accounting Dates:** Filter criteria evaluate transaction `effective_date`. All evaluations occur relative to `Asia/Karachi` business days.
2.  **Open Boundaries:** If `startDate` or `endDate` is omitted, the range is unbounded on that side. Omission of both spans all historical recorded transactions for the active business.
3.  **Whole-Dataset Scope:** Aggregations cover the complete filtered business dataset, independent of pagination limits or page boundaries.
4.  **No Financial Inference:** Returns actual posted cash-activity totals only. Does not compute or expose accruals, receivables, payables, inventory, projected cash flows, or net profit.

### 6.3. Response Schema (`200 OK`)
```json
{
  "currency": "PKR",
  "startDate": "2026-10-01",
  "endDate": "2026-10-31",
  "accountId": "e4f8d521-7291-49b0-bc34-927361a90c12",
  "eventType": null,
  "transactionCount": 14,
  "moneyReceived": "125000.00",
  "moneyPaid": "45000.00",
  "netCashMovement": "80000.00",
  "observationAt": "2026-10-14T20:30:00Z"
}
```

### 6.4. Field Definitions & Nullability
*   `currency` (string, non-null): `"PKR"`.
*   `startDate` (string, nullable): Effective filter start date (`YYYY-MM-DD`) or `null` if unbounded.
*   `endDate` (string, nullable): Effective filter end date (`YYYY-MM-DD`) or `null` if unbounded.
*   `accountId` (string UUID, nullable): Account ID filter or `null` if cross-account.
*   `eventType` (string, nullable): Event type filter name or `null` if all types included.
*   `transactionCount` (number, integer $\ge 0$, non-null): Total number of transactions matching criteria.
*   `moneyReceived` (string, non-null): Exact two-decimal string $\ge 0$. Total money received via `DIRECT_SALE_RECEIPT` events in the filtered range. Default `"0.00"`.
*   `moneyPaid` (string, non-null): Exact two-decimal string $\ge 0$. Total money paid via `OPERATING_EXPENSE_PAYMENT` events in the filtered range. Default `"0.00"`.
*   `netCashMovement` (string, non-null): Exact two-decimal string = `moneyReceived - moneyPaid`. Can be positive, zero (`"0.00"`), or negative (e.g. `"-15000.00"`).
*   `observationAt` (string ISO-8601 UTC timestamp, non-null): Server timestamp of summary observation truncated to whole seconds.

### 6.5. Empty Filter Match Example (`200 OK`)
When zero transactions match the requested filter criteria:
```json
{
  "currency": "PKR",
  "startDate": "2026-11-01",
  "endDate": "2026-11-30",
  "accountId": null,
  "eventType": null,
  "transactionCount": 0,
  "moneyReceived": "0.00",
  "moneyPaid": "0.00",
  "netCashMovement": "0.00",
  "observationAt": "2026-10-14T20:30:00Z"
}
```

### 6.6. Error Responses
*   `400 Bad Request`: Validation failure on query parameters:
    ```json
    {
      "errors": {
        "startDate": "Invalid date format. Expected YYYY-MM-DD",
        "eventType": "Invalid eventType: UNKNOWN. Supported types: DIRECT_SALE_RECEIPT, OPERATING_EXPENSE_PAYMENT"
      }
    }
    ```
*   `401 Unauthorized`: Unauthenticated request.
*   `403 Forbidden`: Authenticated user lacking `FINANCIAL_TRANSACTION_READ` permission or missing active business context.

---

## 7. Backend Implementation & Regression Verification — Antigravity A-RECORDS-01-BE

**Date:** 14 October 2026 (Asia/Karachi)
**Status:** **RUNTIME_READY — PAUSED** (Ready for Codex combined live harness verification)

### 7.1. Resolution of Reported Integrity Defects

1.  **BE-TX-CAPACITY (Aggregate Account Capacity Overflow):**
    *   `TransactionPostService` simulates the full chronological movement sequence and validates that neither any intermediate balance nor the resulting final balance exceeds aggregate capacity (`magnitude >= 10^12`).
    *   Attempting to post a transaction causing overflow (e.g. `+0.01` to an account at `999,999,999,999.99` or exceeding `-999,999,999,999.99` for BANK/WALLET) is rejected with HTTP `400 Bad Request` (`errors.amount`).
    *   **Atomic Rollback Verified:** Persistence paths (`financial_transactions`, `account_movements`, `financial_idempotency_records`, `security_audit_events`) remain completely untouched (`IT-TX-09`).
2.  **BE-TX-NULLABILITY (v1.3 Account Parity):**
    *   Verified that rejected postings leave account balances unchanged and available (`IT-TX-09`).
    *   `AccountReadService` guarantees that for current-only balance overflow where opening data is valid, `openingBalance` is preserved, `currentBalance` is `null`, and `balanceStatus` is `UNAVAILABLE` with reason `CAPACITY_OVERFLOW`. For corrupt opening data, `openingBalance` remains `null` per v1.3.
3.  **BE-TX-SYNTAX (Malformed Amount Strings):**
    *   `TransactionPostService` now enforces canonical two-decimal positive string syntax via regex `^(?:0|[1-9]\d*)\.\d{2}$` directly on the raw request string before `BigDecimal` parsing.
    *   Malformed inputs such as `1e2`, `+1.00`, `01.00`, `10.0`, `10.555`, and non-positive `0.00` are rejected before numeric parsing, writing zero rows to any table (`IT-TX-10`, `postTransaction_malformedAmounts_rejected`).

### 7.2. Records Period Summary Runtime Delivery

*   Implemented `GET /api/finance/transactions/summary` in `TransactionController` and `TransactionReadService`.
*   Route disambiguation: `@GetMapping("/{id:[0-9a-fA-F\\-]{36}}")` explicitly isolates UUIDs and prevents `/summary` route collisions.
*   Single-query JPA Criteria multiselect calculates `count(*)`, `sum(increase)`, and `sum(decrease)` directly in the database.
*   Full support for:
    *   Whole-dataset period aggregation across active business transactions.
    *   Filter combinations by `accountId`, `eventType`, `startDate`, `endDate`.
    *   Inclusive date range semantics relative to `Asia/Karachi` accounting dates.
    *   Boundary validation: rejects `startDate > endDate` with HTTP `400 Bad Request`.
    *   Known zero defaults (`"0.00"`, `transactionCount = 0`) on empty filter matches.
    *   Exact negative net cash movement representation (e.g. `"-15000.00"`).
    *   Strict multi-tenant isolation (`IT-TX-11`).

### 7.3. Fresh Test Evidence Summary

Executed comprehensive accounting and transaction test suite:
```
mvn test "-Dtest=TransactionControllerTest,TransactionPostServiceTest,FinancialTransactionPostgreSqlIT,FinancialAccountPostgreSqlIT,AccountReadServiceTest,AccountCreateServiceTest,AccountControllerTest,CurrencyRegistryTest,BusinessAuthorizationServiceTest"
```
**Results:** **140 / 140 PASSED** (0 failures, 0 errors, 0 skipped).
*   `FinancialTransactionPostgreSqlIT`: **11 / 11 passed** (including `IT-TX-09`, `IT-TX-10`, `IT-TX-11` on disposable PostgreSQL 14.22).
*   `TransactionControllerTest`: **11 / 11 passed** (including `/summary` success, validation, and route isolation tests).
*   `TransactionPostServiceTest`: **13 / 13 passed** (including malformed amount validation and capacity overflow rejection tests).
*   `FinancialAccountPostgreSqlIT`: **7 / 7 passed**.
*   `AccountReadServiceTest`: **18 / 18 passed**.
*   `AccountCreateServiceTest`: **15 / 15 passed**.
*   `AccountControllerTest`: **19 / 19 passed**.
*   `CurrencyRegistryTest`: **39 / 39 passed**.
*   `BusinessAuthorizationServiceTest`: **7 / 7 passed**.

Backend runtime is verified and **PAUSED** awaiting Codex's combined live harness run.




---

## 8. Frontend and Integration — Codex A-RECORDS-01-FE

**Date:** 14 October 2026 (Asia/Karachi).
**Status:** **FRONTEND_VERIFIED_BY_ISOLATED_CHECKS — INTEGRATION_BLOCKED — PAUSED**. The combined real harness exits **1**, solely for the producer nullability mismatch below. **Not INTEGRATION_VERIFIED**, full F01, ledger cutover, or production acceptance.

### 8.1. Checkout and consumed contracts

Existing `C:\Users\PC\Projects\sme-financial-webapp`, branch `dev/suleman`, HEAD `68d2995ca3548e57256b0be5f8a44322e11d8f7c`. Root/frontend AGENTS v1.1 and implementation state inspected. Preserved existing concurrent backend/untracked work, including `backend/.cp.txt`.

Consumed C01 v2.0 (bounded sale receipt/operating expense), accounts read v1.3, account creation v1.1, and this handoff's **Records Period Summary revision 1.0 CONTRACT_READY**. Waited for section 7 **RUNTIME_READY — PAUSED**, explicitly announcing backend readiness for the combined live harness, before the live window. No financial-policy approval or contract freeze is implied.

### 8.2. Implemented behavior

- `/records` connects Activity, Money accounts and Historical summaries. Add record and Scan/upload are visible together in live Activity. Existing account/transaction components and adapters are reused; `/finance/accounts` and `/finance/transactions` remain usable direct routes.
- Plain event labels: Sale payment received / Business expense paid, with the bounded event exclusions and bilingual snapshot/cutover notice. Account selection and authorized PKR account creation are in the entry context. Cancelling creation preserves transaction fields and confirmation; completing creation refreshes real accounts and selects the returned eligible account, preserving event, raw amount, date and description. Confirmation is reset when the selected account changes.
- Shared exact-string input accepts ordinary integers/fractions, validated Western grouping and Arabic/Urdu digits. String/BigInt HALF_UP normalization supports declared account currency scales, signed bank/wallet openings, canonical zero, capacity after carry and positive PKR postings. Original input is retained while editing and the final canonical/rounded amount is displayed before recording. Existing transport DTO validators remain strict; malformed wire amounts are not rounded. No Number monetary arithmetic or browser balance/total calculations.
- Explicit uncertain-outcome retries retain the original key and immutable normalized payload; fields/close/create actions are locked pending resolution. No automatic replay or private payload persistence in localStorage.
- Today / This week / This month / Custom use Asia/Karachi calendar dates, Monday week start, and inclusive boundaries (presets through today). Activity filters/page and money-account filters/page use separate URL keys. Immediate URL updates merge the latest URL, fixing the observed rapid-filter lost-update race. Detail close restores existing focus; OCR return restores Records filters and Scan/upload focus. Historical list return context is retained; existing monthly form/API behavior is preserved with returns directed to the historical list.
- Received/paid/net use the real complete-dataset summary endpoint. Totals drill into event filters; cash movement is explicitly distinguished from profit. Summary error/retry is independent of usable activity. Large server aggregate strings are displayed without imposing a per-account capacity or computing net locally.
- Existing upload/review/removal links carry a validated Records return destination. Copy clearly states document confirmation contributes to monthly summaries, not the transaction ledger. Original extraction/correction/confirm/permission logic is unchanged. No OCR-to-ledger path, persistent drafts, unclassified movements, transfers, reversals, FX or cutover controls added.
- Feature-local typed EN/UR copy, Western numeric display/RTL isolation, existing tokens/components, accessible Sheets/forms and mobile summary touch targets. Demo mounts no account/transaction resource or creation form and explains preview unavailability.

### 8.3. Exact changed paths

- `frontend/src/app/records/page.tsx`
- `frontend/src/app/records/history/page.tsx`
- `frontend/src/app/records/[id]/page.tsx`
- `frontend/src/app/finance/accounts/page.tsx`
- `frontend/src/app/finance/transactions/page.tsx`
- `frontend/src/components/records/MonthlyRecordForm.tsx`
- `frontend/src/components/records/records-copy.ts`
- `frontend/src/components/records/record-period.ts`
- `frontend/src/components/records/record-query.ts`
- `frontend/src/components/records/RecordsDocumentContext.tsx`
- `frontend/src/components/layout/Sidebar.tsx`
- `frontend/src/components/layout/MobileNav.tsx`
- `frontend/src/components/integration/LiveDocuments.tsx`
- `frontend/src/components/integration/LiveDocumentReview.tsx`
- `frontend/src/components/finance/accounts/AccountsList.tsx`
- `frontend/src/components/finance/accounts/CreateAccountForm.tsx`
- `frontend/src/components/finance/accounts/account-copy.ts`
- `frontend/src/components/finance/transactions/TransactionsList.tsx`
- `frontend/src/components/finance/transactions/PostTransactionForm.tsx`
- `frontend/src/components/finance/transactions/transaction-copy.ts`
- `frontend/src/lib/utils/amount-input.ts`
- `frontend/src/lib/api/dev-a/transaction-totals.ts`
- `frontend/tests/accounts-create.test.mjs`
- `frontend/tests/transactions.test.mjs`
- `frontend/tests/records.test.mjs`
- `frontend/tests/accounts-live.integration.mjs`
- `docs/phase2/handoffs/TRANSACTIONS.md`

Shared frontend writer paths touched in the user-authorized Records window: `Sidebar.tsx`, `MobileNav.tsx`, Records routes/`MonthlyRecordForm.tsx`, and document integration components solely for return links/copy. Preserve these changes during any Fatima integration. No central dictionary, root layout, auth/session transport, navigation permission registry, backend, migration, contract section or shared implementation-state edits.

### 8.4. Fresh isolated/build checks

All pnpm invocations used process-only `pnpm_config_verify_deps_before_run=false`; no installation or intentional shim/dependency refresh.

| Command | Fresh result |
| --- | --- |
| `pnpm exec node --test tests/records.test.mjs` | **12 passed**, exit 0 |
| affected account/transaction/Records tests | Passed; controlled fixtures only |
| `pnpm test` | **120 passed**, 0 failures/skips, exit 0 |
| `pnpm exec tsc --noEmit --incremental false` | exit 0 |
| `pnpm run lint` | exit 0; existing SessionProvider line 204 exhaustive-deps warning only |
| `pnpm run build` with live mode and `BACKEND_API_URL=http://127.0.0.1:8180` | exit 0; existing Next middleware deprecation warning |
| `pnpm exec node --check tests/accounts-live.integration.mjs` | exit 0 |
| `git diff --check -- frontend` (read-only) | exit 0 |

New isolated tests cover natural/grouped/Arabic/Urdu input, exact HALF_UP ties/sign/zero, each scale and capacity carry, rejected notation/grouping, Karachi/Monday date boundaries, safe return URLs, canonical required summary fields/filter echo, independent server aggregates, abort/error with no fallback, normalized frozen retry identity and rapid URL filter merging. Wire validator tests deliberately remain strict.

### 8.5. Real HTTP/database and browser evidence

Final `pnpm exec node tests/accounts-live.integration.mjs`: **31 passing grouped checks**, **exit 1**, with one remaining producer-contract blocker. Actual **PostgreSQL 14.22 through Flyway V20**, actual Spring on loopback 8180, production Next on loopback 3180, headless Edge. Synthetic identities/accounts are persisted only in each disposable database; no synthetic API response substitutes for Spring. Session, role authorization, CSRF and MFA mechanisms are preserved.

Real HTTP/database assertions: read/create/post permissions; absent/invalid/valid CSRF; foreign/inactive/non-PKR/date/amount rejection; durable replay and changed payload conflict; replay authorization; eight concurrent same-key one-account/one-event/movement and distinct requests preserved; backdated CASH deficits; atomic aggregate capacity rejection; syntax rejection before writes; exact balances and real actor/business audit identity. Independent expected balance `100000.00 + 20000.00 - 5000.00 = 115000.00`; retries contribute nothing. Original stored PKR scale-3 and opening-precision-overflow reads still emit valid unavailable null combinations.

Records browser assertions: historical monthly list and contextual return, account creation cancelled/completed with transaction inputs preserved, integer `1500`, fractional `1500.5`, grouped Urdu `۱,۵۰۰.۵۵۵`, HALF_UP preview and recorded `1500.56`, real refreshed account balance **5501.62**. Independent summary expectations: **1500.00 + 1500.50 + 1500.56 = 4501.06**, received **4501.06**, paid **0.00**, net **4501.06**, count **3**. Whole-dataset summary count matches history total even with page size 1. Total drilldowns, period URL changes, detail close/focus, Scan/upload return filters/focus, OWNER/ACCOUNTANT normal posting and MANAGER/VIEWER read-only Records, EN/UR/RTL/mobile, session expiry/business-switch stale responses all exercised.

Summary-only network failure was induced **after fetching the actual Spring response**, then dropping delivery; the real activity list remained usable and explicit summary retry recovered. A genuine full backend outage stopped only owned Spring: existing safe session-verification failure blocked private data; accounts/transactions/Records showed errors rather than mock or empty data. This distinction preserves the existing identity boundary.

Database count/MD5 fingerprints of nonempty `monthly_records`, `score_results`, `insights`, `recommendations` were unchanged across creation/posting/Records/retries, including the final snapshot after delayed writes. OCR confirmation itself was not run and would retain its existing monthly contribution behavior.

Inspected backend Surefire XML read-only: `FinancialTransactionPostgreSqlIT` actually contains **11** discovered testcase entries (including aggregate-overflow, malformed syntax and summary cases), 0 failures/errors/skips. Antigravity's reported **140** test results are not claimed as a Codex rerun. No backend Maven suite rerun or production PostgreSQL/TLS fixture gate in this task.

Earlier attempts surfaced/fixed the frontend URL filter race, historical heading and small summary touch targets. Harness assumptions were adapted for intentionally persistent filters and existing accessible labels; a harness calendar variable and language selector were corrected. An initial build used the default backend port and failed CSRF bootstrap through the proxy; corrected by **process-only** BACKEND_API_URL and a harness rewrite precondition. No environment/transport/security files changed.

### 8.6. Remaining backend request — no consumer weakening

**BE-TX-NULLABILITY persists for pre-existing corrupt current data.** In an isolated integrity business, post a valid `1.00` movement to a valid `0.00` bank opening, then change only that fixture's stored opening to `999999999999.99`. Real account GET returns HTTP 200 with:

```json
{
  "openingBalance": "999999999999.99",
  "currentBalance": null,
  "balanceStatus": "UNAVAILABLE",
  "balanceUnavailableReason": "CAPACITY_OVERFLOW"
}
```

`AccountReadService` current-evaluation failure branch preserves opening while returning the capacity reason. The v1.3 normative integrity-isolation invariant/strict consumer permits preserved opening with **INTEGRITY_CHECK_FAILED**, while capacity/precision/metadata opening failures require both balances null. This mismatch remains visible; frontend validation was not weakened. Recommend Antigravity align the current-only corrupt-data reason with the existing integrity-isolation contract, or publish an explicitly coordinated consumer-ready clarification before changing the consumer. This is not policy approval.

Aggregate overflow on **new posting** now rejects atomically and leaves the original maximum account AVAILABLE with both exact balances unchanged. Malformed exponent/plus/leading-zero amounts reject atomically. Those two original posting defects pass; the controlled pre-existing nullability regression prevents INTEGRATION_VERIFIED. No current corrupt fixtures are copied into the review database.

Documentation-only clarification requested: summary section 6.1 says a GET requires a valid CSRF token. Existing apiClient/Spring protect unsafe mutations with X-XSRF-TOKEN; safe authenticated GETs use established session handling. Correct wording without changing transport/security.

### 8.7. Demo and restored local review

Demo production build **exit 0**, `--demo` real Edge harness **exit 0**: accounts/transactions/Records EN/UR preview states, **zero private API requests**, no live mutation. Current live production build restored **exit 0** after demo.

`--review` setup **exit 0**: independent loopback Spring/PostgreSQL/Next processes survive the setup process. Real API registration, labelled local business, PKR/JPY/BHD/CASH accounts and sale/expense samples; a separate clearly local MVP monthly snapshot/score makes existing dashboards available. Loaded EN/UR mobile Records was additionally checked after data finished loading, with no overflow and Arabic/Urdu natural input preview; smoke form cancelled without posting.

- Frontend: **http://127.0.0.1:3180/records**, PID **17504**.
- Backend: **127.0.0.1:8180**, PID **4148**.
- Disposable database: loopback port **51590**, migration **V20**. Not publicly exposed; independent Java owns its lifetime.
- Direct routes: `/finance/accounts`, `/finance/transactions`, `/records/history`, existing scan/upload/review.
- Use the established user-only credentials file outside the repository (`work/finsight-review-v20/credentials.txt` in the Codex working directory) through normal sign-in; complete any MFA prompts/enroll through Settings. Alternatively register a separate local identity/business normally. No credential/token/MFA values in this handoff, source, or logs.
- Stop **only these services** with `C:\Users\PC\Documents\Codex\2026-10-14\you-are-joining-finsight-as-the\work\finsight-review-v20\stop-review.ps1`; it validates recorded process command lines and signals the owned runtime to close its database. `processes.json` in that same private directory contains nonsecret process metadata.
- OCR/FastAPI and external provider delivery are not started/verified here. Upload/review UI is reused with honest monthly-destination copy; OCR-to-ledger, persistent drafts, unified classification, full F01 and production acceptance remain later work. Monthly scores still use monthly entries, not the new cash activity totals.

No Git mutations, branches/worktrees/clones, remote writes, backend/migration/contract edits, dependency/manifest/lockfile/environment edits. Existing backend work and `.cp.txt` preserved. Codex **PAUSED**; functioning local review services intentionally remain running.


---

## 9. Final Frontend Parity — Codex A-RECORDS-01-FINAL-PARITY

**Date:** 15 October 2026 (Asia/Karachi).
**Status:** **INTEGRATION_VERIFIED (bounded Records/accounts/transaction slice) — PAUSED**. Final combined real harness **exit 0**, **33 passing check groups**, **0 blockers**. This supersedes the blocked status and producer-correction recommendation in sections 8/8.6. It does not approve financial policies, freeze contracts, complete full F01/ledger cutover or establish production acceptance.

### 9.1. Exact capture and diagnosis

Fresh reproduction before changing the validator, in disposable PostgreSQL V20: create a valid bank opening `0.00`, post a valid `1.00` receipt, then alter only this isolated fixture's stored opening to the valid maximum `999999999999.99`. The computed current value exceeds capacity. Authenticated real account detail GET returned HTTP 200:

```json
{
  "id": "117dd8a7-9f27-494f-bceb-4f76034330ee",
  "accountName": "PARITY current-only overflow",
  "accountType": "BANK",
  "currency": "PKR",
  "currencyDecimals": 2,
  "openingBalance": "999999999999.99",
  "openingDate": "2026-10-15",
  "currentBalance": null,
  "balanceStatus": "UNAVAILABLE",
  "balanceUnavailableReason": "CAPACITY_OVERFLOW",
  "status": "ACTIVE",
  "observationAt": "2026-10-15T04:57:45Z",
  "effectiveCutoffDate": "2026-10-15",
  "institutionName": null,
  "accountIdentifier": null,
  "notes": null,
  "createdAt": "2026-10-15T04:57:44Z",
  "updatedAt": "2026-10-15T04:57:44Z"
}
```

The pre-fix **`parseAccountDetail(payload, id)` threw `InvalidAccountResponse`**. Its type/validator permitted a non-null opening in UNAVAILABLE only for INTEGRITY_CHECK_FAILED. The old harness condition

```js
openingBalance !== null && balanceUnavailableReason !== 'INTEGRITY_CHECK_FAILED'
```

evaluated true and incorrectly reported BE-TX-NULLABILITY. Historical exact payload/reproduction evidence is in ignored `frontend/test-artifacts/accounts-live/parity-before.json` and `parity-before-results.json`.

**Fault attribution:** frontend type/validation and harness expectations, with ambiguous/contradictory contract wording; **not a backend output defect or stale running class**. Backend `AccountReadService` validates opening first, retains that exact valid string, and returns null current plus the current evaluation's CAPACITY_OVERFLOW reason. String DTO fields serialize that state correctly. Accounts v1.3's capacity reason note explicitly says opening is null *if the opening caused overflow*; its integrity-isolation wording/revision summary led to the narrower frontend assumption. Suleman's explicit 15 October intended invariant resolves that ambiguity: **current-only capacity overflow preserves valid opening**. The previous suggestion to change a correct producer reason to INTEGRITY_CHECK_FAILED is withdrawn. Antigravity may clarify the contract prose separately; no contract/backend edit was made here.

### 9.2. Narrow frontend correction and strictness

- Extended the preserved-opening UNAVAILABLE type branch to INTEGRITY_CHECK_FAILED **or CAPACITY_OVERFLOW**.
- CAPACITY_OVERFLOW with non-null opening still requires supported/matching currency metadata, valid canonical exact opening at the declared scale/capacity and null current. A valid future opening cannot report current-only overflow because it contributes canonical zero before its effective date.
- Opening corruption retains v1.3 null-opening behavior. PRECISION_OVERFLOW/UNSUPPORTED_CURRENCY do not gain a preserved-opening exception. AVAILABLE still requires two valid exact strings and null reason. Malformed amounts, unsupported metadata, missing fields, negative zero, non-null unavailable current, unknown reasons and inconsistent future state remain rejected.
- Existing AccountMoneyValue/AccountDetail rendering is reused: valid opening is formatted exactly; unavailable current is displayed in EN/UR as unavailable, never zero. Focused component assertions and the **real browser detail Sheet** exercise this separation.
- Harness now asserts the intended tuple and passes the actual HTTP payload through the real frontend parser, then checks the rendered detail opening/current states. It retains atomic post-capacity and malformed wire-syntax rejection checks.

**Exact files changed in this task:**

- `frontend/src/lib/api/dev-a/account-contracts.ts`
- `frontend/tests/accounts-read.test.mjs`
- `frontend/tests/accounts-live.integration.mjs`
- `docs/phase2/handoffs/TRANSACTIONS.md` (this appended diagnosis/evidence only)

### 9.3. Current compiled backend/runtime provenance

Before the probe and combined run, the harness verifies source/class timestamp freshness for AccountReadService, TransactionPostService, TransactionReadService, account/transaction controllers and account response DTOs. AccountReadService source was last modified **14 October 17:39:45 +05:00**, compiled output **14 October 20:47:08 +05:00**. Code is unchanged since that compilation; a new backend Maven compilation/suite was **not** claimed or required.

Fresh Java runtimes report the loaded AccountReadService code-source location and SHA-256 class bytes. The running hash must equal current `backend/target/classes`, or the harness fails. Review startup performs the same assertion before provisioning or browser smoke verification.

Verified current/review class SHA-256: `91edb2f4f2e7bcfa45c4d8a11746745f227cb6e42001ae3344a2b20087662535`.

The prior review environment was no longer listening at task entry. Its stop script refused stale/unverifiable PID metadata instead of stopping an unrelated process. The harness reserved free 3180/8180 ports and launched only owned fresh runtimes. No unrelated process was killed.

### 9.4. Fresh commands/results and evidence reuse

All pnpm commands used process-only `pnpm_config_verify_deps_before_run=false`. Browser commands used the already installed bundled PLAYWRIGHT_MODULE; no dependency installation. One initial targeted invocation omitted that process variable and failed module resolution before runtime startup; the correctly configured invocation passed.

| Check | Result |
| --- | --- |
| `pnpm exec node tests/accounts-live.integration.mjs --parity --capture-old-parity` before correction | exit 0, **expected old rejection reproduced**, not an integration acceptance |
| `pnpm exec node --test tests/accounts-read.test.mjs` | **25 passed**, exit 0 |
| `pnpm exec node tests/accounts-live.integration.mjs --parity` after correction | **exit 0**, actual response accepted; `parity-after.json` preserves full payload |
| `pnpm test` | **121 passed**, 0 failures/skips, exit 0 |
| `pnpm exec tsc --noEmit --incremental false` | exit 0 |
| `pnpm run lint` | exit 0; existing SessionProvider line 204 warning only |
| `pnpm run build` with live mode / process-only BACKEND_API_URL=http://127.0.0.1:8180 | exit 0; existing Next middleware deprecation warning |
| `pnpm exec node tests/accounts-live.integration.mjs` | **exit 0, 33 groups passed, no blockers** |
| `pnpm exec node --check tests/accounts-live.integration.mjs` / frontend diff whitespace check | exit 0 |
| `pnpm exec node tests/accounts-live.integration.mjs --review` | exit 0; current backend class hash verified and loaded EN/UR mobile Records/browser preview smoke passed |

New regressions accept valid positive/negative preserved openings across PKR/JPY/BHD scales, verify actual adapter acceptance and honest bilingual component rendering, and keep malformed/invalid state rejection. Existing corrupted opening precision/scale/metadata null semantics remain covered.

The combined run used **actual Spring, disposable PostgreSQL 14.22 migrated through V20, production Next and Edge**. Current-only capacity response is accepted and honestly rendered. New posting capacity overflow still rejects atomically (no event/movement/idempotency/audit writes), malformed exponent/plus/leading-zero wire amounts still reject, and successful/rejected posting account responses remain valid. Concurrency/replay/CSRF/role/tenant/stale-context journeys pass. Independent expected account balance remains **115000.00** (`100000.00 + 20000.00 - 5000.00`, retries add nothing).

Records context creation/natural input/rounding/filter/detail/EN-UR-RTL-mobile/keyboard/OCR-return journeys pass. Real received/paid/net summary remains **4501.06 / 0.00 / 4501.06**, count **3**, with actual refreshed account **5501.62**. Whole-dataset count still agrees with paginated history totals. Nonempty monthly records, scores, insights and recommendations count/MD5 fingerprints remain unchanged, including delayed writes and final snapshot.

Prior **14 October** demo EN/UR/accounts/transactions/Records zero-private-request evidence is **reused, not rerun**: this narrow pure type/validator/harness fix does not change demo gating, transport, navigation or entry workflows. No unrelated backend/MVP suites or production infrastructure gates were repeated.

### 9.5. Local review left running

- URL: **http://127.0.0.1:3180/records** (direct accounts/transactions/historical routes retained).
- Next frontend **3180**, PID **25712**; Spring **8180**, PID **11620**; isolated PostgreSQL **61890**, migration **V20**, loopback only.
- Clean, clearly labelled synthetic review business/accounts/receipt/expense and separate monthly snapshot/score are provisioned through established APIs. No corrupt probe fixture is copied into this database.
- Private local credentials remain in the existing user-only `work/finsight-review-v20/credentials.txt` outside the repository. Sign in normally and complete any MFA prompts; alternatively register a separate local identity. No password/token/MFA secrets in source, logs or this handoff.
- Stop only these processes with `C:\Users\PC\Documents\Codex\2026-10-14\you-are-joining-finsight-as-the\work\finsight-review-v20\stop-review.ps1`. Nonsecret `processes.json` in that directory records current process metadata and backend class hash.
- OCR/external providers and production acceptance remain unverified as previously recorded; monthly calculations still use existing monthly entries until cutover.

Branch/HEAD remain `dev/suleman` / `68d2995ca3548e57256b0be5f8a44322e11d8f7c`. No Git mutations, branches/worktrees/clones, remote writes, backend/migration/contract/shared-state edits, manifests/lockfiles/dependency/environment changes. Concurrent backend work and `backend/.cp.txt` preserved. **PAUSED**, with the review app intentionally running.
