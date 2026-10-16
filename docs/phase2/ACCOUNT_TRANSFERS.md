# Account Transfers (PKR Internal Transfers) Specification & Contract

**Feature:** `A-F01-TRANSFERS` (PKR Transfers Between Accounts)
**Date:** 16 October 2026 (Asia/Karachi)
**Author:** Antigravity (Developer A Backend & Git Coordinator)
**Status:** **INTEGRATION_VERIFIED**
**Governing Contract:** `ACCOUNT_TRANSFERS.md` (Revision 1.1)
**Allocated Migration:** `V22__add_internal_transfers.sql`

---

## 1. Feature Purpose & Scope

### 1.1 Objective
Allow authorized `OWNER` and `ACCOUNTANT` users in central Records or Money Accounts to move funds between two existing active PKR accounts (for example, moving 5,000 PKR from Meezan Bank to Petty Cash).

### 1.2 Financial Invariants
1. **Atomic Dual-Movement Posting:** Exactly one financial transaction is created, containing:
   - One source account movement with direction `DECREASE` (outflow).
   - One destination account movement with direction `INCREASE` (inflow).
2. **Business-Wide Cash Neutrality:** Internal transfers are not external cash flows:
   - `moneyReceived` (external revenue/receipts) does NOT increase.
   - `moneyPaid` (external operating expenses) does NOT increase.
   - `netCashMovement` (`moneyReceived - moneyPaid`) does NOT change.
   - Across the business, `transferIn` and `transferOut` offset exactly.
3. **Monthly & Intelligence Preservation:** Monthly records, historical records, Hanafi Zakat calculations, and `health-score-v1` weights/fingerprints remain completely untouched.

### 1.3 Scope Exclusions
- No foreign exchange (FX) conversion or multi-currency transfers (PKR-only in this slice).
- No transfer fees, deductions, or split transfers.
- No reversals or cancel-and-replace workflows.
- No recurring transfers or automated schedules.
- No transfer drafts (drafts remain bounded to receipt/expense).
- No monthly ledger cutover or AR/AP settlement.

---

## 2. Normative REST API Contract

### 2.1 Transfer Endpoint: `POST /api/finance/transfers`

#### Headers
| Header | Required | Format / Constraint | Description |
| :--- | :--- | :--- | :--- |
| `Idempotency-Key` | **Yes** | String, 1–100 chars | Client-generated unique operation key |
| `X-XSRF-TOKEN` | **Yes** | Standard CSRF token | Required on state-mutating requests |
| `Content-Type` | **Yes** | `application/json` | Request payload type |

#### Request Body
```json
{
  "sourceAccountId": "11111111-1111-4111-8111-111111111111",
  "destinationAccountId": "22222222-2222-4222-8222-222222222222",
  "amount": "5000.00",
  "effectiveDate": "2026-10-16",
  "description": "Replenish petty cash float from bank"
}
```

#### Field Specifications
| Field | Type | Required | Constraints |
| :--- | :--- | :--- | :--- |
| `sourceAccountId` | UUID | **Yes** | Must reference an active PKR account belonging to active business. |
| `destinationAccountId` | UUID | **Yes** | Must reference an active PKR account belonging to active business; distinct from `sourceAccountId`. |
| `amount` | String | **Yes** | Strict canonical positive two-decimal ASCII string (`\A(?:0\|[1-9][0-9]*)\.[0-9]{2}\z`), $> 0.00$, $\le 999,999,999,999.99$. No surrounding whitespace or control characters. |
| `effectiveDate` | String | **Yes** | ISO-8601 calendar date (`YYYY-MM-DD`). Must not precede either account's opening date. Must not be in the future (Asia/Karachi). |
| `description` | String | No | Optional note, max 255 characters. Trimmed if provided. |

#### Successful Response: `201 Created`
```json
{
  "id": "33333333-3333-4333-8333-333333333333",
  "businessId": "00000000-0000-4000-8000-000000000001",
  "accountId": "11111111-1111-4111-8111-111111111111",
  "accountName": "Meezan Bank",
  "accountType": "BANK",
  "eventType": "INTERNAL_TRANSFER",
  "movementDirection": "DECREASE",
  "amount": "5000.00",
  "currency": "PKR",
  "baseAmountPkr": "5000.00",
  "effectiveDate": "2026-10-16",
  "description": "Replenish petty cash float from bank",
  "resultingBalance": "45000.00",
  "movements": [
    {
      "id": "44444444-4444-4444-8444-444444444444",
      "accountId": "11111111-1111-4111-8111-111111111111",
      "movementDirection": "DECREASE",
      "amount": "5000.00",
      "currency": "PKR",
      "effectiveDate": "2026-10-16",
      "createdAt": "2026-10-16T08:30:00Z"
    },
    {
      "id": "55555555-5555-4555-8555-555555555555",
      "accountId": "22222222-2222-4222-8222-222222222222",
      "movementDirection": "INCREASE",
      "amount": "5000.00",
      "currency": "PKR",
      "effectiveDate": "2026-10-16",
      "createdAt": "2026-10-16T08:30:00Z"
    }
  ],
  "createdByUserId": "99999999-9999-4999-8999-999999999999",
  "createdAt": "2026-10-16T08:30:00Z",
  "sourceAccountId": "11111111-1111-4111-8111-111111111111",
  "sourceAccountName": "Meezan Bank",
  "destinationAccountId": "22222222-2222-4222-8222-222222222222",
  "destinationAccountName": "Petty Cash"
}
```

### 2.2 Error Responses
- **400 Bad Request:** Validation failure on input fields (`sourceAccountId`, `destinationAccountId`, `amount`, `effectiveDate`, `description`, `cashBalance`).
  ```json
  {
    "status": 400,
    "error": "Validation Failed",
    "message": "One or more request parameters are invalid",
    "errors": {
      "destinationAccountId": "Source and destination accounts must be distinct"
    }
  }
  ```
- **401 Unauthorized:** Missing or invalid session cookie.
- **403 Forbidden:** User lacks `FINANCIAL_TRANSACTION_POST` (e.g. `MANAGER`, `VIEWER`).
- **404 Not Found:** Referenced account does not exist in active business (non-disclosing).
- **409 Conflict:** Idempotency key payload mismatch (`DuplicateResourceException`).

---

## 3. History & Summary Compatibility

### 3.1 Extended Summary Contract: `GET /api/finance/transactions/summary`
```json
{
  "currency": "PKR",
  "startDate": "2026-10-01",
  "endDate": "2026-10-31",
  "accountId": null,
  "eventType": null,
  "transactionCount": 4,
  "moneyReceived": "4501.06",
  "moneyPaid": "0.00",
  "netCashMovement": "4501.06",
  "transferIn": "5000.00",
  "transferOut": "5000.00",
  "netAccountMovement": "4501.06",
  "observationAt": "2026-10-16T08:30:00Z"
}
```

#### Summary Field Rules
1. `moneyReceived`: Sum of external receipts (`DIRECT_SALE_RECEIPT`). Transfers NEVER inflate this field.
2. `moneyPaid`: Sum of external payments (`OPERATING_EXPENSE_PAYMENT`). Transfers NEVER inflate this field.
3. `netCashMovement`: `moneyReceived - moneyPaid`.
4. `transferIn`:
   - Account filter: Sum of `INTERNAL_TRANSFER` `INCREASE` movements on the filtered account.
   - All accounts: Total transfers transferred in across all accounts.
5. `transferOut`:
   - Account filter: Sum of `INTERNAL_TRANSFER` `DECREASE` movements on the filtered account.
   - All accounts: Total transfers transferred out across all accounts.
6. `netAccountMovement`:
   - Account filter: `(moneyReceived + transferIn) - (moneyPaid + transferOut)`.
   - All accounts: Equal to `netCashMovement` since `transferIn == transferOut` across the business.
7. `transactionCount`: Distinct matching transactions.

### 3.2 List Endpoint Compatibility: `GET /api/finance/transactions`
- Filtering by `accountId = X` matches any transaction where `account_id = X` OR `destination_account_id = X`.
- Total count and pagination do not duplicate transfers.
- In content items, for a transfer:
  - If filtered by destination account, item displays as `INCREASE` with destination account resulting balance.
  - If filtered by source account (or unfiltered), item displays as `DECREASE` with source account resulting balance.
  - `sourceAccountId`, `sourceAccountName`, `destinationAccountId`, `destinationAccountName` are provided on transfer items.

---

## 4. Concurrency & Deadlock Prevention

### Deterministic Two-Account Locking
When transferring between Account A and Account B:
1. Sort UUIDs deterministically:
   ```java
   UUID firstId = sourceId.compareTo(destId) < 0 ? sourceId : destId;
   UUID secondId = sourceId.compareTo(destId) < 0 ? destId : sourceId;
   ```
2. Acquire pessimistic write lock on `firstId`, then `secondId`.
3. Simultaneous opposite-direction transfers (A $\to$ B vs B $\to$ A) both lock A first, eliminating all deadlock conditions.

---

## 5. Migration Specification: `V22__add_internal_transfers.sql`

1. Alter table `financial_transactions` constraint `chk_tx_event_type` to allow `'INTERNAL_TRANSFER'`.
2. Add column `destination_account_id UUID REFERENCES financial_accounts(id) ON DELETE RESTRICT`.
3. Add constraint `chk_tx_transfer_accounts` enforcing:
   - For `INTERNAL_TRANSFER`: `destination_account_id IS NOT NULL AND destination_account_id <> account_id`.
   - For other event types: `destination_account_id IS NULL`.
4. Add partial index `idx_fin_tx_dest_account_date` on `(destination_account_id, effective_date DESC, created_at DESC, id DESC)`.

---

## 6. Implementation & Verification Evidence

### 6.1 Test Execution Matrix

| Test Suite / Command | Scope | Result | Details |
| :--- | :--- | :--- | :--- |
| `TransferServiceTest` | Backend Domain Service Unit Tests | **11/11 Passed** | Negative CASH rejection, future date rejection, capacity bounds, malformed amount validation, distinct accounts, inactive/non-PKR rejection, deterministic lock sorting, chronological simulator. |
| `TransferControllerTest` | REST Controller WebMvc Tests | **7/7 Passed** | Authorization grants (`OWNER`/`ACCOUNTANT` allowed; `VIEWER`/`MANAGER` 403), Idempotency header enforcement, validation error mapping. |
| `FinancialTransferPostgreSqlIT` | Real PostgreSQL 14.22 Integration | **9/9 Passed** | Concrete 5,000 PKR Meezan-to-Cash transfer, dual movements, atomic rollback on cash deficit, concurrent opposite-direction deadlock prevention, idempotent retry returns original 201, changed payload returns 409 conflict, account & business summary calculations. |
| `TransactionPostServiceTest` & `TransactionControllerTest` | Regressions | **24/24 Passed** | Existing single-account posting regressions unaffected. |
| `RecordDraftPostgreSqlIT` | Draft Regression | **11/11 Passed** | Full draft lifecycle, replay syntax, and isolation verified. |
| `FinancialAccountPostgreSqlIT` & `FinancialTransactionPostgreSqlIT` | Accounting Regressions | **22/22 Passed** | Account creation, transactions, and filters verified. |
| `tests/transfers.test.mjs` | Frontend Unit & Contract Suite | **11/11 Passed** | Bilingual copy completeness, distinct account validation, opening date boundaries, canonical normalization, payload freezing, dual movement parsing, explicit retry state machine. |
| `tests/records.test.mjs` & `tests/transactions.test.mjs` | Frontend Regressions | **37/37 Passed** | Period calculations, filter echo, Natural amounts, demo isolation. |
| `npx tsc --noEmit` | TypeScript Strict Check | **Exit 0** | Zero compiler errors. |
| `npm run lint` | ESLint Linter | **Exit 0** | Zero errors. |
| `npm run build` | Next.js Production Build (`BACKEND_API_URL=http://127.0.0.1:8280`) | **Exit 0** | Turbopack compilation succeeded in 11.5s, TypeScript 3.1s, static pages generated. Routes manifest rewrite verified. |
| `accounts-live.integration.mjs` | Full Live PostgreSQL + Browser Acceptance Harness | **Exit 0 (54 groups, 0 blockers)** | Disposable PostgreSQL 14.22 with V22 schema, real Spring Boot, transparent gateway, production Next.js build, Playwright/Edge. Full end-to-end transfer lifecycle verified. |

### 6.2 Review Environment Status
- **Review URL:** `http://127.0.0.1:3480/records`
- **Gateway:** Port 3480 (PID 27472)
- **Production Next.js:** Port 3481 (PID 24784)
- **Spring Boot Backend:** Port 8580 (PID 16772)
- **Isolated PostgreSQL:** Port 59580
- **Health Check:** Returning `HTTP 200`
- **Data Preservation:** Unchanged and isolated.

**STATUS: INTEGRATION_VERIFIED. PAUSED.**
