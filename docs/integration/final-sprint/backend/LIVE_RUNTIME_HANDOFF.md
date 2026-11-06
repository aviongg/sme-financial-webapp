# FinSight Phase 2 — Live Runtime & Fixture Handoff
**Task ID:** `FINAL-S3A-LIVE-RUNTIME-AND-FIXTURE-HANDOFF`
**Date:** Friday, 6 November 2026
**Audience:** Codex (Frontend Engineer) & Antigravity (Backend Lead / Git Coordinator)
**Status:** **ACTIVE & READY FOR E2E BROWSER ACCEPTANCE**

---

## 1. Verified Repository & Worktree Baseline

- **Repository Root:** `C:\Users\PC\Projects\sme-financial-webapp`
- **Branch:** `dev/suleman`
- **Merged Integration Baseline:** `2fa20c3b6ca59d04504290542fdfef21ef7e1da2` (Flyway V1–V37 unified)
- **Concurrent Feature Status:**
  - **Antigravity (Backend):** Task S2A completed (C11 Authoritative Financial Query Facade & AI Evidence Adapter integrated with read-only invariants).
  - **Codex (Frontend):** Task S2B in progress (Next.js components and `frontend/tests/unified-live.integration.mjs` implemented; editing paused during backend runtime launch).
- **Protected Files:**
  - `backend/.cp.txt` (Untracked, untouched).
  - `frontend/tests/obligations.live.mjs` (Untouched).
  - All frontend source files under `frontend/` remain completely untouched by Antigravity.
  - Zero Git write operations (no commits, merges, pushes, resets, or restores).

---

## 2. Running Services & Disposable Architecture

A persistent disposable runtime environment has been initialized and verified running locally on host `127.0.0.1`:

| Service | Address / Port | Role / Database | Status | Daemon / Process Info |
| :--- | :--- | :--- | :--- | :--- |
| **PostgreSQL 14.22** | `127.0.0.1:5433` | `sme_health` (Flyway V1–V37 applied) | **RUNNING** | Daemon task on port 5433 (`finsight-disposable-db`) |
| **Spring Boot Backend** | `http://127.0.0.1:8080` | `finsight_app` (DML only, `dev` profile) | **RUNNING** | Daemon task on port 8080 (`sme-health-backend`) |
| **Next.js Production** | `http://127.0.0.1:3100` | Frontend consumer (Rewrites `/api/*`) | Ready for Codex launch | Managed by Codex browser acceptance harness |

### Database Roles & Migration Compliance
- **Schema Owner & Migrator:** `finsight_migrator` (Executed Flyway V1 through V37 cleanly in 0.857s).
- **Runtime Application User:** `finsight_app` (Strictly DML only, connection pool active).
- **Migration Head:** Version 37 (`V37__create_inventory_catalog_tables.sql`).

---

## 3. Fixture Manifest & Configuration

Codex's browser acceptance suite (`frontend/tests/unified-live.integration.mjs`) reads the fixture manifest from `process.env.FINSIGHT_LIVE_FIXTURE`.

- **Fixture File Location:** `C:\Users\PC\Projects\sme-financial-webapp\tmp\finsight-live-fixture.json`
- **Environment Variable for Harness:**
  ```powershell
  $env:FINSIGHT_LIVE_FIXTURE="C:\Users\PC\Projects\sme-financial-webapp\tmp\finsight-live-fixture.json"
  ```

### Manifest Schema & Values
```json
{
  "disposable": true,
  "migrationHead": 37,
  "frontendUrl": "http://127.0.0.1:3100",
  "backendUrl": "http://127.0.0.1:8080",
  "email": "live.owner@finsight.local",
  "password": "Synthetic-Owner-2026!",
  "businessAId": "87fdc51d-3f50-475c-b04a-19244464397b",
  "businessAName": "Apex Industrial Ltd",
  "businessBId": "d45d4c3f-0327-4869-9de1-3a2f1c801f21",
  "businessBName": "Zenith Commercial Ltd",
  "account1Id": "391f7195-39e0-4398-8076-89cfc7940ee5",
  "account2Id": "77648672-b72b-40be-ac0e-f2c5b1ce077e",
  "account3Id": "e0ec4240-ca10-48a5-a153-186cc016bf69",
  "obligationId": "0ffe1ad6-46a1-40c6-be7a-6e539b039457",
  "customerCounterpartyId": "e4adda6b-de4c-42a5-ad46-237680f68f60",
  "supplierCounterpartyId": "8933da96-c4b2-44af-8396-d69e1d2f0ef7"
}
```

---

## 4. Authoritative Financial Fixtures & Numerical Invariants

All figures originate from Developer A's backend calculation engine and posted ledger movements:

### 4.1. Account Balances & Cash Movement
- **Account 1 (`Apex Main Operations`, BANK, PKR):**
  - Opening balance: **PKR 100,000.00**
  - Direct sale receipt (`DIRECT_SALE_RECEIPT`): **+PKR 20,000.00** $\rightarrow$ balance PKR 120,000.00
  - Operating expense payment (`OPERATING_EXPENSE_PAYMENT`): **-PKR 5,000.00** $\rightarrow$ balance PKR 115,000.00
  - Inter-account transfer to Account 2 (`INTERNAL_TRANSFER`): **-PKR 10,000.00**
  - Resulting Account 1 Balance: **PKR 105,000.00**
- **Account 2 (`Apex Reserve Account`, BANK, PKR):**
  - Opening balance: **PKR 0.00**
  - Inter-account transfer in from Account 1: **+PKR 10,000.00**
  - Resulting Account 2 Balance: **PKR 10,000.00**
- **Combined Operating Cash (Account 1 + Account 2):**
  - $\text{PKR } 105,000.00 + \text{PKR } 10,000.00 = \mathbf{\text{PKR } 115,000.00}$ (Invariant preserved across internal transfers).

### 4.2. Trade Collections, Customer Advance & Obligations
- **Account 3 (`Apex Trade Collections`, BANK, PKR):**
  - Opening balance: **PKR 0.00**
  - Advance collection received: **+PKR 30,000.00**
  - Settlement payment received: **+PKR 50,000.00**
  - Resulting Account 3 Balance: **PKR 80,000.00**
- **Customer Counterparty:** `Alpha Wholesale Buyers` (CUSTOMER)
- **Receivable Obligation (`INV-2026-001`):**
  - Original invoice amount: **PKR 100,000.00**
  - Customer advance applied: **PKR 30,000.00** $\rightarrow$ remaining PKR 70,000.00 (`PARTIALLY_PAID`)
  - Settlement recorded: **PKR 50,000.00** $\rightarrow$ remaining **PKR 20,000.00** (`PARTIALLY_PAID`)
  - Total paid/applied: **PKR 80,000.00**; Status: `OPEN`; Settlement Status: `PARTIALLY_PAID`.

### 4.3. Supplier Counterparty & Recurring Lease
- **Supplier Counterparty:** `Apex Raw Materials` (SUPPLIER)
- **Recurring Transaction Rule:** `Monthly Factory Rent` (Lease payment PKR 15,000.00/month).

### 4.4. Historical Records & Canonical Health Score
- **Monthly Records:** `2026-07`, `2026-08`, `2026-09` persisted with valid financial figures.
- **Canonical Health Score:** Computed for `2026-09` via `health-score-v1` (Cash Flow Stability 30%, Profitability 25%, Repayment 20%, Trend 15%, Compliance 10%).

### 4.5. Developer B Scoped Records (F04–F08)
- **Commercial Documents (F04):** Draft invoice `INV-LIVE-DRAFT-01` to `Alpha Wholesale Buyers` (financial effect: `NONE`).
- **Projects (F05):** Project `PRJ-LIVE-01` (`Apex Factory Expansion`, kind `PROJECT`).
- **Inventory Items (F08):** SKU `SKU-STEEL-01` (`Industrial Steel Sheets`, `RAW_MATERIAL`).

### 4.6. Tenant Isolation (Business B Target)
- **Business B:** `Zenith Commercial Ltd` (`services`).
- **Account B1:** `Zenith Operations` (PKR 88,888.00).
- **Obligation B1:** Payable PKR 45,000.00 to `Zenith Tech Solutions`.
- **Tenant Boundary:** Inaccessible to Business A; cross-tenant query returns `404 Not Found`.

---

## 5. Authentication, CSRF & Session Lifecycle

The live backend enforces standard production-grade cookie and CSRF protections:

1. **CSRF Acquisition:**
   - **Endpoint:** `GET /api/auth/csrf`
   - **Response:** `{ "token": "<uuid>", "headerName": "X-XSRF-TOKEN", "parameterName": "_csrf" }`
   - Also sets cookie `XSRF-TOKEN`.
2. **User Login:**
   - **Endpoint:** `POST /api/auth/login`
   - **Headers:** `X-XSRF-TOKEN: <csrf-token>`, `Content-Type: application/json`
   - **Body:** `{ "email": "live.owner@finsight.local", "password": "Synthetic-Owner-2026!" }`
   - **Response:** Sets HTTP-only cookie `FINSIGHT_SESSION`.
3. **Session Cookie Propagation:**
   - Include `FINSIGHT_SESSION` on all requests.
   - For all mutating requests (`POST`, `PUT`, `PATCH`, `DELETE`), fetch fresh CSRF from `GET /api/auth/csrf` and include header `X-XSRF-TOKEN: <token>`.
4. **Active Business Context:**
   - FinSight resolves tenant identity strictly server-side from the active session.
   - **Endpoint:** `POST /api/businesses/active`
   - **Body:** `{ "businessId": "<businessAId>" }`

---

## 6. C11 AI Financial Evidence API Contract

Developer B's Ask FinSight interface queries `/api/ai/explain`. Developer A's authoritative `FinancialQueryFacade` provides deterministic facts with zero synthetic mock numbers.

### Request Payload
```json
POST /api/ai/explain
Content-Type: application/json
X-XSRF-TOKEN: <csrf-token>

{
  "type": "ask",
  "language": "en",
  "question": "What is my cash position?"
}
```

### Verified Response Structure
```json
{
  "schemaVersion": "ai-explanation-v1",
  "language": "en",
  "summary": "This explanation reflects the current authoritative cash and bank position from posted account movements.",
  "interpretation": [
    "Current cash balance (2026-11-06): 195000.00",
    "Opening balance (2026-11-06): 100000.00",
    "Net movements (2026-11-06): 95000.00",
    "Total financial accounts (2026-11-06): 3",
    "Apex Main Operations (**** Ltd) (2026-11-06): 105000.00",
    "Apex Reserve Account (****Bank) (2026-11-06): 10000.00",
    "Apex Trade Collections (****Bank) (2026-11-06): 80000.00"
  ],
  "considerations": [],
  "limitations": [
    "Recorded associations do not establish why a business event happened. Review the underlying records before acting.",
    "Detailed inventory, invoices, customer receivables and project finance are not available to this assistant.",
    "AI is unavailable. This is a deterministic FinSight explanation, not a GPT response."
  ],
  "evidence": [
    {
      "id": "cash_position.PKR.balance",
      "kind": "cash_position",
      "label": "Current cash balance",
      "value": "195000.00",
      "unit": "PKR",
      "period": "2026-11-06",
      "sourceMode": "POSTED_ACTUAL"
    }
  ],
  "provenance": {
    "mode": "deterministic",
    "model": null,
    "promptVersion": "evidence-selection-v1",
    "evidenceFingerprint": "...",
    "cached": false
  },
  "reasonCode": "evidence_changed"
}
```

### Cache Freshness Verification
- **Test:** Posted a new transaction of PKR 1,000.00 (`DIRECT_SALE_RECEIPT`) and re-queried `/api/ai/explain`.
- **Evidence Result:** Pre-mutation cash was PKR 195,000.00; post-mutation cash immediately updated to PKR 196,000.00 with `cached: false`.
- **Verdict:** Stale caching is absent; real-time recalculation from authoritative ledger is verified.

---

## 7. Supported Acceptance Journeys

The running backend fully supports the following routes and API flows:

1. **Authentication & Session:** `/login`, `/api/auth/csrf`, `/api/auth/login`, `/api/businesses/active`.
2. **Accounts & Balances:** `/finance/accounts`, `/api/finance/accounts`, `/api/finance/accounts/{id}`.
3. **Transaction Posting:** `/records?add=1`, `/api/finance/transactions` (`DIRECT_SALE_RECEIPT`, `OPERATING_EXPENSE_PAYMENT`, `INTERNAL_TRANSFER`).
4. **Receivables & Payables:** `/records?view=receivables`, `/records?view=payables`, `/api/finance/obligations`.
5. **Daily Cash Forecasting:** `/finance/forecast`, `/api/finance/forecast/daily?currency=PKR&horizonDays=30`.
6. **Financial Risk Assessment:** `/finance/risks`, `/api/finance/risk/assessment?currency=PKR&horizonDays=30`.
7. **Commercial Invoices (F04):** `/invoices`, `/api/invoices`, `/api/invoices/{id}/finalize` (Non-posting verified: balances and scores unchanged).
8. **Projects (F05):** `/projects`, `/api/projects`.
9. **Estimates (F06):** `/estimates`, `/api/estimates`.
10. **Samples (F09):** `/samples`, `/api/samples`.
11. **Inventory Catalog (F08):** `/inventory`, `/api/inventory`.
12. **Ask FinSight Copilot (C11):** `/ask-finsight`, `/api/ai/explain`.
13. **Tenant Isolation:** Foreign accounts return `404 Not Found`; session switching clears previous business state.
14. **Bilingual Support:** English (LTR) and Urdu (RTL) locales supported without data loss.

---

## 8. Runtime Lifecycle & Operations Commands

### How to Check Running Status
```powershell
# Verify PostgreSQL listening on 5433
Get-NetTCPConnection -LocalPort 5433

# Verify Spring Boot listening on 8080
Get-NetTCPConnection -LocalPort 8080

# Verify CSRF endpoint
curl -s http://127.0.0.1:8080/api/auth/csrf
```

### How to Reset Database Fixtures to Pristine State
If acceptance testing introduces dirty records and you need to reset the deterministic baseline:
```powershell
cd C:\Users\PC\Projects\sme-financial-webapp\backend
.\mvnw.cmd test -Dtest=LiveRuntimeHandoffSeeder
```
*Note: This cleans transactions, movements, obligations, and accounts, and regenerates `tmp/finsight-live-fixture.json` without modifying code or migrations.*

### Coordinated Restart Procedure (If Needed)
If a backend reboot is required, coordinate explicitly:
```powershell
# Stop Spring Boot
Stop-Process -Id <pid>

# Restart Spring Boot with Dev Profile
$env:SPRING_PROFILES_ACTIVE="dev"
$env:POSTGRES_HOST="127.0.0.1"
$env:POSTGRES_PORT="5433"
$env:POSTGRES_DB="sme_health"
$env:POSTGRES_APP_USER="finsight_app"
$env:POSTGRES_APP_PASSWORD="TestAppPassword2026!"
$env:FINSIGHT_CRYPTO_KEY_K1="MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI="
$env:INTERNAL_SERVICE_SECRET="devb-browser-fixture-only-not-production"
$env:CORS_ALLOWED_ORIGINS="http://127.0.0.1:3100,http://localhost:3100,http://localhost:3000"
$env:AI_ENABLED="true"
$env:AI_PROVIDER="mock"
$env:AI_DEMO_ENABLED="true"
$env:AI_REQUESTS_PER_MINUTE="60"
$env:SERVER_PORT="8080"
$env:SERVER_ADDRESS="127.0.0.1"
cd backend
.\mvnw.cmd spring-boot:run
```

---

## 9. Shared Runtime Ownership & Acceptance Authorization

With the verification of:
1. Real PostgreSQL 14.22 on port 5433 with Flyway V1–V37.
2. Real Spring Boot backend on `http://127.0.0.1:8080`.
3. Deterministic test fixtures matching exact mathematical invariants.
4. All 10 smoke-test verification suites passing against the live HTTP server.
5. Pristine database state confirmed.

**Codex is now authorized to execute live E2E browser acceptance tests (`unified-live.integration.mjs`).**
Antigravity will maintain the running runtime untouched throughout Codex's testing window.
