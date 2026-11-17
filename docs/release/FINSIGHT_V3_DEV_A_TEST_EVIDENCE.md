# FinSight Candidate v3 — Developer A Test Execution & Verification Evidence

**Document ID:** `FINSIGHT-V3-DEV-A-TEST-EVIDENCE-01`  
**Date:** November 17, 2026  
**Auditor / Engineer:** Antigravity (Backend Architect & Git Coordinator for Developer A)  
**Execution Environment:** Windows 11 Workstation, Java 21 (Temurin-21.0.12.1+1-LTS), Apache Maven 3.9.16 via `backend/mvnw.cmd`  
**Target Codebase:** Zarwia / FinSight Candidate v3 (`origin/integration/finsight-production-candidate-v3` @ `19547bf3dda9744d51e74c87a908e2e42577299c`)  

---

## 1. Local Automated Test Execution Records

The following test suites were executed directly on the local workstation using Maven wrapper (`backend/mvnw.cmd`) under Java 21:

### 1.1 Core Accounting & Currency Precision Suite (C01 Domain)
* **Execution Command:**
  ```powershell
  cd C:\Users\PC\Projects\sme-financial-webapp\backend
  .\mvnw.cmd test -Dtest=CurrencyRegistryTest
  ```
* **Execution Outcome:** **BUILD SUCCESS** (Time elapsed: 3.672 s)
* **Tests Executed:** **39 tests**, 0 failures, 0 errors, 0 skipped.
* **Verified Coverage:**
  - JPY scale 0 (exact integer math, rejection of fractional yen).
  - BHD, KWD, OMR scale 3 (3 decimal places precision).
  - Standard currency scale 2 (PKR, USD, EUR, GBP, AED, SAR).
  - Currency capacity boundary: $\text{magnitude} < 10^{12}$ (`MAX_CAPACITY = 1000000000000`).
  - Strict rejection of arithmetic overflows via `RoundingMode.UNNECESSARY`.

### 1.2 Core Ledger Services & Transaction Posting Suite (C01 & C05 Domains)
* **Execution Command:**
  ```powershell
  cd C:\Users\PC\Projects\sme-financial-webapp\backend
  .\mvnw.cmd test "-Dtest=AccountCreateServiceTest,AccountReadServiceTest,TransactionPostServiceTest,TransactionReverseServiceTest,TransferServiceTest,RecordDraftServiceTest"
  ```
* **Execution Outcome:** **BUILD SUCCESS** (Time elapsed: 6.105 s)
* **Tests Executed:** **133 tests**, 0 failures, 0 errors, 0 skipped.
* **Class-by-Class Breakdown:**
  - `AccountCreateServiceTest`: **13 tests**, 0 failures, 0 errors. (Opening balances, currency constraints, idempotency).
  - `AccountReadServiceTest`: **13 tests**, 0 failures, 0 errors. (Active vs archived accounts, balance calculations).
  - `TransactionPostServiceTest`: **13 tests**, 0 failures, 0 errors. (Direct sale receipts, operating expenses, balance equations).
  - `TransactionReverseServiceTest`: **9 tests**, 0 failures, 0 errors. (Compensating movements, chronological sequence guards, negative cash balance prevention).
  - `TransferServiceTest`: **11 tests**, 0 failures, 0 errors. (Internal transfers, source decrease & destination increase, audit logging).
  - `RecordDraftServiceTest`: **74 tests**, 0 failures, 0 errors. (Draft creation, optimistic locking, atomic posting, discard lifecycle).

### 1.3 Operational Financial Domains Suite (C03, C06, C07, C08, C09, C10, C11)
* **Execution Command:**
  ```powershell
  cd C:\Users\PC\Projects\sme-financial-webapp\backend
  .\mvnw.cmd test "-Dtest=FinancialObligationServiceTest,ObligationAgingCalculatorTest,BankReconciliationServiceTest,RecurringRuleServiceTest,CommissionCalculationServiceTest,CommissionEarningServiceTest,CommissionRuleServiceTest,CommissionSettlementServiceTest,FxCalculationServiceTest,FxSettlementServiceTest,FxTransferServiceTest,ProjectFundingGapCalculatorTest,FinancialQueryFacadeServiceTest"
  ```
* **Execution Outcome:** **BUILD SUCCESS** (Time elapsed: 6.754 s)
* **Tests Executed:** **143 tests**, 0 failures, 0 errors, 0 skipped.
* **Class-by-Class Breakdown:**
  - `FinancialObligationServiceTest`: **21 tests**, 0 failures. (Receivables/payables creation, partial allocations, over-allocation prevention).
  - `ObligationAgingCalculatorTest`: **19 tests**, 0 failures. (Aging brackets: 0–30, 31–60, 61–90, 90+ days).
  - `BankReconciliationServiceTest`: **15 tests**, 0 failures. (Statement ingestion, line-item matching, tolerance matching).
  - `RecurringRuleServiceTest`: **10 tests**, 0 failures. (Recurrence frequency, next due date calculation, draft generation).
  - `CommissionCalculationServiceTest`: **5 tests**, 0 failures. (Percentage and tiered commission calculations).
  - `CommissionEarningServiceTest`: **5 tests**, 0 failures. (Commission accrual upon invoice settlement).
  - `CommissionRuleServiceTest`: **6 tests**, 0 failures. (Salesperson assignment and rule precedence).
  - `CommissionSettlementServiceTest`: **8 tests**, 0 failures. (Payout generation and ledger obligation linking).
  - `FxCalculationServiceTest`: **8 tests**, 0 failures. (Realized vs unrealized FX gain/loss, scale 6 exchange rates).
  - `FxSettlementServiceTest`: **7 tests**, 0 failures. (Cross-currency settlements and variance tracking).
  - `FxTransferServiceTest`: **6 tests**, 0 failures. (Multi-currency internal transfers).
  - `ProjectFundingGapCalculatorTest`: **24 tests**, 0 failures. (Budget vs actual outflows + commitments, deficit scenarios).
  - `FinancialQueryFacadeServiceTest`: **9 tests**, 0 failures. (Read-only capabilities, tenant scoping, structured response envelopes).

---

## 2. Summary of Full Automated Test Suite Execution (Candidate v3 Frozen Tree)

The table below reconciles the complete test execution across all candidate stages on the frozen tree (`19547bf3dda9744d51e74c87a908e2e42577299c`):

| Test Suite / Gate | Test Scope | Passed | Failed | Skipped | Status | Execution Environment |
|---|---|:---:|:---:|:---:|:---:|---|
| **Backend Unit Tests** | 179 test classes | **1,846** | 0 | 1 | **PASS** | Local Windows + Hosted Ubuntu CI |
| **PostgreSQL Integration Tests** | 91 IT classes (V1–V52 Flyway) | **485** | 0 | 0 | **PASS** | Disposable PostgreSQL via `postgres_gate.py` |
| **Frontend Unit & Contract** | Next.js / Vitest | **482** | 0 | 0 | **PASS** | Node 22, pnpm frozen lockfile |
| **Real Full Product Browser Gate** | Cross-stack Playwright | **26** | 0 | 0 | **PASS** | Spring Boot (8093) + Next.js (3593) + PostgreSQL |
| **Historical Browser Regression** | 5 drivers (Landing, Primary, Phase-Two, Extra, Risk) | **86** | 0 | 0 | **PASS** | Headless Chromium |
| **Provider Disclosure Suite (DEF-08)** | Simulated WhatsApp + Copilot Fallback | **10** | 0 | 0 | **PASS** | Mock provider runtime |
| **OCR Python Suite** | FastAPI / Pytest | **248** | 0 | 0 | **PASS** | Python 3.11 venv |
| **Demo Container Contract Suite** | `DemoContainerOwnershipTest` + Tooling Contract | **21** | 0 | 0 | **PASS** | PowerShell fixture-backed tests |

---

## 3. Explicit Distinction: Tested vs. Untested Behavior

To maintain strict financial and engineering integrity, Developer A explicitly delineates between verified automated tests and unexecuted physical infrastructure gates:

### 3.1 Verified Automated Behavior
- **Double-Entry Equality:** Verified across all account movements; debits equal credits for every transaction.
- **Scale Precision:** Verified across all 10 supported currencies, specifically JPY (0), BHD (3), and PKR (2).
- **V52 Projection Determinism:** Verified in `TransactionDrivenMonthlyPostgreSqlIT`: daily postings flow to monthly records without double counting; cash receipts do not double-count as accrual revenue; reversals remove original expenses.
- **Mandatory 409 Conflict Protection:** Verified in both unit and integration tests: attempting manual monthly record writes when projections are active returns `409 monthly_figures_derived`.
- **CORS Configuration:** Verified in `CorsOriginPolicyTest`: wildcards rejected in production; explicit origins required.
- **Session Auth Fix:** Byte-identical verification of `SessionProvider.tsx` prevents browser login aborts.

### 3.2 Untested / Pending Physical Infrastructure Behavior
The following items are **NOT VERIFIED** by local automated testing and remain pending until executed on dedicated physical infrastructure:

1. **Physical Docker Rehearsal (DEF-18-B):**
   - The execution sequence (`init-demo.ps1` $\rightarrow$ `start-demo.ps1` $\rightarrow$ `verify-public-demo.mjs` $\rightarrow$ `stop-demo.ps1`) has been tested only against PowerShell Docker function fixtures (`DockerFunctionFixture.ps1`).
   - A real live execution on a physical Windows runner with Docker Desktop (Linux containers) has **not been executed** because the current development host lacks Docker Desktop.
2. **PostgreSQL 17 Production Privileges & TLS/HBA:**
   - Production DDL/DML isolation (`finsight_migrator` vs `finsight_app`) and TLS certificate enforcement (`DatabaseRolePrivilegesPostgreSqlIT`, `PostgreSqlTlsConnectionIT`) have only run on simulated containers, not on a physical enterprise PostgreSQL 17 cluster.
3. **Live External Provider Integrations:**
   - Meta WhatsApp Cloud API (real phone number, templates, delivery webhooks).
   - OpenAI API live billing and completion token usage.
   - Google Cloud Vision API credentials.
   - Production SMTP mail server.
4. **Physical Mobile Device Camera Capture (DEF-15):**
   - File input `capture` behavior has not been tested on physical Android or iOS hardware.
