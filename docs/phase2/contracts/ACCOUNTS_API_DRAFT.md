# Financial Accounts API Contract: Bounded Read Specification

**Contract ID:** `ACCOUNTS_API_DRAFT`  
**Version:** `1.3`  
**Date:** 14 October 2026  
**Status:**  
*   **Read Wire Specification:** `READY_FOR_BOUNDED_FRONTEND_DEV`  
*   **Backend Implementation:** `IMPLEMENTED` (`GET /api/finance/accounts`, `GET /api/finance/accounts/{id}`)  
*   **Role Grants & Temporary Module Policy:** `SULEMAN_AUTHORIZED`  
*   **Flyway Migration:** `V18_RESERVED`  
**Owner:** Antigravity (Developer A Backend)  
**Reviewers:** Codex (Developer A Frontend), Fatima (Developer B & AI Orchestration), Muhammad Suleman Adil (Project Lead)  
**Target Release:** Wave 1 (`A-W1-01-BE-READ` / `A-W1-01-FE-READ`)  

---

## 1. Scope & Implementation Boundary

> [!IMPORTANT]
> **Strict Read Boundary:** This contract specifies **strictly and exclusively** the read wire protocol for Financial Accounts:
> *   `GET /api/finance/accounts` (List accounts)
> *   `GET /api/finance/accounts/{id}` (Account detail)
> 
> Account creation (`POST`), transaction posting, balance adjustments, cutover controls, aggregate multi-currency summaries (`combinedBalances`), and evidence navigation links remain **completely excluded** from this specification.  
> 
> **Decoupled Development:** Frontend component and fixture development for accounts-read (`A-W1-01-FE-READ`) is unblocked by this wire specification and does **not** require freezing the entire posting or projection contract (C01/C02).

---

## 2. Revision History & Review Disposition

| Version | Date | Author | Summary of Changes |
| :--- | :--- | :--- | :--- |
| 1.0 | 2026-10-14 | Antigravity | Initial proposal covering listing, detail, and account creation. |
| 1.1 | 2026-10-14 | Antigravity | Addressed Codex review findings A1–A5: bounded read slice, added timestamps, aligned error envelopes, decoupled summaries. |
| 1.2 | 2026-10-14 | Antigravity | Finalized Read Specification for Planning: defined temporal semantics, restricted balance states to AVAILABLE/UNAVAILABLE, standardized error envelopes. |
| 1.3 | 2026-10-14 | Antigravity | **Adopted Option A (SULEMAN_AUTHORIZED) — Nullable `openingBalance` (`string \| null`):**<br>1. *Consumer-Impacting Change:* `openingBalance` is now nullable (`string \| null`) requiring Codex adapter adaptation.<br>2. *Unrepresentable Opening Invariant:* When opening data cannot be safely represented (`PRECISION_OVERFLOW`, `CAPACITY_OVERFLOW`, or `UNSUPPORTED_CURRENCY`), both `openingBalance` and `currentBalance` are emitted as `null` with `balanceStatus = UNAVAILABLE`. Never round, truncate, fabricate zero, or leak malformed amounts.<br>3. *Integrity Failure Isolation:* If opening data is valid but only current balance fails integrity reconciliation (`INTEGRITY_CHECK_FAILED`), the valid `openingBalance` is preserved while `currentBalance` is `null`.<br>4. *Future Opening Invariant:* Valid future-opening accounts retain their valid non-null `openingBalance`, with `currentBalance` at canonical zero before `openingDate`. Corrupt future-opening accounts evaluate to `UNAVAILABLE` with null balances. |

---

## 3. Temporal Semantics & Snapshot Consistency

1.  **Effective Cutoff Date (`effectiveCutoffDate`):**
    *   Represents the current business accounting date in Pakistan Standard Time (`Asia/Karachi`, UTC+05:00) as of the query execution.
    *   Formatted as ISO-8601 Date: `YYYY-MM-DD` (e.g. `"2026-10-14"`).
2.  **Observation Timestamp (`observationAt`):**
    *   The precise UTC instant associated with the database snapshot read by the query.
    *   Formatted as ISO-8601 UTC DateTime: `YYYY-MM-DDTHH:mm:ssZ` (e.g. `"2026-10-14T13:30:00Z"`).
3.  **Included vs. Excluded Movements:**
    *   **Included:** Posted movements visible in that snapshot whose `effectiveDate <= effectiveCutoffDate`.
    *   **Opening Balance Invariant:** The baseline `openingBalance` is included if and only if `openingDate <= effectiveCutoffDate`.
    *   **Pre-Opening Date Invariant:** If `openingDate > effectiveCutoffDate`, the account has a known balance of canonical zero under this read specification.
    *   **Excluded:** Any transaction whose `effectiveDate > effectiveCutoffDate` (future-dated entries) is strictly excluded from `currentBalance`.
4.  **Snapshot Consistency:**
    *   A single invocation of `GET /api/finance/accounts` executes within a repeatable read / snapshot transaction. All items, balances, and pagination counts in that single response reflect one internally consistent database snapshot.
    *   A subsequent call to `GET /api/finance/accounts/{id}` represents a separate query and may observe a newer snapshot than a preceding list query.
    *   Responses do not imply or report knowledge of transactions committed after `observationAt`.

---

## 4. Balance States & Machine-Readable Unavailability

To eliminate ambiguity, `balanceStatus` supports **exactly two** states:

1.  **`AVAILABLE`:**
    *   `currentBalance` is guaranteed to be a valid, exact decimal string conforming to the currency's declared scale.
    *   `openingBalance` is guaranteed to be a valid, exact decimal string conforming to the currency's declared scale.
    *   `balanceUnavailableReason` is `null`.
    *   *Pre-Opening Date Invariant:* If `openingDate > effectiveCutoffDate`, `openingBalance` remains non-null (the planned opening baseline), while `currentBalance` is guaranteed to be the currency's canonical zero (e.g. `"0.00"` for PKR, `"0"` for JPY).
2.  **`UNAVAILABLE`:**
    *   `currentBalance` is `null`. (The backend **never** substitutes `0.00` for an unavailable or unverified balance).
    *   `openingBalance` is `null` if the opening balance itself cannot be safely represented (due to precision overflow, capacity overflow, or unsupported currency metadata). The backend **never** rounds, truncates, fabricates zero, or leaks malformed decimal strings.
    *   *Integrity Isolation Invariant:* If the baseline opening data is valid and representable, but only the current balance calculation fails an integrity check (e.g. cached total does not reconcile to posted movements in future ledger slices), `openingBalance` is **preserved** as a valid exact decimal string while `currentBalance` is `null`.
    *   `balanceUnavailableReason` is populated with a stable, machine-readable string without leaking sensitive database internals:
        *   `"INTEGRITY_CHECK_FAILED"`: Cached balance does not reconcile to movement ledger (opening balance is preserved if valid).
        *   `"PRECISION_OVERFLOW"`: Source balance exceeds declared currency scale without approved rounding (opening balance is null if opening value caused the overflow).
        *   `"CAPACITY_OVERFLOW"`: Source balance exceeds maximum supported 12 integer digits (opening balance is null if opening value caused the overflow).
        *   `"UNSUPPORTED_CURRENCY"`: Account currency metadata is corrupt or not in the supported registry (opening balance is null).

*Note on Simplification:* The tentative state `RECONCILING` has been **removed** from this bounded read schema. No asynchronous background reconciliation engine is assumed or exposed in the initial read slice.

---

## 5. Endpoints & Request / Response Wire Protocol

### 5.1. List Financial Accounts
*   **Method & Path:** `GET /api/finance/accounts`
*   **Query Parameters:**
    *   `status` (optional, string): Filter by lifecycle: `ACTIVE`, `INACTIVE`, `ARCHIVED`. Default is `ACTIVE`.
    *   `accountType` (optional, string): Filter by type: `CASH`, `BANK`, `WALLET`.
    *   `currency` (optional, string, 3-letter ISO code): Case-insensitive query, normalized to uppercase.
    *   `page` (optional, integer): 0-indexed page number. Default `0`, minimum `0`.
    *   `size` (optional, integer): Page size. Default `50`, minimum `1`, maximum `100`.
*   **Ordering:** Strictly deterministic: `createdAt ASC, id ASC`.

#### Query Validation Rules
*   If `page < 0`, `size < 1`, or `size > 100`, returns HTTP `400 Bad Request`.
*   If `page` or `size` is a non-integer string (e.g. `page=abc`), returns HTTP `400 Bad Request`.
*   If `status` is not one of `ACTIVE`, `INACTIVE`, `ARCHIVED`, returns HTTP `400 Bad Request`.
*   If `accountType` is not one of `CASH`, `BANK`, `WALLET`, returns HTTP `400 Bad Request`.

#### Pagination Metadata Invariants
*   **Preserved Request Parameters:** The response echoes the valid requested `page` and `size`, even when zero items match or when the page is out of range.
*   **Zero Matches (Empty Business / Filter Mismatch):**
    ```json
    {
      "items": [],
      "page": 0,
      "size": 50,
      "totalElements": 0,
      "totalPages": 0
    }
    ```
*   **Out-of-Range Page (e.g. requested `page=5`, `size=20`, but only 12 accounts exist):**
    ```json
    {
      "items": [],
      "page": 5,
      "size": 20,
      "totalElements": 12,
      "totalPages": 1
    }
    ```

#### Normative List Response (200 OK — Typical Mixed State)
```json
{
  "items": [
    {
      "id": "e4f8d521-7291-49b0-bc34-927361a90c12",
      "accountName": "Meezan Bank - Main Operations",
      "accountType": "BANK",
      "currency": "PKR",
      "currencyDecimals": 2,
      "openingBalance": "100000.00",
      "openingDate": "2026-10-01",
      "currentBalance": "115000.00",
      "balanceStatus": "AVAILABLE",
      "balanceUnavailableReason": null,
      "status": "ACTIVE",
      "observationAt": "2026-10-14T13:30:00Z",
      "effectiveCutoffDate": "2026-10-14",
      "institutionName": "Meezan Bank Ltd",
      "accountIdentifier": "****4821",
      "createdAt": "2026-10-14T10:00:00Z",
      "updatedAt": "2026-10-14T12:30:00Z"
    },
    {
      "id": "18a2bc44-8902-4e78-9e54-bc7128509811",
      "accountName": "Petty Cash Drawer",
      "accountType": "CASH",
      "currency": "PKR",
      "currencyDecimals": 2,
      "openingBalance": "25000.00",
      "openingDate": "2026-10-01",
      "currentBalance": "21500.00",
      "balanceStatus": "AVAILABLE",
      "balanceUnavailableReason": null,
      "status": "ACTIVE",
      "observationAt": "2026-10-14T13:30:00Z",
      "effectiveCutoffDate": "2026-10-14",
      "institutionName": null,
      "accountIdentifier": null,
      "createdAt": "2026-10-14T10:05:00Z",
      "updatedAt": "2026-10-14T12:15:00Z"
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 2,
  "totalPages": 1
}
```

---

### 5.2. Get Account Detail
*   **Method & Path:** `GET /api/finance/accounts/{id}`
*   **Path Parameter:** `id` (string, required RFC 4122 UUID).

#### Normative Detail Response (200 OK — Available Balance)
```json
{
  "id": "e4f8d521-7291-49b0-bc34-927361a90c12",
  "accountName": "Meezan Bank - Main Operations",
  "accountType": "BANK",
  "currency": "PKR",
  "currencyDecimals": 2,
  "openingBalance": "100000.00",
  "openingDate": "2026-10-01",
  "currentBalance": "115000.00",
  "balanceStatus": "AVAILABLE",
  "balanceUnavailableReason": null,
  "status": "ACTIVE",
  "observationAt": "2026-10-14T13:30:00Z",
  "effectiveCutoffDate": "2026-10-14",
  "institutionName": "Meezan Bank Ltd",
  "accountIdentifier": "****4821",
  "notes": "Primary operational disbursement account",
  "createdAt": "2026-10-14T10:00:00Z",
  "updatedAt": "2026-10-14T12:30:00Z"
}
```

#### Normative Detail Response (200 OK — Unavailable Current Balance, Valid Opening)
```json
{
  "id": "f901ab23-4567-8901-cdef-1234567890ab",
  "accountName": "Tokyo Branch Settlement",
  "accountType": "BANK",
  "currency": "JPY",
  "currencyDecimals": 0,
  "openingBalance": "500000",
  "openingDate": "2026-10-01",
  "currentBalance": null,
  "balanceStatus": "UNAVAILABLE",
  "balanceUnavailableReason": "INTEGRITY_CHECK_FAILED",
  "status": "ACTIVE",
  "observationAt": "2026-10-14T13:30:00Z",
  "effectiveCutoffDate": "2026-10-14",
  "institutionName": "Mitsubishi UFJ",
  "accountIdentifier": "****9012",
  "notes": "Balance undergoing manual investigation",
  "createdAt": "2026-10-14T10:10:00Z",
  "updatedAt": "2026-10-14T12:45:00Z"
}
```

#### Normative Detail Response (200 OK — Unavailable Opening and Current Balance)
```json
{
  "id": "66666666-6666-4666-8666-666666666666",
  "accountName": "Legacy Settlement Account",
  "accountType": "BANK",
  "currency": "PKR",
  "currencyDecimals": 2,
  "openingBalance": null,
  "openingDate": "2026-10-01",
  "currentBalance": null,
  "balanceStatus": "UNAVAILABLE",
  "balanceUnavailableReason": "PRECISION_OVERFLOW",
  "status": "INACTIVE",
  "observationAt": "2026-10-14T13:30:00Z",
  "effectiveCutoffDate": "2026-10-14",
  "institutionName": "Habib Metropolitan Bank",
  "accountIdentifier": "****7890",
  "notes": "Persisted opening amount exceeded declared scale; opening and current balances unavailable",
  "createdAt": "2026-10-14T10:10:00Z",
  "updatedAt": "2026-10-14T12:45:00Z"
}
```

---

## 6. Standardized Error Responses (Matching `apiClient` Parser)

All error responses strictly match the contract expected by `frontend/src/lib/api/client.ts`:

### 1. 400 Bad Request — Validation Failure (Invalid UUID or Query Parameters)
```json
{
  "timestamp": "2026-10-14T13:30:00Z",
  "status": 400,
  "error": "Validation Failed",
  "message": "One or more request parameters are invalid",
  "errors": {
    "id": "Invalid UUID format: abc-123"
  }
}
```
*   `errors` map matches `body.errors` in `client.ts` line 34.
*   Single standardized error code: `"Validation Failed"`.

### 2. 401 Unauthorized — Session Invalidation / Expired
```json
{
  "timestamp": "2026-10-14T13:30:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication required"
}
```
*   Caught by `client.ts` line 101; triggers `finsight-session-expired` event.

### 3. 403 Forbidden — Missing Required Permission
```json
{
  "timestamp": "2026-10-14T13:30:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Caller does not have permission: FINANCIAL_ACCOUNT_READ"
}
```
*   Demonstrates denial due to missing `FINANCIAL_ACCOUNT_READ` grant (does not use an allowed role as a negative test example).

### 4. 404 Not Found — Non-Existent or Cross-Business Account Detail
```json
{
  "timestamp": "2026-10-14T13:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Financial account not found"
}
```
*   **Non-Disclosing Invariant:** Requesting an ID that exists in another business returns HTTP `404 Not Found` with this exact message, preventing cross-tenant existence disclosure.

### 5. 409 Conflict — Active Business Context Required
```json
{
  "error": "active_business_required",
  "message": "Select or create a business before using this feature."
}
```
*   Preserves exact handler output from `GlobalExceptionHandler.java`; triggers `finsight-business-changed` in `client.ts` line 96.

---

## 7. Module Integration Strategy (Bounded First Slice)

For the immediate Wave 1 read implementation:
*   **No Unapproved C03 Module Key:** The backend will **not** invent or hardcode a module key before Fatima delivers contract C03.
*   **No Global Navigation Integration:** Accounts UI is accessed via direct route `/finance/accounts` in development/preview. Global sidebar links remain disabled or unlinked until module resolver integration.
*   **Direct Access Security:** Access to `/finance/accounts` is governed strictly by authenticated session, valid active business context, and role permission.
*   **`module_disabled` is Deferred:** HTTP 403 `module_disabled` is a **future** integration behavior. In the first bounded slice, the endpoint responds to all authorized active businesses without checking an unapproved module registry.
*   **Review Decision:** Retained in state register as a team decision requiring Fatima's cross-review before global navigation enablement.

---

## 8. Role Permission Decision Record

```
Proposed Permission: FINANCIAL_ACCOUNT_READ
Status: RECOMMENDED_PENDING_TEAM_REVIEW
```

| Role | Proposed Access | Rationale | Backend Enforcement |
| :--- | :--- | :--- | :--- |
| **OWNER** | **Allowed** | Full financial transparency over business liquid assets. | Checked via `authService.requirePermission(request, BusinessPermission.FINANCIAL_ACCOUNT_READ)` |
| **ACCOUNTANT** | **Allowed** | Required for bookkeeping, reconciliation, and audit. | Checked via `requirePermission` |
| **MANAGER** | **Allowed** | Operational cash flow monitoring without write permissions. | Checked via `requirePermission` |
| **VIEWER** | **Allowed** | Read-only stakeholder and investor financial visibility. | Checked via `requirePermission` |
| **SALESPERSON** | **DENIED** | Salespeople must never see cash balances or bank accounts. | No grant by default. Not added in read slice. |

*   **Implementation Rule:** Frontend development and contract tests may proceed assuming this proposed matrix. Backend implementation will formally add `FINANCIAL_ACCOUNT_READ` to `BusinessPermission.java` once the team review decision is recorded.

---

## 9. Exact Decimal & Supported Currency Contract

### 9.1. Canonical Decimal String Syntax & Nullability
Every non-null decimal amount string (`openingBalance`, `currentBalance`) must strictly match the following canonical grammar:
```regex
^-?(0|[1-9]\d*)(\.\d+)?$
```

#### Field Nullability Rules (v1.3):
1. **`currentBalance`:** `string | null`. Non-null exact decimal string when `balanceStatus == AVAILABLE`; strictly `null` when `balanceStatus == UNAVAILABLE`.
2. **`openingBalance`:** `string | null`. Non-null exact decimal string when opening data is representable under declared currency/scale metadata; strictly `null` when opening data cannot be safely represented (`PRECISION_OVERFLOW`, `CAPACITY_OVERFLOW`, or `UNSUPPORTED_CURRENCY`).

#### Syntax Rules:
1.  **No Exponent Notation:** Expressions like `"1e5"` or `"1.5e+4"` are strictly forbidden.
2.  **No Leading Plus Sign:** Positive values must not have a leading `+` (e.g. `"100.00"`, never `"+100.00"`).
3.  **No Leading Integer Zeros:** `"050.00"` is invalid; must be `"50.00"`. A single `"0"` before the decimal point is required for values between -1 and 1 (e.g. `"0.50"`, `"-0.25"`).
4.  **No Negative Zero:** Values evaluating to zero must never be signed (canonical is `"0.00"`, never `"-0.00"`).
5.  **Scale Zero Formatting:** For currencies with 0 decimals (`JPY`), the string contains **no decimal point** (e.g. `"1500"`, `"0"`).
6.  **Integer Digit Capacity:** Exactly 12 maximum integer digits.

### 9.2. Supported Currency Registry & Decimal Scale Mapping

| Currency Code | Currency Name | `currencyDecimals` | Canonical Zero | Maximum Positive Value | Maximum Negative Value |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`PKR`** | Pakistani Rupee (Base) | `2` | `"0.00"` | `"999999999999.99"` | `"-999999999999.99"` |
| **`USD`** | US Dollar | `2` | `"0.00"` | `"999999999999.99"` | `"-999999999999.99"` |
| **`EUR`** | Euro | `2` | `"0.00"` | `"999999999999.99"` | `"-999999999999.99"` |
| **`GBP`** | British Pound | `2` | `"0.00"` | `"999999999999.99"` | `"-999999999999.99"` |
| **`AED`** | UAE Dirham | `2` | `"0.00"` | `"999999999999.99"` | `"-999999999999.99"` |
| **`SAR`** | Saudi Riyal | `2` | `"0.00"` | `"999999999999.99"` | `"-999999999999.99"` |
| **`JPY`** | Japanese Yen | `0` | `"0"` | `"999999999999"` | `"-999999999999"` |
| **`BHD`** | Bahraini Dinar | `3` | `"0.000"` | `"999999999999.999"` | `"-999999999999.999"` |
| **`KWD`** | Kuwaiti Dinar | `3` | `"0.000"` | `"999999999999.999"` | `"-999999999999.999"` |
| **`OMR`** | Omani Rial | `3` | `"0.000"` | `"999999999999.999"` | `"-999999999999.999"` |

### 9.3. Read Serialization & Overflow Invariants
*   **Exact Value Preservation:** Read serialization preserves exact database scale without truncation or rounding.
*   **Unrepresentable Opening Values:** If an account's persisted opening balance cannot be represented safely under its declared currency scale without rounding or truncating (excess precision or capacity overflow), or if the account declares unsupported/inconsistent currency metadata, both `openingBalance` and `currentBalance` are emitted as `null`, with `balanceStatus = "UNAVAILABLE"` and the corresponding reason (`PRECISION_OVERFLOW`, `CAPACITY_OVERFLOW`, or `UNSUPPORTED_CURRENCY`). The backend never rounds, truncates, fabricates zero, or leaks malformed decimal strings.
*   **Integrity Isolation:** If the opening balance is valid and representable, but the current balance calculation fails an integrity check (e.g. movement ledger mismatch), the valid `openingBalance` string is preserved while `currentBalance` is `null` with `balanceUnavailableReason = "INTEGRITY_CHECK_FAILED"`.
*   **Capacity Overflow:** Any balance whose magnitude exceeds 12 integer digits causes `balanceStatus` to become `"UNAVAILABLE"` with `balanceUnavailableReason = "CAPACITY_OVERFLOW"`.
*   **Precision Mismatch:** Any balance with fractional digits exceeding `currencyDecimals` causes `balanceStatus` to become `"UNAVAILABLE"` with `balanceUnavailableReason = "PRECISION_OVERFLOW"`.
*   **Unsupported Currency:** If an account contains an unrecognized currency code or stored scale mismatch with the registry, the backend marks the balance `"UNAVAILABLE"` with `balanceUnavailableReason = "UNSUPPORTED_CURRENCY"` and both balances `null`.
*   **Pre-Opening Date Accounts:** If `openingDate > effectiveCutoffDate`, a valid account emits its planned `openingBalance` and an `AVAILABLE` `currentBalance` of canonical zero. A corrupt future-opening account emits `UNAVAILABLE` with null balances.
*   **Legacy MVP DTOs:** Existing monthly records and score DTOs continue using their legacy format (`NUMERIC(14,2)`). This currency-aware contract applies specifically to Phase 2 financial accounts.
*   **FX Rates:** FX-rate precision (scale 6) is outside this read schema and governed separately by Contract C09.

---

## 10. Implementation Dependencies Matrix

| Component / Layer | Status | Prerequisites |
| :--- | :--- | :--- |
| **A. Frontend Wire Specification** | **`READY_FOR_BOUNDED_FRONTEND_DEV`** | None. Wire contract is self-contained. |
| **B. Backend Read Implementation** | **`PENDING_MIGRATION_RESERVATION`** | Requires reserving Flyway V18 with Fatima. |
| **C. Role Grants Decision** | **`RECOMMENDED_PENDING_TEAM_REVIEW`**| Sign-off from Suleman Adil / Fatima on `FINANCIAL_ACCOUNT_READ`. |
| **D. First-Slice Module Policy** | **`PROPOSED_DIRECT_ACCESS`** | Agreement to allow direct route access prior to C03. |
| **E. Posting & Cutover (C01/C02)** | **`DRAFT_PENDING_REVIEW`** | Not required for `A-W1-01-FE-READ` development. |
