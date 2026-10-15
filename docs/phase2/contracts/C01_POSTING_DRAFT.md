# Contract C01: Transaction Event & Posting Specification

**Contract ID:** `C01_POSTING_DRAFT`
**Version:** `2.0` (Wave 1 Bounded Implementation Subset — CONTRACT_READY)
**Date:** 14 October 2026 (Asia/Karachi)
**Status:** **CONTRACT_READY (Bounded Wave 1 Subset)**
**Owner:** Antigravity (Developer A Backend)
**Reviewers:** Codex (Developer A Frontend), Fatima (Developer B & AI Orchestration)
**Target Task:** Wave 1 (`A-W1-03-BE-TRANSACTIONS` / `A-W1-03-FE-TRANSACTIONS`)

---

## 1. Revision History & Codex Review Disposition

| Version | Date | Author | Summary of Changes |
| :--- | :--- | :--- | :--- |
| 1.0 | 2026-10-14 | Antigravity | Initial draft proposing event posting, movement directions, and idempotency. |
| 1.1 | 2026-10-14 | Antigravity | Addressed Codex Mutation Findings M1–M3, M5–M6, M10: Removed duplicate opening movement; separated economic event identity from content hashes; standardized idempotency replay codes; documented cash balance propagation; defined atomic rejection for derived negatives; unified DTO enum naming. |
| 2.0 | 2026-10-14 | Antigravity | **CONTRACT_READY Bounded Wave 1 Implementation Subset:** Specified bounded wire protocol for `POST /api/finance/transactions`, `GET /api/finance/transactions`, `GET /api/finance/transactions/{id}`, and movement-inclusive account reads. Defined exact validation, PKR-only posting, `DIRECT_SALE_RECEIPT` / `OPERATING_EXPENSE_PAYMENT`, non-negative CASH sequence checks, deterministic pagination/sorting, and distinct retained later policies. |

---

## 2. Wave 1 Bounded Implementation Subset (Authoritative)

### 2.1. Bounded Scope & Constraints
- **Initial Event Types:**
  - `DIRECT_SALE_RECEIPT`: Account `INCREASE`; revenue recognition.
  - `OPERATING_EXPENSE_PAYMENT`: Account `DECREASE`; operating-expense recognition.
  - *No generic receipts/payments:* Customer advances, loans, invoice collections, supplier settlements, inventory purchases, and commissions require distinct later classifications.
- **Currency:** Posting is restricted to `PKR` accounts only in this slice. Non-PKR accounts remain readable but reject posting with HTTP 400.
- **Amount:** Positive exact two-decimal string within existing capacity (`amount > 0`, integer digits $\le 12$, maximum `"999999999999.99"`).
- **Effective Date:** Calendar date `YYYY-MM-DD` in `Asia/Karachi`. Invariant: `account.openingDate <= effectiveDate <= today(Asia/Karachi)`.
- **Account State:** Target account must be `ACTIVE` and belong to the authenticated active business tenant.
- **Balance Authority & Baselines:**
  - The account `openingBalance` remains the single immutable baseline; no second opening movement is generated.
  - Account balances are computed directly from `openingBalance` plus effective movements where `effectiveDate <= effectiveCutoffDate`.
  - `BANK` and `WALLET` accounts may have negative balances (e.g. approved overdraft / credit line).
  - `CASH` accounts disallow negative balances. For backdated outflows, the backend evaluates the *subsequent chronological balance sequence* up to today; if the proposed outflow would cause the cash balance on any date in that sequence to fall below zero, the transaction is rejected atomically with HTTP 400.
- **Audit & Idempotency:** Every post creates an event, movement, idempotency record, and security audit event atomically. Actor user ID and business tenant ID are recorded distinctly.
- **Unchanged Monthly Data:** Transaction posting creates no double-entry movements outside the account ledger and performs zero writes to `MonthlyRecord`, composite health scores, or recommendations. Existing monthly health calculations continue using existing snapshots.

### 2.2. Permissions & Security
- Permissions:
  - `FINANCIAL_TRANSACTION_READ`: Granted to `OWNER`, `ACCOUNTANT`, `MANAGER`, and `VIEWER`.
  - `FINANCIAL_TRANSACTION_POST`: Granted strictly to `OWNER` and `ACCOUNTANT` only (`MANAGER` and `VIEWER` receive HTTP 403 Forbidden).
- Standard session cookie/token and established `X-XSRF-TOKEN` CSRF header required for mutating requests.
- Active business tenant resolved strictly server-side (`ActiveBusinessContext`).

---

## 3. Wire Protocol & Endpoint Specification

### 3.1. POST /api/finance/transactions
Creates and posts a discrete financial transaction event and its underlying account movement.

#### Request Headers
```http
POST /api/finance/transactions HTTP/1.1
Content-Type: application/json
Idempotency-Key: <opaque-uuid-or-string, 1 to 100 characters>
X-XSRF-TOKEN: <csrf-token>
```

#### Request Payload
```json
{
  "accountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "eventType": "DIRECT_SALE_RECEIPT",
  "amount": "15000.00",
  "effectiveDate": "2026-10-14",
  "description": "Counter retail sale"
}
```

#### Request Fields
| Field | Type | Required | Constraints / Rules |
| :--- | :--- | :--- | :--- |
| `accountId` | `UUID` | **Required** | Must identify an existing `ACTIVE` account belonging to the active business with currency `PKR`. |
| `eventType` | `String` | **Required** | `DIRECT_SALE_RECEIPT` or `OPERATING_EXPENSE_PAYMENT`. Backend determines classification and movement direction. |
| `amount` | `String` | **Required** | Positive decimal string, strictly `> 0`, max 2 decimal places, integer capacity $\le 12$ digits (`<= 999999999999.99`). |
| `effectiveDate` | `String` | **Required** | Format `YYYY-MM-DD`. Must satisfy `account.openingDate <= effectiveDate <= today(Asia/Karachi)`. |
| `description` | `String` | Optional | Free-text note, 1 to 255 characters, trimmed. |

#### Response: HTTP 201 Created
```json
{
  "id": "c1f72a9b-7e6d-4952-b883-fa38d726190a",
  "businessId": "d0e14a22-8e42-45e6-993d-4c31154c1bb2",
  "accountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "accountName": "Retail Cash Counter",
  "accountType": "CASH",
  "eventType": "DIRECT_SALE_RECEIPT",
  "movementDirection": "INCREASE",
  "amount": "15000.00",
  "currency": "PKR",
  "baseAmountPkr": "15000.00",
  "effectiveDate": "2026-10-14",
  "description": "Counter retail sale",
  "resultingBalance": "65000.00",
  "movements": [
    {
      "id": "e4f88120-1b2c-473d-82d9-bb5b2e259b3a",
      "accountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "movementDirection": "INCREASE",
      "amount": "15000.00",
      "currency": "PKR",
      "effectiveDate": "2026-10-14",
      "createdAt": "2026-10-14T12:00:00Z"
    }
  ],
  "createdByUserId": "52c80387-9bb3-470a-939e-26f633342379",
  "createdAt": "2026-10-14T12:00:00Z"
}
```

#### Idempotency Behavior
- **Identical Replay:** Sending the same `Idempotency-Key` with the identical normalized payload hash returns HTTP `201 Created` with the original response body verbatim.
- **Mismatched Payload Conflict:** Reusing an `Idempotency-Key` with a different payload hash returns HTTP `409 Conflict`.
- **Replay Authorization:** Every replay verifies that the active business and user possess `FINANCIAL_TRANSACTION_POST` permission.

---

### 3.2. GET /api/finance/transactions/{id}
Retrieves detailed information for a single transaction.

#### Request Headers
```http
GET /api/finance/transactions/{id} HTTP/1.1
```

#### Response: HTTP 200 OK
Returns the identical `TransactionDetailResponse` schema as shown in the 201 response above. If the transaction does not exist or belongs to another business, returns HTTP `404 Not Found`.

---

### 3.3. GET /api/finance/transactions
Retrieves a paginated, filterable list of transactions for the active business.

#### Request Parameters
| Parameter | Type | Required | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `accountId` | `UUID` | Optional | `null` | Filter by target account ID. |
| `eventType` | `String` | Optional | `null` | Filter by `DIRECT_SALE_RECEIPT` or `OPERATING_EXPENSE_PAYMENT`. |
| `startDate` | `String` | Optional | `null` | Format `YYYY-MM-DD`. Filter `effectiveDate >= startDate`. |
| `endDate` | `String` | Optional | `null` | Format `YYYY-MM-DD`. Filter `effectiveDate <= endDate`. |
| `page` | `Integer` | Optional | `0` | Zero-based page index. |
| `size` | `Integer` | Optional | `20` | Page size (1 to 100). |

#### Sorting
Transactions are sorted deterministically by:
`ORDER BY effectiveDate DESC, createdAt DESC, id DESC`

#### Response: HTTP 200 OK
```json
{
  "content": [
    {
      "id": "c1f72a9b-7e6d-4952-b883-fa38d726190a",
      "businessId": "d0e14a22-8e42-45e6-993d-4c31154c1bb2",
      "accountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "accountName": "Retail Cash Counter",
      "accountType": "CASH",
      "eventType": "DIRECT_SALE_RECEIPT",
      "movementDirection": "INCREASE",
      "amount": "15000.00",
      "currency": "PKR",
      "baseAmountPkr": "15000.00",
      "effectiveDate": "2026-10-14",
      "description": "Counter retail sale",
      "resultingBalance": "65000.00",
      "createdByUserId": "52c80387-9bb3-470a-939e-26f633342379",
      "createdAt": "2026-10-14T12:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "effectiveCutoffDate": "2026-10-14",
  "observationAt": "2026-10-14T12:00:00Z"
}
```

---

### 3.4. Account Reads Incorporating Posted Movements
When reading accounts via `GET /api/finance/accounts` or `GET /api/finance/accounts/{id}`:
- The effective `currentBalance` is evaluated as:
  $$\text{currentBalance} = \text{openingBalance} + \sum_{\text{eff} \le \text{cutoff}} \text{Movements}_{\text{INCREASE}} - \sum_{\text{eff} \le \text{cutoff}} \text{Movements}_{\text{DECREASE}}$$
- If `account.openingDate > effectiveCutoffDate`, `currentBalance` remains the canonical zero string (e.g. `"0.00"` for PKR).
- If aggregate capacity or decimal precision overflows, `currentBalance` is set to `null` with `balanceStatus = "UNAVAILABLE"` and `balanceUnavailableReason = "CAPACITY_OVERFLOW"`.

---

### 3.5. Stable Error Envelopes

#### HTTP 400 Bad Request (Validation Failure)
```json
{
  "status": 400,
  "error": "Validation Failed",
  "message": "One or more request parameters are invalid",
  "errors": {
    "amount": "Amount must be greater than zero",
    "effectiveDate": "Effective date cannot precede account opening date",
    "cashBalance": "Transaction would cause cash account balance to become negative on 2026-10-10"
  }
}
```

#### HTTP 403 Forbidden
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "Role VIEWER does not have permission: FINANCIAL_TRANSACTION_POST"
}
```

#### HTTP 404 Not Found
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Financial account not found"
}
```

#### HTTP 409 Conflict
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Idempotency key 'idem-tx-001' was already used with different request parameters"
}
```

---

## 4. Retained Later Policies (Deferred Scope)

The following architectural and domain policies are explicitly deferred from this bounded Wave 1 slice:
1. **Inter-Account Transfers:** Atomic dual-movement transfer transactions between two accounts.
2. **Compensating Reversals:** Dedicated reversal endpoints referencing `reverses_transaction_id`.
3. **Multi-Currency & FX:** Posting in USD, EUR, GBP, AED, SAR, JPY, BHD, KWD, OMR with daily FX exchange rates.
4. **AR / AP Settlement & Invoices:** Invoice collections, supplier bill settlements, advances, and loans.
5. **Inventory Purchases & Pre-orders:** COGS and pre-order pricing integration.
6. **Monthly Financial Record Rollups:** Automatic aggregation into `MonthlyRecord` revenue/expense figures.
7. **Ledger-Mode Cutover:** Platform-wide transition from high-level monthly summaries to pure transaction-based financial health scoring.
