# Antigravity Task Handoff: A-W0-04

**Task ID:** `A-W0-04`  
**Task Title:** Finalize the bounded accounts-read specification for implementation planning  
**Author:** Antigravity (Developer A Backend & Checkpoint Git Coordinator)  
**Date:** 14 October 2026  
**Status:** `COMPLETED (DOCUMENTATION ONLY)`  

---

## 1. Exact Edited Files & Revisions

1.  [`docs/phase2/contracts/ACCOUNTS_API_DRAFT.md`](file:///c:/Users/PC/Projects/sme-financial-webapp/docs/phase2/contracts/ACCOUNTS_API_DRAFT.md) (v1.2) — Finalized accounts read wire specification.
2.  [`docs/PHASE2_IMPLEMENTATION_STATE.md`](file:///c:/Users/PC/Projects/sme-financial-webapp/docs/PHASE2_IMPLEMENTATION_STATE.md) (v1.2) — Updated contract status, separated implementation dependencies, and retained policy registers.
3.  [`docs/phase2/handoffs/ANTIGRAVITY_W0_04.md`](file:///c:/Users/PC/Projects/sme-financial-webapp/docs/phase2/handoffs/ANTIGRAVITY_W0_04.md) (v1.0) — This handoff document.

*Scope Invariant:* Assistant instructions (`AGENTS.md`, `frontend/AGENTS.md`, `backend/AGENTS.md`), mutation contracts (`C01`, `C02`), and architecture records (`ADR_001`) were **not modified** during this task.

---

## 2. Mapping of the Six Read Corrections

### 1. Temporal Semantics
*   `effectiveCutoffDate` is today's accounting calendar date in Pakistan Standard Time (`Asia/Karachi`, UTC+05:00).
*   `observationAt` is the UTC instant of the database snapshot read during request execution.
*   Includes only posted movements visible in that snapshot whose `effectiveDate <= effectiveCutoffDate`.
*   Includes opening balance if and only if `openingDate <= effectiveCutoffDate`. Prior to `openingDate`, the account has a known balance of canonical zero under this read specification.
*   Future-dated transactions (`effectiveDate > effectiveCutoffDate`) are strictly excluded.
*   A single list response reflects one consistent database snapshot across items, balances, and pagination counts. Detail requests execute independently and may observe a newer snapshot. Responses do not imply knowledge of postings committed after `observationAt`.

### 2. Balance States
*   Restricted strictly to **two** states:
    *   `AVAILABLE`: `currentBalance` is guaranteed to be a valid exact decimal string; `balanceUnavailableReason` is `null`.
    *   `UNAVAILABLE`: `currentBalance` is `null`; `balanceUnavailableReason` is populated with a stable machine-readable code without sensitive details (`INTEGRITY_CHECK_FAILED`, `PRECISION_OVERFLOW`, `CAPACITY_OVERFLOW`, `UNSUPPORTED_CURRENCY`).
*   `RECONCILING` was **completely removed**. No asynchronous reconciliation service is assumed or exposed in this read slice.

### 3. Pagination & Errors
*   Requested `page` and `size` parameters are preserved in both empty matches (`totalElements = 0`, `totalPages = 0`, `items = []`) and out-of-range queries (e.g. `page = 5`, `size = 20`, `totalElements = 12`, `totalPages = 1`, `items = []`).
*   Negative, non-integer, and over-limit query parameters are rejected with HTTP 400 (`Validation Failed`) and a structured `errors` map matching `frontend/src/lib/api/client.ts`.
*   Invalid UUID path parameters return HTTP 400 with standardized error code `ERROR_INVALID_UUID` in `errors.id`.
*   Permission checks evaluate `FINANCIAL_ACCOUNT_READ` consistently. The 403 documentation example demonstrates denial due to an unauthorized role without `FINANCIAL_ACCOUNT_READ`, rather than using an allowed role as a negative example.
*   Existing session expiration (401) and active business requirements (409 `active_business_required`) remain preserved.
*   Cross-business account detail requests return non-disclosing HTTP `404 Not Found`.

### 4. Module Behavior (Bounded First Slice)
*   No invented C03 module keys or unapproved module resolvers are hardcoded.
*   Direct access via route `/finance/accounts` is supported during slice 1 development, governed by session and role permissions.
*   Global navigation integration is deferred until Fatima delivers the C03 Module Resolver contract.
*   HTTP 403 `module_disabled` is noted as future integration behavior; it is not returned in slice 1.

### 5. Role Permission Decision Record
*   **Recommended Grants (`FINANCIAL_ACCOUNT_READ`):** `OWNER`, `ACCOUNTANT`, `MANAGER`, `VIEWER`.
*   **Default Denial:** `SALESPERSON` (no grant).
*   Formally labeled: **`RECOMMENDED_PENDING_TEAM_REVIEW`**.
*   Frontend fixture and component development may proceed assuming this matrix; backend role configuration will be enabled once the team decision is recorded.

### 6. Exact Decimal Contract & Supported Currencies
*   Supported Currencies & Scale: `PKR` (2), `USD` (2), `EUR` (2), `GBP` (2), `AED` (2), `SAR` (2), `JPY` (0), `BHD` (3), `KWD` (3), `OMR` (3).
*   Capacity bounds: exactly 12 maximum integer digits and exactly the currency's declared scale.
*   Scale zero (`JPY`) contains **no decimal point** (e.g. `"0"`, `"1500"`, `"999999999999"`).
*   Canonical zeros: JPY `"0"`, PKR `"0.00"`, BHD `"0.000"`.
*   No exponent notation (`1e5`), leading plus sign (`+`), leading integer zeros (`050.00`), or negative zero (`-0.00`).
*   Maximum positive and negative examples:
    *   JPY: `"999999999999"` / `"-999999999999"`
    *   PKR: `"999999999999.99"` / `"-999999999999.99"`
    *   BHD: `"999999999999.999"` / `"-999999999999.999"`
*   Excess-precision or overflowing balances become `UNAVAILABLE` (`"PRECISION_OVERFLOW"`, `"CAPACITY_OVERFLOW"`), never silently rounded or truncated.
*   Invalid currency metadata yields `UNAVAILABLE` with `"UNSUPPORTED_CURRENCY"`.
*   Existing MVP DTOs continue using their legacy format (`NUMERIC(14,2)`). FX rates are governed separately by Contract C09.

---

## 3. Complete Normative JSON Examples

### 3.1. List Response (200 OK — Mixed Available State)
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

### 3.2. Detail Response (200 OK — Available Balance)
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

### 3.3. Detail Response (200 OK — Machine-Readable Unavailable Example)
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

---

## 4. Remaining Team Decisions (Separated from Wire Corrections)

1.  **Role Grants Sign-Off (Track C):** Formally approve the recommended `FINANCIAL_ACCOUNT_READ` grants for `OWNER`, `ACCOUNTANT`, `MANAGER`, and `VIEWER`.
2.  **First-Slice Direct Route Agreement (Track D):** Formally sign off on allowing direct route access to `/finance/accounts` prior to C03 Module Resolver integration.
3.  **Migration V18 Global Reservation (Track B):** Agree with Fatima on reserving Flyway `V18` for `financial_accounts` schema before backend runtime implementation.

---

## 5. Confirmation

*   **Endpoints Still Do Not Exist:** No backend code, migrations, controllers, services, or entities were created.
*   **Flyway V18 remains UNRESERVED.**
*   **Git State is Untouched:** No git mutations, commits, staging, or branch switches were executed.
*   **Untracked `backend/.cp.txt` is preserved.**
*   **Antigravity is in a PAUSED state.**
