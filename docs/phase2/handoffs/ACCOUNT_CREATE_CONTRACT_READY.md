# Financial Account Creation Contract Handoff (Codex Frontend Ready)

**Handoff Identifier:** `ACCOUNT_CREATE_CONTRACT_READY`
**Date:** 14 October 2026 (Asia/Karachi)
**Author:** Antigravity (Developer A Backend Coordinator)
**Target Assistant:** Codex (Developer A Frontend)
**Status:** **CONTRACT_READY — STABLE SPECIFICATION PUBLISHED**
**Governing Contract:** [`docs/phase2/contracts/ACCOUNT_CREATE_V1.md`](file:///C:/Users/PC/Projects/sme-financial-webapp/docs/phase2/contracts/ACCOUNT_CREATE_V1.md)

---

## 1. Summary of Published Contract

Antigravity has published the stable, complete wire specification for financial account creation:
- **Endpoint:** `POST /api/finance/accounts`
- **Response Format:** Reuses `AccountDetailResponse` v1.3 with exact string decimals and normalized metadata.
- **Required Header:** `Idempotency-Key` (e.g. standard UUIDv4 generated per user submission).
- **Security:** Requires `FINANCIAL_ACCOUNT_CREATE` (mapped to `OWNER` and `ACCOUNTANT` roles; `MANAGER` and `VIEWER` receive 403 Forbidden). Standard CSRF protection (`X-XSRF-TOKEN`) applies in cookie sessions (documentation correction from earlier `X-CSRF-TOKEN` typo).

The wire specification in [`ACCOUNT_CREATE_V1.md`](file:///C:/Users/PC/Projects/sme-financial-webapp/docs/phase2/contracts/ACCOUNT_CREATE_V1.md) is now **frozen** for backend implementation and frontend consumption.

---

## 2. Quick Reference for Frontend Implementation

### 2.1. Request Headers
```http
POST /api/finance/accounts HTTP/1.1
Content-Type: application/json
Idempotency-Key: <unique-uuid-v4>
X-XSRF-TOKEN: <csrf-token>
```

### 2.2. Request Body
```json
{
  "accountName": "Meezan Operational Account",
  "accountType": "BANK",
  "currency": "PKR",
  "openingBalance": "100000.00",
  "openingDate": "2026-10-14",
  "institutionName": "Meezan Bank Ltd",
  "notes": "Primary operational disbursement account"
}
```

### 2.3. Field Guidance for UI Forms
1. **`accountName` (required):** Trimmed, 1–100 characters. Unique per business.
2. **`accountType` (required):** Select dropdown: `CASH`, `BANK`, `WALLET`.
3. **`currency` (required):** Select dropdown from supported currencies (`PKR`, `USD`, `EUR`, `GBP`, `AED`, `SAR`, `JPY`, `BHD`, `KWD`, `OMR`). Default to business currency (e.g. `PKR`).
4. **`openingBalance` (required):** String input.
   - For `CASH`: Must be non-negative (`>= 0`).
   - For `BANK`/`WALLET`: Can be signed (negative allowed for overdraft / credit lines).
   - Decimal places must not exceed currency decimals (e.g. 2 for PKR/USD, 0 for JPY, 3 for BHD). Do not convert to JavaScript `Number`.
5. **`openingDate` (optional):** Date picker. Defaults to today's date in `Asia/Karachi`. Accepts past or future dates.
6. **`institutionName` (optional):** Free text, max 150 chars. Hidden or disabled when `accountType === "CASH"`.
7. **`notes` (optional):** Multiline text, max 1000 chars.
8. **`accountIdentifier` (omitted):** Note that raw account identifiers are explicitly omitted from creation in V1.

### 2.4. HTTP Responses
- **`201 Created`:** Account created or identical idempotent submission replayed. Returns `AccountDetailResponse`.
- **`400 Bad Request`:** Validation failure. Field errors are mapped in `errors: { [field]: string }`.
- **`403 Forbidden`:** Actor role is `MANAGER`, `VIEWER`, or unauthorized.
- **`409 Conflict`:** Duplicate account name within business OR existing `Idempotency-Key` sent with differing payload.

---

## 3. Next Steps for Teams
- **Backend (Antigravity):** Implementing backend service, repository, persistence, and tests against [`ACCOUNT_CREATE_V1.md`](file:///C:/Users/PC/Projects/sme-financial-webapp/docs/phase2/contracts/ACCOUNT_CREATE_V1.md).
- **Frontend (Codex):** Authorized to implement account creation forms, client adapters, and unit tests against [`ACCOUNT_CREATE_V1.md`](file:///C:/Users/PC/Projects/sme-financial-webapp/docs/phase2/contracts/ACCOUNT_CREATE_V1.md) when activated.
