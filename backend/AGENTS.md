# Antigravity Backend Working Instructions (Phase 2)

**Version:** `1.1`  
**Date:** 14 October 2026  
**Owner:** Antigravity (Developer A Backend & Checkpoint Git Coordinator)  
**Location:** `backend/`  
**Shared Branch:** `dev/suleman`  

---

## 1. Scope & Ownership

*   Antigravity owns Developer A backend architecture and implementation:
    *   **F01:** Accounts, transactions, ledger movements, monthly aggregation, cutover.
    *   **F03:** Counterparties, obligations, receivables/payables, payment allocations, aging.
    *   **F07:** Project cash plan, funding gap calculator, scenario modeling.
    *   **F10:** Salesperson role permissions, row-level authorization, commission calculation.
    *   **F11:** Multi-currency, locked historical exchange rates, realized FX, gateway fees.
    *   **F12A:** Bank statement staging, reconciliation matching, recurring rule engine.
    *   **Intelligence:** Deterministic risk rules, financing schedules, read-only `FinancialQueryFacade`.
*   Antigravity acts as the sole Git coordinator at explicit paused checkpoints.

---

## 2. Package Architecture & Code Conventions

1.  **Package-by-Feature Layout:**
    *   Organize code into clean domain feature packages under `com.app.sme_health_backend`:
        *   `accounting/` (F01: accounts, transactions, movements, cutover, aggregation)
        *   `obligation/` (F03: counterparties, obligations, payments, aging)
        *   `liquidity/` (F07: cash plans, funding gap calculator, scenarios)
        *   `commission/` (F10: salesperson scope, commission rules, recognition)
        *   `currency/` (F11: currency configs, exchange rates, settlement)
        *   `operations/` (F12A: statement imports, reconciliation, recurring)
        *   `facade/` (Intelligence: read-only FinancialQueryFacade)
    *   Keep entities, repositories, services, DTOs, and controllers grouped within their respective domain package.
2.  **Server-Side Tenant Isolation:**
    *   Always resolve the tenant identity on the server using `ActiveBusinessContext.getRequiredContext(request)`.
    *   Never allow client-supplied `businessId` parameters in request bodies or query parameters to override the authenticated active business context.
    *   Enforce object-level tenant boundaries on every repository query (e.g., `findByBusinessIdAndId`). Querying an entity belonging to another business must return `404 Not Found` (never `403`), preventing resource enumeration.
3.  **Role-Based Access Control (RBAC):**
    *   Check explicit permissions via `BusinessAuthorizationService.requirePermission(request, BusinessPermission.X)`.
    *   Respect existing roles: `OWNER`, `ACCOUNTANT`, `MANAGER`, `VIEWER`, and future `SALESPERSON`.
4.  **Monetary Math & Currency Scale Discipline:**
    *   Never use `double` or `float` for monetary amounts or rates.
    *   Use `BigDecimal` exclusively.
    *   **Currency-Specific Scale:** Do **not** mandate scale 2 for all currencies. While base PKR and standard currencies (USD, EUR, AED, SAR) use scale 2, non-standard currencies define specific scale based on currency metadata (e.g. `JPY` scale 0, `BHD`/`KWD`/`OMR` scale 3). Standard rounding mode is `RoundingMode.HALF_UP`.
    *   **FX Rate Precision:** Exchange rates and percentage factors must define explicit precision and scale (e.g., scale 6 for FX rates: `"280.500000"`), distinctly separated from currency amount scale.
    *   Legacy MVP DTOs continue using their historical format (`NUMERIC(14,2)`) to preserve backward compatibility.

---

## 3. Database & Migration Discipline

1.  **Flyway Migrations (V1–V17 are Immutable):**
    *   Existing migrations `V1` through `V17` are committed and must never be altered, renamed, or reordered.
    *   New migrations begin at `V18` or higher and **must be globally reserved in agreement with Fatima**. Never assume a migration number is available merely because it is absent locally.
2.  **Two-Plane Database Security Architecture:**
    *   **Plane 2 (Migrator):** `finsight_migrator` has DDL privileges to execute Flyway migrations (`application-migrate.properties`).
    *   **Plane 3 (Runtime):** `finsight_app` has restricted DML privileges only (SELECT, INSERT, UPDATE, DELETE). `V15` strictly revokes all access to `flyway_schema_history` from `finsight_app`.
3.  **Triggers & Immutability:**
    *   Preserve database immutability triggers for `security_audit_events`, `uploaded_documents` (original extraction), and `document_corrections` (append-only history).
    *   Financial transactions and account movements must also follow append-only immutability.

---

## 4. Financial Integrity, Concurrency & Idempotency

1.  **Atomic Posting & Row Locking:**
    *   All financial posting mutations must execute within `@Transactional(rollbackFor = Exception.class)`.
    *   Acquire pessimistic write locks (`FOR UPDATE`) on accounts during posting to prevent lost updates or negative balance race conditions.
2.  **Durable Duplicate Prevention:**
    *   Financial posting endpoints must enforce business-scoped `Idempotency-Key` tracking backed by durable persistence (`financial_idempotency_records`).
    *   Replay with identical payload returns the cached result; replay with conflicting payload returns HTTP `409 Conflict` (`ERROR_IDEMPOTENCY_CONFLICT`).
3.  **Shared Files & Coordination:**
    *   Changes to shared domain services (e.g., `MonthlyRecordService.java`, `BusinessPermission.java`, `application.properties`) require prior coordination with Fatima where her domain or interface is affected.
4.  **Audit Trail:**
    *   Every financial state transition, posting, reversal, and balance adjustment must write an audit record via `SecurityAuditService`.

---

## 5. Verification Tiers & Testing Commands

*   **Fast Unit Tests:**
    ```powershell
    .\mvnw.cmd test
    ```
*   **PostgreSQL Integration Tests:**
    ```powershell
    .\mvnw.cmd -B -Ppostgres-it "-Dit.test=*IT,!DatabaseRolePrivilegesPostgreSqlIT,!PostgreSqlTlsConnectionIT" verify
    ```
    *Note on Exclusions:* `DatabaseRolePrivilegesPostgreSqlIT` and `PostgreSqlTlsConnectionIT` are excluded **only** when dedicated production database containers/fixtures are absent. Any test exclusion must be explicitly named in verification evidence.
*   **Migration Fresh & Upgrade Tests:**
    ```powershell
    .\mvnw.cmd -B -Ppostgres-it "-Dit.test=MvpLegacyMigrationPostgreSqlIT,OneShotMigrationExecutionTest" verify
    ```
*   **Scoring & Financial Numerical Regression:**
    ```powershell
    .\mvnw.cmd -B "-Dtest=ScoringMethodologyRegressionTests,CashFlowStabilityCalculatorTests,ProfitabilityEfficiencyCalculatorTests,TrendProjectionCalculatorTests" test
    ```
