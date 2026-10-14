# Financial Account Creation Contract (v1.1)

**Contract Version:** `1.1` (Documentation correction: established CSRF header is `X-XSRF-TOKEN`)
**Date:** 14 October 2026 (Asia/Karachi)
**Task:** `A-W1-02-BE-CREATE` / `A-W1-02-CHECKPOINT`
**Scope:** Single-account creation (`POST /api/finance/accounts`) with immutable opening baseline and durable idempotency.
**Baseline Model:** Compatible with Accounts Read Contract v1.3 (`ACCOUNTS_API_DRAFT.md`).

---

## 1. Scope & Bounded Context

This specification governs the creation of financial accounts (`CASH`, `BANK`, `WALLET`) with initial opening balance configuration.

### In Scope
- Creation endpoint `POST /api/finance/accounts`.
- Server-side active business and user resolution (`ActiveBusinessContext`).
- Role-based authorization (`FINANCIAL_ACCOUNT_CREATE` for `OWNER` and `ACCOUNTANT` only).
- Strict validation of currency, decimal scale, capacity, and account type rules.
- Durable idempotency via `Idempotency-Key` header with normalized SHA-256 payload digest.
- Duplicate account name conflict detection scoped to active business.
- Default opening date resolution to today in `Asia/Karachi`.
- Support for explicit past and future opening dates consistent with temporal cutoff rules.
- 201 Created response reusing `AccountDetailResponse` format from Accounts Read Contract v1.3.

### Out of Scope (Non-Accepted)
- No double-entry transaction posting or ledger cutover.
- No second opening movement or double-counting in ledger balances.
- No impact on `MonthlyRecord`, revenue, cash-flow totals, financial health scores, or deterministic advice.
- No account editing (`PUT`/`PATCH`), status archiving, or deletion.
- No global sidebar navigation changes or C03 module resolver keys.
- `accountIdentifier` input is omitted from the V1 creation wire payload to ensure zero unencrypted raw banking PII enters storage or logs.

---

## 2. Security & Authorization

### 2.1. Authentication & Tenant Isolation
- Requests must supply a valid session cookie or token.
- Active business tenant is resolved strictly server-side (`ActiveBusinessContext`). Client-supplied tenant overrides are rejected.
- Standard CSRF protection applies (`X-XSRF-TOKEN` header required on mutating requests).

### 2.2. Permissions
- Permission: `FINANCIAL_ACCOUNT_CREATE`.
- Role Matrix:
  - `OWNER`: **ALLOW**
  - `ACCOUNTANT`: **ALLOW**
  - `MANAGER`: **DENY (HTTP 403 Forbidden)** (Retains `FINANCIAL_ACCOUNT_READ` only)
  - `VIEWER`: **DENY (HTTP 403 Forbidden)** (Retains `FINANCIAL_ACCOUNT_READ` only)
  - `SALESPERSON`: **DENY (HTTP 403 Forbidden)**

---

## 3. Wire Protocol & Endpoint Specification

### 3.1. HTTP Method & Path
```http
POST /api/finance/accounts
```

### 3.2. Headers
| Header | Requirement | Format / Rules |
| :--- | :--- | :--- |
| `Content-Type` | **Required** | `application/json` |
| `Idempotency-Key` | **Required** | Non-blank string, 1 to 100 characters (e.g. UUIDv4). |
| `X-XSRF-TOKEN` | **Required** | Valid CSRF token matching the active session. |

---

## 4. Request Payload Schema

```json
{
  "accountName": "Main Operating Account",
  "accountType": "BANK",
  "currency": "PKR",
  "openingBalance": "50000.00",
  "openingDate": "2026-10-14",
  "institutionName": "Habib Bank Limited",
  "notes": "Primary payroll and vendor account"
}
```

### Field Definitions & Validation Rules

| Field | Type | Required | Rules & Invariants |
| :--- | :--- | :--- | :--- |
| `accountName` | `string` | **Yes** | Trimmed, non-blank, 1 to 100 characters. Unique within the active business context (case-preserving, duplicate match raises HTTP 409). |
| `accountType` | `string` | **Yes** | Must be one of: `CASH`, `BANK`, `WALLET`. Case-insensitive in request, normalized to uppercase. |
| `currency` | `string` | **Yes** | Exactly 3 uppercase ASCII letters matching supported `CurrencyRegistry` currencies: `PKR`, `USD`, `EUR`, `GBP`, `AED`, `SAR`, `JPY`, `BHD`, `KWD`, `OMR`. |
| `openingBalance` | `string` | **Yes** | Exact decimal string representation. Never rounded or truncated.<br>• For `CASH`: Must be non-negative (`>= 0`).<br>• For `BANK` / `WALLET`: Signed values allowed (can be negative for overdraft / credit lines, zero, or positive).<br>• Scale: Fractional digits cannot exceed official currency decimals (e.g. max 2 for PKR/USD, 0 for JPY, 3 for BHD).<br>• Capacity: Absolute value must be `< 10^12` (`999,999,999,999.9999`). |
| `openingDate` | `string` | *Optional* | Date in ISO format `YYYY-MM-DD`. If omitted or null, defaults to today's date in `Asia/Karachi`. Valid past, present, or future dates are accepted. |
| `institutionName` | `string` | *Optional* | Max 150 characters trimmed.<br>• For `CASH`: Must be null or omitted (providing an institution name for cash raises HTTP 400).<br>• For `BANK` / `WALLET`: Optional. |
| `notes` | `string` | *Optional* | Free-text string, max 1000 characters. |

---

## 5. Response Payload Schema (HTTP 201 Created)

On first success or authorized replay, the endpoint returns `HTTP 201 Created` with the complete `AccountDetailResponse` (reused verbatim from Accounts Read Contract v1.3):

```json
{
  "id": "c1f7a0b2-4d2a-4467-bc20-5c6a3f912e77",
  "accountName": "Main Operating Account",
  "accountType": "BANK",
  "currency": "PKR",
  "currencyDecimals": 2,
  "openingBalance": "50000.00",
  "openingDate": "2026-10-14",
  "currentBalance": "50000.00",
  "balanceStatus": "AVAILABLE",
  "balanceUnavailableReason": null,
  "status": "ACTIVE",
  "observationAt": "2026-10-14T10:47:00Z",
  "effectiveCutoffDate": "2026-10-14",
  "institutionName": "Habib Bank Limited",
  "accountIdentifier": null,
  "notes": "Primary payroll and vendor account",
  "createdAt": "2026-10-14T10:47:00Z",
  "updatedAt": "2026-10-14T10:47:00Z"
}
```

### Balance Invariants on Response
1. **Current / Past Opening Date (`openingDate <= effectiveCutoffDate`):**
   - `openingBalance`: Exact formatted string at currency scale (e.g. `"50000.00"`).
   - `currentBalance`: Matches `openingBalance` (e.g. `"50000.00"`).
   - `balanceStatus`: `AVAILABLE`.
   - `balanceUnavailableReason`: `null`.
2. **Future Opening Date (`openingDate > effectiveCutoffDate`):**
   - `openingBalance`: Exact formatted string at currency scale (e.g. `"50000.00"`).
   - `currentBalance`: Canonical zero at currency scale (e.g. `"0.00"` for PKR/USD, `"0"` for JPY, `"0.000"` for BHD).
   - `balanceStatus`: `AVAILABLE`.
   - `balanceUnavailableReason`: `null`.
3. **Status:**
   - Newly created accounts are always initialized with `status = "ACTIVE"`.

---

## 6. Durable Idempotency & Conflict Semantics

All account creations require the `Idempotency-Key` header. Idempotency is persisted durably and atomically within the database.

### 6.1. Scoping & Payload Digest
- Key uniqueness is bounded by `(business_id, operation, idempotency_key)` where `operation = 'ACCOUNT_CREATE'`.
- The request body is normalized (whitespace-trimmed, ordered keys) and hashed using SHA-256 (`request_hash`).

### 6.2. Behavior Matrix

| Condition | Idempotency Key Status | Payload Hash | Caller Permissions | Response Status & Body |
| :--- | :--- | :--- | :--- | :--- |
| **First Submission** | New / Unseen | N/A | Has `FINANCIAL_ACCOUNT_CREATE` | **HTTP 201 Created** with newly created account detail. |
| **Authorized Replay** | Seen | Identical (`==`) | Has `FINANCIAL_ACCOUNT_CREATE` | **HTTP 201 Created** with original response body verbatim. |
| **Conflicting Payload** | Seen | Different (`!=`) | Any | **HTTP 409 Conflict** (`"Idempotency key has already been used with a different request payload"`). |
| **Replay by Demoted Actor** | Seen | Identical (`==`) | Lacks `FINANCIAL_ACCOUNT_CREATE` | **HTTP 403 Forbidden** (Authorization is always rechecked before replay). |
| **Duplicate Account Name** | New / Unseen | N/A | Has `FINANCIAL_ACCOUNT_CREATE` | **HTTP 409 Conflict** (`"An account with name '%s' already exists for this business"`). |
| **Missing Key Header** | Absent / Blank | N/A | Any | **HTTP 400 Bad Request** (`"Missing required header: Idempotency-Key"`). |

---

## 7. Error Handling & Wire Error Schemas

### 7.1. Validation Errors (HTTP 400 Bad Request)
Returned when payload fails validation rules:
```json
{
  "status": 400,
  "error": "Validation Failed",
  "message": "One or more request parameters are invalid",
  "errors": {
    "accountName": "Account name is required",
    "openingBalance": "Cash account opening balance cannot be negative"
  }
}
```

Common error keys and messages:
- `accountName`: `"Account name is required"`, `"Account name must not exceed 100 characters"`.
- `accountType`: `"Invalid accountType: XYZ. Must be CASH, BANK, or WALLET"`.
- `currency`: `"Unsupported currency: XYZ. Supported currencies: PKR, USD, EUR, GBP, AED, SAR, JPY, BHD, KWD, OMR"`.
- `openingBalance`:
  - `"Opening balance is required"`.
  - `"Cash account opening balance cannot be negative"`.
  - `"Opening balance exceeds maximum capacity of 999,999,999,999"`.
  - `"Opening balance exceeds currency decimal scale of X"`.
- `openingDate`: `"Invalid date format. Expected YYYY-MM-DD"`.
- `institutionName`: `"Institution name is not permitted for CASH accounts"`, `"Institution name must not exceed 150 characters"`.
- `notes`: `"Notes must not exceed 1000 characters"`.
- `Idempotency-Key`: `"Missing required header: Idempotency-Key"`, `"Idempotency-Key must not exceed 100 characters"`.

### 7.2. Conflict Errors (HTTP 409 Conflict)
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "An account with name 'Main Operating Account' already exists for this business",
  "timestamp": "2026-10-14T10:47:00Z"
}
```

### 7.3. Forbidden Errors (HTTP 403 Forbidden)
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied: User does not possess the required permission [FINANCIAL_ACCOUNT_CREATE]",
  "timestamp": "2026-10-14T10:47:00Z"
}
```
