# Transaction Reversals (Safe Bookkeeping Correction) Specification & Contract

**Feature:** `A-F01-REVERSALS` (Safe Correction of Recorded Transactions)
**Date:** 17 October 2026 (Asia/Karachi)
**Author:** Antigravity (Developer A Backend & Git Coordinator)
**Status:** **INTEGRATION_VERIFIED**
**Allocated Migration:** `V23__add_transaction_reversals.sql`

---

## 1. Feature Purpose & Scope

### 1.1 Objective
Allow authorized `OWNER` and `ACCOUNTANT` users in central Records or Money Accounts to safely correct an erroneously recorded transaction:
- `DIRECT_SALE_RECEIPT`
- `OPERATING_EXPENSE_PAYMENT`
- `INTERNAL_TRANSFER`

### 1.2 Nature of the Workflow
This is an immutable **bookkeeping correction**, not an external customer refund, dispute, chargeback, or money transfer through a bank.
- The original transaction remains visible, linked, and immutable in history.
- A linked compensating transaction reverses its financial effect exactly once.
- An original transaction cannot be reversed more than once.
- A reversal transaction cannot itself be reversed (no reversal-of-reversal).
- Users may enter a corrected replacement transaction through the existing entry or transfer workflow afterward. Reversal and replacement are distinct, deliberate operations.

### 1.3 Financial & Accounting Invariants
1. **Immutable Original Records:** The original `FinancialTransaction` and all of its associated `AccountMovement` rows are never deleted, updated, or recalculated. Their recorded balance snapshots are never altered.
2. **Atomic Inverse Movements:**
   - For `DIRECT_SALE_RECEIPT` (original movement `INCREASE` on account A): the reversal transaction posts an inverse movement `DECREASE` on account A for the exact same amount.
   - For `OPERATING_EXPENSE_PAYMENT` (original movement `DECREASE` on account A): the reversal transaction posts an inverse movement `INCREASE` on account A for the exact same amount.
   - For `INTERNAL_TRANSFER` (original movement `DECREASE` on source account S and `INCREASE` on destination account D): the reversal transaction posts inverse movements `INCREASE` on source account S and `DECREASE` on destination account D for the exact same amount.
3. **Exact Chronological Date Alignment:**
   - In this bookkeeping correction workflow, the reversal's `effectiveDate` equals the original transaction's `effectiveDate`.
   - `createdAt` records the exact instant when the reversal was recorded.
   - There is no client-selected reversal date. (A later-date real refund or customer return is a separate future workflow).
4. **Chronological CASH & Capacity Non-Violation:**
   - The entire chronological balance sequence of every affected account is simulated including the proposed inverse movement(s).
   - If reversing a receipt or destination transfer movement would cause a `CASH` account balance to drop below zero on any historical date, the reversal is rejected atomically with an actionable domain error.
   - Account balances must remain within supported 12-digit capacity (`999,999,999,999.99`).
5. **Summary Classification Neutrality:**
   - A reversed receipt reduces `moneyReceived` by the reversed amount. It is NEVER presented as an operating expense.
   - A reversed expense reduces `moneyPaid` by the reversed amount. It is NEVER presented as revenue/receipts.
   - A reversed transfer cancels its original `transferIn` and `transferOut` contributions. It NEVER alters external cash flows.
   - `netAccountMovement` reflects true signed movements.
   - `transactionCount` increments by 1 for each reversal transaction, as both original and reversal are distinct, recorded audit-tracked entries.

### 1.4 Scope Exclusions
- No partial reversals or amount adjustments.
- No reversal-of-a-reversal.
- No automatic replacement entries.
- No bank refunds, customer chargebacks, or physical cash returns.
- No foreign exchange (FX) conversion (PKR-only in this slice).
- No draft reversal (drafts are discarded or edited prior to posting).
- No monthly ledger cutover alterations.

---

## 2. Normative REST API Contract

### 2.1 Reversal Endpoint: `POST /api/finance/transactions/{id}/reverse`

#### Path Parameter
- `id` (UUID, required): The unique identifier of the posted transaction to be reversed. Must exist in the active business.

#### Headers
| Header | Required | Format / Constraint | Description |
| :--- | :--- | :--- | :--- |
| `Idempotency-Key` | **Yes** | String, 1–100 chars | Client-generated unique operation key |
| `X-XSRF-TOKEN` | **Yes** | Standard CSRF token | Required on state-mutating requests |
| `Content-Type` | **Yes** | `application/json` | Request payload type |

#### Request Body
```json
{
  "reason": "Customer invoice entered twice by mistake"
}
```

#### Field Specifications
| Field | Type | Required | Constraints |
| :--- | :--- | :--- | :--- |
| `reason` | String | **Yes** | Required non-blank string, trimmed length between 5 and 255 characters. Explains the bookkeeping correction. |

#### Successful Response: `201 Created`
The response returns the `TransactionDetailResponse` of the newly created reversal transaction, linked to the original.

##### Example Response (Receipt Reversal):
```json
{
  "id": "77777777-7777-4777-8777-777777777777",
  "businessId": "00000000-0000-4000-8000-000000000001",
  "accountId": "11111111-1111-4111-8111-111111111111",
  "accountName": "Meezan Bank",
  "accountType": "BANK",
  "eventType": "DIRECT_SALE_RECEIPT",
  "movementDirection": "DECREASE",
  "amount": "2000.00",
  "currency": "PKR",
  "baseAmountPkr": "2000.00",
  "effectiveDate": "2026-10-15",
  "description": "Customer invoice entered twice by mistake",
  "resultingBalance": "10000.00",
  "movements": [
    {
      "id": "88888888-8888-4888-8888-888888888888",
      "accountId": "11111111-1111-4111-8111-111111111111",
      "movementDirection": "DECREASE",
      "amount": "2000.00",
      "currency": "PKR",
      "effectiveDate": "2026-10-15",
      "createdAt": "2026-10-17T09:00:00Z"
    }
  ],
  "createdByUserId": "99999999-9999-4999-8999-999999999999",
  "createdAt": "2026-10-17T09:00:00Z",
  "sourceAccountId": null,
  "sourceAccountName": null,
  "destinationAccountId": null,
  "destinationAccountName": null,
  "isReversal": true,
  "reversedTransactionId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  "isReversed": false,
  "reversedByTransactionId": null,
  "reversalReason": "Customer invoice entered twice by mistake"
}
```

##### Example Response (Transfer Reversal):
```json
{
  "id": "99999999-9999-4999-8999-999999999999",
  "businessId": "00000000-0000-4000-8000-000000000001",
  "accountId": "11111111-1111-4111-8111-111111111111",
  "accountName": "Meezan Bank",
  "accountType": "BANK",
  "eventType": "INTERNAL_TRANSFER",
  "movementDirection": "INCREASE",
  "amount": "5000.00",
  "currency": "PKR",
  "baseAmountPkr": "5000.00",
  "effectiveDate": "2026-10-16",
  "description": "Wrong account selected for float replenishment",
  "resultingBalance": "50000.00",
  "movements": [
    {
      "id": "12121212-1212-4212-8212-121212121212",
      "accountId": "11111111-1111-4111-8111-111111111111",
      "movementDirection": "INCREASE",
      "amount": "5000.00",
      "currency": "PKR",
      "effectiveDate": "2026-10-16",
      "createdAt": "2026-10-17T09:15:00Z"
    },
    {
      "id": "34343434-3434-4434-8434-343434343434",
      "accountId": "22222222-2222-4222-8222-222222222222",
      "movementDirection": "DECREASE",
      "amount": "5000.00",
      "currency": "PKR",
      "effectiveDate": "2026-10-16",
      "createdAt": "2026-10-17T09:15:00Z"
    }
  ],
  "createdByUserId": "99999999-9999-4999-8999-999999999999",
  "createdAt": "2026-10-17T09:15:00Z",
  "sourceAccountId": "11111111-1111-4111-8111-111111111111",
  "sourceAccountName": "Meezan Bank",
  "destinationAccountId": "22222222-2222-4222-8222-222222222222",
  "destinationAccountName": "Petty Cash",
  "isReversal": true,
  "reversedTransactionId": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
  "isReversed": false,
  "reversedByTransactionId": null,
  "reversalReason": "Wrong account selected for float replenishment"
}
```

### 2.2 Error Responses
- **400 Bad Request:** Validation failure on input parameters (`reason` missing/too short/too long; target transaction is itself a reversal; cash would become negative; account balance would exceed capacity).
  ```json
  {
    "status": 400,
    "error": "Validation Failed",
    "message": "One or more request parameters are invalid",
    "errors": {
      "reason": "Reason must be between 5 and 255 characters"
    }
  }
  ```
- **401 Unauthorized:** Missing or invalid session cookie.
- **403 Forbidden:** User lacks `FINANCIAL_TRANSACTION_POST` (e.g. `MANAGER`, `VIEWER`).
- **404 Not Found:** Referenced transaction does not exist in the active business (non-disclosing).
- **409 Conflict:**
  - Idempotency key payload mismatch (`DuplicateResourceException`: "Idempotency key has already been used with a different request payload").
  - Transaction has already been reversed by another request (`DuplicateResourceException`: "Transaction has already been reversed").

---

## 3. Read Model & DTO Specifications

### 3.1 Extended `TransactionDetailResponse` & `TransactionSummary`
Both list items and detail responses include:
| Field | Type | Description |
| :--- | :--- | :--- |
| `isReversal` | Boolean | `true` if this transaction is a reversal compensating entry; `false` otherwise. |
| `reversedTransactionId` | UUID / String | If `isReversal` is `true`, points to the original transaction ID; `null` otherwise. |
| `isReversed` | Boolean | `true` if this transaction is an original entry that has been reversed; `false` otherwise. |
| `reversedByTransactionId` | UUID / String | If `isReversed` is `true`, points to the compensating reversal transaction ID; `null` otherwise. |
| `reversalReason` | String | If `isReversal` or `isReversed` is `true`, contains the recorded reason for reversal; `null` otherwise. |

### 3.2 List Endpoint Filtering & Visibility: `GET /api/finance/transactions`
- Originals and reversals are both visible and individually listed in transaction history.
- Reverse chronological sorting by `(effective_date DESC, created_at DESC, id DESC)` places the reversal and original predictably.
- When filtering by `accountId = X`:
  - Receipt/expense reversals matching account X are included.
  - Transfer reversals where X is either source or destination account are included.
- When filtering by `eventType = E`:
  - Reversals retain the original's economic classification `eventType`, ensuring filters for `DIRECT_SALE_RECEIPT`, `OPERATING_EXPENSE_PAYMENT`, or `INTERNAL_TRANSFER` consistently include their associated corrections.

### 3.3 Summary Endpoint Semantics: `GET /api/finance/transactions/summary`
Summary arithmetic incorporates reversals strictly as netting adjustments:
1. `moneyReceived`:
   $$\sum_{\text{active receipts}} \text{amount} - \sum_{\text{reversed receipts}} \text{amount}$$
   Transfers and expenses contribute 0.00.
2. `moneyPaid`:
   $$\sum_{\text{active expenses}} \text{amount} - \sum_{\text{reversed expenses}} \text{amount}$$
   Transfers and receipts contribute 0.00.
3. `netCashMovement`:
   $$\text{moneyReceived} - \text{moneyPaid}$$
4. `transferIn`:
   $$\sum_{\text{active transfer in}} \text{amount} - \sum_{\text{reversed transfer in}} \text{amount}$$
5. `transferOut`:
   $$\sum_{\text{active transfer out}} \text{amount} - \sum_{\text{reversed transfer out}} \text{amount}$$
6. `netAccountMovement`:
   $$(\text{moneyReceived} + \text{transferIn}) - (\text{moneyPaid} + \text{transferOut})$$
7. `transactionCount`:
   $$\text{Total recorded entries matching filter criteria (both originals and reversals)}$$

---

## 4. Concurrency, Atomicity & Locking

### 4.1 Locking Strategy
1. **Target Transaction Lock:**
   Acquire pessimistic write lock on the target transaction:
   ```sql
   SELECT * FROM financial_transactions WHERE id = :id AND business_id = :businessId FOR UPDATE
   ```
   Serializes concurrent reversal attempts on the same transaction immediately.
2. **Deterministic Account Locking:**
   - Single account (`DIRECT_SALE_RECEIPT`, `OPERATING_EXPENSE_PAYMENT`):
     Lock the single account row `FOR UPDATE`.
   - Dual accounts (`INTERNAL_TRANSFER`):
     Sort UUIDs deterministically:
     $$\text{firstId} = \min(\text{sourceId}, \text{destId}), \quad \text{secondId} = \max(\text{sourceId}, \text{destId})$$
     Lock `firstId` then `secondId` `FOR UPDATE`, eliminating all possibility of deadlock.

### 4.2 Durable Idempotency
- **Operation:** `TRANSACTION_REVERSE`
- **Request Hash:** `SHA-256(transactionId + "|" + normalizedReason)`
- Identical replay returns original successful response (`201 Created` or `200 OK`) without any additional database writes.
- Changed request with identical key returns `409 Conflict`.
- Concurrency race produces exactly one reversal; second thread detects prior reversal and returns `409 Conflict`.

---

## 5. Migration Specification: `V23__add_transaction_reversals.sql`

1. Add column `reversed_transaction_id UUID REFERENCES financial_transactions(id) ON DELETE RESTRICT` to `financial_transactions`.
2. Add column `reversal_reason VARCHAR(255)` to `financial_transactions`.
3. Add constraint `chk_tx_reversal_not_self`:
   `CHECK (reversed_transaction_id IS NULL OR reversed_transaction_id <> id)`.
4. Add constraint `chk_tx_reversal_reason`:
   `CHECK ((reversed_transaction_id IS NOT NULL AND reversal_reason IS NOT NULL) OR (reversed_transaction_id IS NULL))`.
5. Add unique constraint `uq_fin_tx_reversed_transaction_id UNIQUE (reversed_transaction_id)` ensuring at most one reversal per original transaction.
6. Add composite unique constraint `uq_fin_tx_id_business UNIQUE (id, business_id)` and foreign key constraint `fk_fin_tx_reversed_tx_business FOREIGN KEY (reversed_transaction_id, business_id) REFERENCES financial_transactions (id, business_id) ON DELETE RESTRICT` guaranteeing cross-business reversals are rejected at the database schema level.
7. Add partial index `idx_fin_tx_reversed_tx ON financial_transactions (reversed_transaction_id) WHERE reversed_transaction_id IS NOT NULL`.
8. Grant runtime privileges for `finsight_app` (SELECT, INSERT).

---

## 6. Implementation & Verification Evidence

### 6.1 Backend Verification Suite
- **Unit & Controller Tests:**
  `.\mvnw.cmd test "-Dtest=TransactionReverseServiceTest,TransactionControllerTest,FinancialTransactionReversalPostgreSqlIT"`
  - **Results:** 35 / 35 PASSED (0 failures, 0 errors, 0 skipped, build time 23.3s).
    - `TransactionReverseServiceTest`: 9 / 9 passed. Covers receipt reversal (decrease), expense reversal (increase), transfer reversal (dual inverse movements), chronological cash deficit simulation rejection, capacity limits, idempotent replay, payload conflict, duplicate reversal rejection, audit event recording.
    - `TransactionControllerTest`: 16 / 16 passed. Covers endpoint routing, request body validation (length 5–255), idempotency headers, HTTP 201 response structure, HTTP 400/404/409 exception mappings, CSRF enforcement, role-based security.
    - `FinancialTransactionReversalPostgreSqlIT`: 10 / 10 passed on disposable PostgreSQL 14.22 with schema V23. Validates database unique constraints (`uq_fin_tx_reversed_transaction_id`), composite tenant foreign key (`fk_fin_tx_reversed_tx_business`), self-reference rejection (`chk_tx_reversal_not_self`), reversal-of-reversal rejection, running cash deficit rejection, concurrent double-reversal locking.

### 6.2 Frontend Verification Suite
- **Unit & Contract Tests:**
  - `node tests/transaction-reversals.test.mjs`: 6 / 6 passed. Validates inverted movement direction wire parser, dual movement transfer reversal parser, `transactionsApi.reverse` client adapter, request parameter validation, bilingual copy parity.
  - `node tests/transactions.test.mjs`: 25 / 25 passed. Full transaction regression suite.
  - `node tests/transfers.test.mjs`: 11 / 11 passed. Full transfer regression suite.
- **Type Checking & Linting:**
  - `npx tsc --noEmit`: 0 errors.
  - `npm run lint`: 0 errors, clean code quality.

### 6.3 End-to-End Live Integration Harness
- **Test Command:** `node tests/accounts-live.integration.mjs`
- **Results:** **EXIT 0 (64 check groups passed, 0 blockers, integrated=true)**
- **Environment:**
  - Disposable PostgreSQL 14.22 (Flyway migrations V1–V23).
  - Task-owned Spring Boot backend (port 8680) with transparent gateway (port 3580).
  - Next.js production build (`BACKEND_API_URL=http://127.0.0.1:8280`, port 3581).
  - Headless Chromium browser via Playwright.
- **Verified Reversal Invariants:**
  1. `REVERSAL HTTP`: Receipt reversal restores bank balance (60,000 -> 50,000), enforces idempotency, prevents double reversal and reversal-of-reversal.
  2. `REVERSAL HTTP`: Expense reversal restores bank balance (46,000 -> 50,000) and nets out `moneyPaid`.
  3. `REVERSAL HTTP`: Transfer reversal restores both balances (Bank 50,000, Cash 2,000) and nets directional transfer movements.
  4. `REVERSAL HTTP`: Chronological CASH simulation rejects reversal when running cash would drop below zero.
  5. `REVERSAL HTTP`: `OWNER` and `ACCOUNTANT` reverse successfully; `MANAGER` and `VIEWER` receive HTTP 403; cross-business access receives HTTP 404.
  6. `REVERSAL BROWSER`: Transaction detail sheet exposes "Reverse incorrect entry" button, validates 5–255 char explanation and explicit confirmation checkbox, records compensating entry, displays "Reversed" status badge, and supports bidirectional navigation between original and compensating reversal entries.
  7. `REVERSAL BROWSER`: Bilingual Urdu RTL rendering verified with correct badges (`منسوخ شدہ` and `منسوخی`).
  8. `HTTP/DB`: Monthly financial records, health scores, insights, and recommendations remain untouched and uncorrupted.
