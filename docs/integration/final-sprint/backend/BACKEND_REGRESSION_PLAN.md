# FinSight Phase 2 — Comprehensive Combined Backend Regression Plan

**Document ID:** `FINAL-S3B-BACKEND-REGRESSION-PLAN`
**Date:** Friday, 6 November 2026
**Author:** Antigravity (Backend Lead / Financial Systems Architect / Sole Git Coordinator)
**Baseline:** Merged Integration Baseline `2fa20c3b6ca59d04504290542fdfef21ef7e1da2` (Flyway V1–V37 unified)
**Branch:** `dev/suleman`
**Target Execution Window:** Post-Codex Live Browser Acceptance (Deferred execution to protect active runtime port 5433/8080)

---

## 1. Executive Summary & Objective

This document defines the authoritative, structured execution plan for the combined backend regression suite across the entire unified codebase:
1. **MVP Baseline (Flyway V1–V17):** Authentication, MFA, Session management, RBAC, Monthly Records, `health-score-v1`, Hanafi Zakat, Scheduled WhatsApp, Immutable OCR Document Provenance.
2. **Developer A Scope (Flyway V18–V29):** Financial Accounts, Financial Transactions, Internal Transfers, Financial Obligations, Counterparty Advances, Daily Cash Forecast, Financial Risk Assessment, Financial Query Facade (`C11`).
3. **Developer B Scope (Flyway V30–V37):** Business Configuration & Module Matrix (F02/V30), Dashboard Preferences (F13/V31), Monthly Record Integrity (F12B/V32), Project Planning (F05/V33), Commercial Documents (F04/V34), Sample Cost Planning (F09/V35), Estimates & Quotations (F06/V36), Inventory Catalog (F08/V37), AI Question Router & Copilot.
4. **Cross-Developer Integration (S2A / S3A):** C11 Authoritative Financial Evidence Adapter, Live Runtime Handoff Seeder, and zero-stale cache synchronization.

### Strict Execution Safety Rules
* **No Runtime Interference:** The regression test suites must **NOT** be executed while Codex is running live browser acceptance against PostgreSQL `127.0.0.1:5433` and Spring Boot `127.0.0.1:8080`.
* **Isolated Disposable PostgreSQL:** When integration tests run, they utilize `DisposablePostgres` (ephemeral port `0`) or disposable container fixtures to guarantee zero shared state corruption.
* **Sole Git Coordinator:** No Git operations will be performed until both Codex's live tests and this regression suite pass cleanly.

---

## 2. Test Inventory Breakdown

The FinSight backend test suite comprises **224 Java source files** organized into two primary execution tiers:
* **Unit & WebMvc Mock Suites:** 148 test classes (`*Test*.java`), managed by `maven-surefire-plugin`.
* **PostgreSQL Integration Suites:** 58 test classes (`*IT.java`), managed by `maven-failsafe-plugin` under Maven profile `postgres-it`.
* **Shared Fixture & Support Classes:** 18 helper and seeder classes (`DisposablePostgres`, `LiveRuntimeHandoffSeeder`, `TestAuditSender`, `AiTestFixtures`, `ZakatTestFixtures`, etc.).

```
backend/src/test/java/com/app/sme_health_backend/
├── accounting/              (13 Unit + 3 IT: Accounts, Transactions, Transfers)
│   └── draft/               (3 Unit + 2 IT: Draft imports, Statement parsers)
├── ai/                      (10 Unit + 1 IT: Router, Evidence adapter, Grounding)
├── audit/                   (2 IT: Security audit immutability & cleanup)
├── cashflow/                (4 Unit + 1 IT: Cash flow trend projections)
├── commercial/              (5 Unit + 2 IT: Non-posting commercial documents)
├── configuration/           (3 Unit + 2 IT: Module matrix & business config)
├── counterparty/            (3 Unit + 1 IT: Counterparties & advances)
├── crypto/                  (6 Unit + 1 IT: AES-256-GCM key rotation & envelopes)
├── dashboard/workspace/     (4 Unit + 2 IT: Workspace preferences & widgets)
├── database/                (6 IT: Role privileges, schema integrity, TLS)
├── documents/               (15 Unit + 3 IT: Provenance, OCR, sanitization)
├── estimates/               (8 Unit + 3 IT: Quotations & cost previews)
├── financialquery/          (2 Unit + 1 IT: C11 authoritative query facade)
├── forecast/                (3 Unit + 1 IT: Daily cash forecast engine)
├── identity/                (7 Unit + 3 IT: RBAC, Tenant isolation, Multi-business)
├── inventory/               (4 Unit + 2 IT: Inventory catalog & valuation)
├── obligation/              (7 Unit + 2 IT: Financial obligations & aging)
├── projects/                (5 Unit + 2 IT: Project planning & milestones)
├── reconciliation/          (4 Unit + 1 IT: Bank statement reconciliation)
├── records/                 (3 Unit + 2 IT: Monthly records & projections)
├── recurring/               (4 Unit + 1 IT: Recurring transaction rules)
├── reports/                 (4 Unit + 1 IT: Trial balance, P&L, General ledger)
├── risk/                    (3 Unit + 1 IT: Financial risk intelligence V1)
├── samples/                 (7 Unit + 2 IT: Sample cost planning & locking)
├── scoring/                 (8 Unit: Canonical health-score-v1 weights)
├── search/                  (3 Unit: Tenant-scoped search & ranking)
├── security/                (15 Unit + 10 IT: Session, MFA, Argon2, Ingress)
├── whatsapp/                (8 Unit + 1 IT: Notifications, Scheduler, Recovery)
└── zakat/                   (4 Unit: Hanafi business Zakat calculation)
```

---

## 3. Four-Tier Execution Architecture

### Tier 1: Unit & Fast In-Memory Suites (MockMvc / Pure Java)
* **Goal:** Verify domain math, entity mappings, input validation, status state machines, and REST controller contracts without database overhead.
* **Command:**
  ```powershell
  cd backend
  .\mvnw.cmd test -Dtest="!*IT"
  ```
* **Key Assertions:**
  1. `health-score-v1` weights: 30% Cash Flow, 25% Profitability, 20% Repayment, 15% Trend, 10% Compliance.
  2. Hanafi Zakat calculation formula and nisab thresholds.
  3. Daily cash forecast recurring recurrence calculations.
  4. Obligation settlement status transitions (`OPEN`, `PARTIALLY_PAID`, `SETTLED`).
  5. Sample cost planning formula: `direct_costs + labor_costs + overhead_allocation`.
  6. AI Question Router intent classification and keyword/pattern categorization.

### Tier 2: Real PostgreSQL Integration Suites (`postgres-it`)
* **Goal:** Verify Flyway migrations V1–V37, database constraints, PostgreSQL triggers, role permissions (`finsight_app` vs `finsight_migrator`), and multi-tenant isolation.
* **Target Isolation:** Uses `DisposablePostgres` (Zonky embedded PostgreSQL on dynamic port `setPort(0)`). Never accesses port `5433`.
* **Command:**
  ```powershell
  cd backend
  .\mvnw.cmd verify -P postgres-it -Dtest="*IT"
  ```
* **Key Invariants Verified:**
  1. **Strict Tenant Isolation:** Cross-tenant access to accounts, transactions, obligations, commercial documents, or projects throws `ResourceNotFoundException` (HTTP 404).
  2. **Inventory Triggers:** Trigger `enforce_inventory_item_lifecycle` prevents unauthorized direct DELETE or TRUNCATE on catalog items.
  3. **Obligation Balance Invariant:** Outstanding balance strictly equals `original_amount - SUM(counterparty_advances) - SUM(payment_allocations)`.
  4. **Account Balance Invariant:** Account balance strictly equals `opening_balance + SUM(receipts) - SUM(expenses) + incoming_transfers - outgoing_transfers`.
  5. **C11 AI Evidence Freshness:** Querying `/api/ai/explain` immediately after a transaction mutation returns freshly calculated balance evidence without stale cache.

### Tier 3: Known Environment Exceptions & Conditional Tests
Two tests in `backend/src/test/java/com/app/sme_health_backend/database/` require specialized container network or TLS fixtures not present in local developer runs:
1. `DatabaseRolePrivilegesPostgreSqlIT`: Requires production-grade PostgreSQL role switching on managed socket.
2. `PostgreSqlTlsConnectionIT`: Requires TLS certificate keystore (`edge.crt` / `edge.key`).
* **Handling:** These two tests are explicitly documented and skipped during local disposable execution (`-Dtest="!DatabaseRolePrivilegesPostgreSqlIT,!PostgreSqlTlsConnectionIT"`), as defined in Section 5 of `AGENTS.md`.

### Tier 4: Local Runtime Non-Mutating Smoke Suite
* **Goal:** Verify the live running instance (`127.0.0.1:8080`) remains healthy and consistent after all suite executions.
* **Command:**
  ```powershell
  node scripts/smoke_test_runtime.mjs
  ```
* **Pass Criteria:** 10/10 suites green (CSRF, Auth, Tenant switch, Account math, Obligation math, Daily forecast, Risk assessment, C11 AI evidence, Cache freshness, Tenant 404 isolation).

---

## 4. Financial & Architectural Invariants Matrix

| Invariant | Scope | Mathematical / Logical Rule | Test Verification Class |
| :--- | :--- | :--- | :--- |
| **Double-Entry Balance** | Dev A | $\sum \Delta \text{Movements}_{\text{transfer}} = 0$ (Debit = Credit) | `FinancialTransactionPostgreSqlIT` |
| **Account Cash Position** | Dev A | $\text{Balance} = \text{Opening} + \text{Inflow} - \text{Outflow}$ | `FinancialAccountPostgreSqlIT` |
| **Obligation Settlement** | Dev A | $\text{Outstanding} = \text{Original} - \text{Advance} - \text{Payments} \ge 0$ | `FinancialObligationPostgreSqlIT` |
| **Health Score Weights** | MVP | $30\% \text{Cash} + 25\% \text{Profit} + 20\% \text{Repay} + 15\% \text{Trend} + 10\% \text{Compliance} = 100\%$ | `ScoringMethodologyAuditTests` |
| **Zakat Valuation** | MVP | $(\text{Cash} + \text{Receivables} + \text{Inventory}) - (\text{Immediate Liabilities}) \ge \text{Nisab}$ | `ZakatCalculationServiceTests` |
| **Non-Posting Commercial** | Dev B | Invoices/Bills status changes do **NOT** modify ledger accounts directly | `CommercialDocumentPostgreSqlIT` |
| **Sample Cost Budgeting** | Dev B | Sample cost = Raw Materials + Labor + Overhead; locks upon approval | `SamplePlanningPostgreSqlIT` |
| **AI Read-Only Grounding** | S2A / C11 | AI explanations derive strictly from `FinancialQueryFacade`; no LLM writes | `AiEvidencePostgreSqlIT` |
| **Append-Only Audit Log** | MVP | Security audit records reject UPDATE, DELETE, and TRUNCATE via trigger | `SecurityAuditImmutabilityIT` |
| **Tenant Isolation** | Unified | Queries without active tenant header / session return HTTP 404 | `TenantIsolationPostgreSqlIT` |

---

## 5. Credential & Cryptographic Secret Hygiene Plan

To protect repository integrity and ensure zero credential leaks before shared deployment or Git commit:
1. **Fixture Credentials Isolation:**
   - Test password `Synthetic-Owner-2026!` and database password `TestAppPassword2026!` reside strictly in memory or untracked `tmp/finsight-live-fixture.json`.
   - `tmp/` is verified untracked and excluded from Git staging.
2. **Cryptographic Key Segregation:**
   - Symmetric AES-256-GCM key `MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=` is supplied exclusively via environment variable `FINSIGHT_CRYPTO_KEY_K1`.
   - Never committed to tracked source files or application property baselines.
3. **Production Deployment Secret Rotation:**
   - Prior to staging/production deployment, all fixture keys must be replaced with secrets mounted via ConfigTree/Kubernetes secrets (`crypto_key_k1`, `crypto_key_k2`).
   - All runtime users will be provisioned with randomized cryptographically secure passwords.

---

## 6. Execution Runbook (Post-Codex Live Acceptance)

Once Codex signals that frontend browser acceptance testing is complete:

```powershell
# Step 1: Verify live runtime log for any errors recorded during Codex testing
# (Inspect task-3848 log output)

# Step 2: Execute pure unit tests
cd C:\Users\PC\Projects\sme-financial-webapp\backend
.\mvnw.cmd test -Dtest="!*IT"

# Step 3: Execute integration test suite with isolated PostgreSQL
.\mvnw.cmd verify -P postgres-it -Dtest="*IT,!DatabaseRolePrivilegesPostgreSqlIT,!PostgreSqlTlsConnectionIT"

# Step 4: Re-verify live shared runtime with smoke test
cd C:\Users\PC\Projects\sme-financial-webapp
node scripts/smoke_test_runtime.mjs

# Step 5: Verify working tree clean of accidental build outputs or secrets
git status
```

---

## 7. Approval & Handoff Criteria

| Gate | Requirement | Status |
| :--- | :--- | :--- |
| **Gate 1** | Codex live browser acceptance finished and reported | **WAITING FOR CODEX** |
| **Gate 2** | Backend unit test execution: 148 test suites passing | **READY TO RUN** |
| **Gate 3** | Backend integration test execution: 56 disposable IT suites passing | **READY TO RUN** |
| **Gate 4** | 10/10 non-mutating smoke test suites passing against live runtime | **VERIFIED (PASS)** |
| **Gate 5** | Credential and secret cleanliness verified (zero tracked leaks) | **VERIFIED (PASS)** |
| **Gate 6** | Joint approval for consolidated Git commit | **PENDING GATES 1-5** |
