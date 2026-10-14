# Financial Account Creation Backend Delivery Handoff

**Task:** `A-W1-02-BE-CREATE`
**Date:** 14 October 2026 (Asia/Karachi)
**Role:** Antigravity (Developer A Backend Coordinator)
**Status:** **BACKEND_VERIFIED — PAUSED**
**Active Development Branch:** `dev/suleman`
**Baseline Commit:** `8070af595ed48a8b88fe6fc67d05ac0fc86cd97b`
**Governing Contract:** [`docs/phase2/contracts/ACCOUNT_CREATE_V1.md`](file:///C:/Users/PC/Projects/sme-financial-webapp/docs/phase2/contracts/ACCOUNT_CREATE_V1.md) (v1.1)
**Frontend Contract Handoff:** [`docs/phase2/handoffs/ACCOUNT_CREATE_CONTRACT_READY.md`](file:///C:/Users/PC/Projects/sme-financial-webapp/docs/phase2/handoffs/ACCOUNT_CREATE_CONTRACT_READY.md)

---

## 1. Summary of Implemented Scope

Antigravity has implemented and verified the Financial Account Creation endpoint and opening balance setup under strict durable idempotency, tenant isolation, and financial precision discipline:

1. **Endpoint `POST /api/finance/accounts`:**
   - Accepts validated JSON payload for `CASH`, `BANK`, and `WALLET` accounts.
   - Enforces the mandatory `Idempotency-Key` header (1–100 characters).
   - Reuses `AccountDetailResponse` schema from Accounts Read Contract v1.3 with exact string decimals.
   - Returns `HTTP 201 Created` on first creation and on authorized idempotent replay.
2. **Security & Role Permissions:**
   - Introduced `FINANCIAL_ACCOUNT_CREATE` permission in `BusinessPermission.java`.
   - Mapped `FINANCIAL_ACCOUNT_CREATE` strictly to `OWNER` and `ACCOUNTANT` roles in `BusinessAuthorizationService.java`.
   - `MANAGER` and `VIEWER` roles retain read access (`FINANCIAL_ACCOUNT_READ`) but are denied creation with `HTTP 403 Forbidden`.
   - Active business context is resolved strictly server-side (`ActiveBusinessContext`).
   - CSRF protection enforced across the real HTTP mutation path (established `X-XSRF-TOKEN` header via Spring `CookieCsrfTokenRepository`).
3. **Persistence & Flyway Migration (V19):**
   - Clean remote collision check performed against `origin/main` (V17) and `origin/dev/fatima` (V17).
   - Authored migration `V19__create_financial_idempotency.sql` introducing table `financial_idempotency_records` with composite uniqueness on `(business_id, operation, idempotency_key)` and DML grants to `finsight_app`.
   - Migration V18 (`financial_accounts`) preserved unchanged.
4. **Opening Baseline & Financial Rules:**
   - Opening amounts are immutable account baselines in this slice.
   - Exact decimal string representation; no silent rounding or truncation.
   - Signed opening balances allowed for `BANK` and `WALLET` (negative represents approved overdraft / credit line).
   - `CASH` opening balance must be non-negative (`>= 0`) and cannot specify an `institutionName`.
   - Default opening date resolves to today's date in `Asia/Karachi`; valid past and future dates are supported.
   - Future opening dates (`openingDate > effectiveCutoffDate`) emit planned `openingBalance` and canonical zero `currentBalance` (`"0.00"` for PKR/USD, `"0"` for JPY, `"0.000"` for BHD).
   - Account creation creates zero double-entry movements, alters no `MonthlyRecord`, changes no revenue/cash totals, and triggers no score or advice updates.
   - Raw account identifiers are explicitly omitted from the V1 creation payload.
5. **Durable Idempotency & Conflict Semantics:**
   - Request payloads are normalized and hashed via SHA-256 (`request_hash`).
   - Identical key + identical payload hash returns original `201 Created` result with stored body verbatim without inserting a second account.
   - Same key + differing payload returns `HTTP 409 Conflict`.
   - Duplicate account names within the same business return `HTTP 409 Conflict`.
   - Distinct business tenants can create accounts with the same name without collision.

---

## 2. Test Verification Evidence

Targeted test execution via `mvnw.cmd` against unit and embedded PostgreSQL 14.22 test suites:

```text
[INFO] Running com.app.sme_health_backend.accounting.AccountControllerTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.app.sme_health_backend.accounting.AccountCreateServiceTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.app.sme_health_backend.accounting.AccountReadServiceTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.app.sme_health_backend.accounting.CurrencyRegistryTest
[INFO] Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.app.sme_health_backend.accounting.FinancialAccountPostgreSqlIT
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.app.sme_health_backend.identity.service.BusinessAuthorizationServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] Total tests run: 105, Failures: 0, Errors: 0, Skipped: 0
```

### Verified Integration Invariants
- **IT-CREATE-01:** Direct PostgreSQL persistence of `financial_accounts` and `financial_idempotency_records`, authorized replay, and conflict on altered payload.
- **IT-CREATE-02:** Duplicate account name rejection within business (409) and multi-tenant isolation.
- **IT-CREATE-03:** Account creation leaves `monthly_records` (count 0) and `score_results` (count 0) completely untouched.
- **IT-CREATE-04:** Signed negative opening balance allowed for `BANK` overdraft; negative `CASH` rejected.
- **IT-CREATE-05:** Real HTTP mutation path enforces CSRF protection (missing CSRF returns 403 Forbidden).

---

## 3. Inventory of Changed & Created Paths

### New Contracts & Documentation
- `docs/phase2/contracts/ACCOUNT_CREATE_V1.md`
- `docs/phase2/handoffs/ACCOUNT_CREATE_CONTRACT_READY.md`
- `docs/phase2/handoffs/ANTIGRAVITY_W1_02_BE_CREATE.md`
- `docs/PHASE2_IMPLEMENTATION_STATE.md` (updated to v1.5)

### Database Migration
- `backend/src/main/resources/db/migration/V19__create_financial_idempotency.sql`

### Shared Backend
- `backend/src/main/java/com/app/sme_health_backend/identity/model/BusinessPermission.java`
- `backend/src/main/java/com/app/sme_health_backend/identity/service/BusinessAuthorizationService.java`
- `backend/src/test/java/com/app/sme_health_backend/identity/service/BusinessAuthorizationServiceTest.java`

### Backend Accounting
- `backend/src/main/java/com/app/sme_health_backend/accounting/controller/AccountController.java`
- `backend/src/main/java/com/app/sme_health_backend/accounting/dto/AccountCreateRequest.java`
- `backend/src/main/java/com/app/sme_health_backend/accounting/entity/FinancialIdempotencyRecord.java`
- `backend/src/main/java/com/app/sme_health_backend/accounting/model/CurrencyRegistry.java`
- `backend/src/main/java/com/app/sme_health_backend/accounting/repository/FinancialAccountRepository.java`
- `backend/src/main/java/com/app/sme_health_backend/accounting/repository/FinancialIdempotencyRepository.java`
- `backend/src/main/java/com/app/sme_health_backend/accounting/service/AccountCreateService.java`
- `backend/src/main/java/com/app/sme_health_backend/accounting/service/AccountReadService.java`

### Backend Tests
- `backend/src/test/java/com/app/sme_health_backend/accounting/AccountControllerTest.java`
- `backend/src/test/java/com/app/sme_health_backend/accounting/AccountCreateServiceTest.java`
- `backend/src/test/java/com/app/sme_health_backend/accounting/FinancialAccountPostgreSqlIT.java`

---

## 4. Remaining Limitations & Outstanding Gates

1. **Opening-Only Baseline:** Balances reflect only persisted opening amounts or canonical zero for future-dated accounts. Full movement ledger posting and cutover reconciliation await subsequent tasks.
2. **Fatima Migration Notification:** Migration V19 is reserved and verified locally. Formal cross-team notification to Fatima remains pending.
3. **Production Gates:** PostgreSQL 17 TLS/HBA, container packaging, SMTP/Vision/WhatsApp provider verifications, and baseline risk items R01–R03 remain outstanding.

---

## 5. Constraints Confirmation
- **No Git mutations were performed:** Working tree modifications remain unstaged/uncommitted.
- **No frontend files were touched:** All `frontend/` files remain untouched.
- **No long-running ports occupied:** All test instances terminated cleanly.

Antigravity is **PAUSED**.
