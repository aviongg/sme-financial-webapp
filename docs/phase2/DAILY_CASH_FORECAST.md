# Daily Cash Forecasting & Liquidity Simulation (A-W6-ADVANCED-CASH-FORECAST-V1)

**Version:** `1.1`  
**Date:** 29 October 2026  
**Status:** `INTEGRATION_VERIFIED` (Developer A Full-Stack Scope)  
**Flyway Migration Reservation:** `0 new migrations` (Flyway head remains `V29__create_recurring_transactions.sql`; `V30` reserved for Developer B / Fatima F05)  
**Contract Version:** `1.0.0`  

---

## 1. Feature Overview & Architecture

`A-W6-ADVANCED-CASH-FORECAST-V1` provides small and medium-sized enterprises with deterministic daily liquidity visibility across standard rolling forward horizons:
*   **7 Days:** Immediate liquidity and operational payroll / supplier settlement coverage.
*   **30 Days (Default):** Standard monthly operational liquidity cycle.
*   **60 Days:** Medium-term working capital exposure.
*   **90 Days:** Quarterly planning and cash runway assessment.

The forecasting engine (`DailyCashForecastService.java`) runs on a strictly read-only execution policy (`@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)`), deriving every day's projected opening, expected inflows, expected outflows, net movement, and projected closing cash from verified financial actuals, confirmed obligation schedules, and active recurring financial rules.

### Key Architectural Invariants & Boundary Disclaimers
1.  **Deterministic & Scenario-Based (Not Machine Learning):** The forecast is strictly arithmetic and rule-derived. There is no stochastic drift, regression guessing, or LLM-generated numbers. All numbers are calculated strictly using canonical `BigDecimal` arithmetic.
2.  **Forecasted Cash is Not Posted Cash:** Projected balances represent forward-looking expectations based on current payment agreements and active recurring rules. They do not alter or substitute for posted ledger balances.
3.  **Dual-Layer Security & RBAC:**
    *   Layer 1: Authenticated HTTP session cookie (`FINSIGHT_SESSION`) with CSRF protection (`XSRF-TOKEN`).
    *   Layer 2: Server-side `ActiveBusinessContext` resolution with RBAC validation (`FINANCIAL_TRANSACTION_READ` / `FINANCIAL_DATA_READ`). Cross-tenant access is strictly denied.
4.  **Non-Mutating Execution:** Absolutely zero database mutations or side effects. Verified via bit-for-bit SHA-256 table snapshots across 12 database tables.
5.  **Zero Flyway Migrations:** Runs on top of Flyway `V29` schema. `V30` is preserved for Developer B / Fatima (`F05 Project Planning & Budgeting (Phase A)`).
6.  **Scope Boundaries:**
    *   Persistent W6 financial risk alerts remain a separate feature.
    *   The financing simulator remains a separate feature.
    *   C02 Phase B live scoring cutover remains pending.
    *   Future integration feeds for Fatima's F05 (project budgets) and F08 (COGS / inventory) are documented as omitted categories in completeness metadata.

---

## 2. Mathematical Pipeline & Data Aggregation

Given an evaluation date $T_0$ (`asOfDate`, defaults to `LocalDate.now(ZoneId.of("Asia/Karachi"))`) and a horizon of $H \in \{7, 30, 60, 90\}$ days:

### 2.1 Starting Cash Balance
Starting cash at $T_0$ is authoritatively derived from all active financial accounts (`FinancialAccount`) in the selected currency:
$$\text{StartingCash}(T_0) = \sum_{a \in \text{Accounts}_{\text{curr}}} \left( \text{OpeningBalance}(a) + \sum_{m \in \text{Movements}(a), \, t_m \le T_0} \text{SignedMovement}(m) \right)$$
Accounts with opening dates after $T_0$ are excluded from starting cash on $T_0$ and incorporated on their respective activation dates.

### 2.2 Expected Inflows
Expected inflows on day $T$ ($T_0 \le T \le T_0 + H$) aggregate:
1.  **Customer Receivables:** Canonical obligations with `direction = RECEIVABLE` and `outstandingAmount > 0`:
    *   If installment payment plan exists, planned installments falling on day $T$.
    *   If no installment plan exists, remaining balance due on obligation `dueDate` if $T = \text{dueDate}$.
2.  **Recurring Receipts:** Active recurring rules with `eventType = DIRECT_SALE_RECEIPT` projected according to frequency schedule (`DAILY`, `WEEKLY`, `BIWEEKLY`, `MONTHLY`, `ANNUAL`), deduplicated against existing posted/draft occurrences via `seenRuleDates`.

### 2.3 Expected Outflows
Expected outflows on day $T$ aggregate:
1.  **Supplier Payables:** Canonical obligations with `direction = PAYABLE` and `outstandingAmount > 0`:
    *   If installment payment plan exists, planned installments falling on day $T$.
    *   If no installment plan exists, remaining balance due on obligation `dueDate` if $T = \text{dueDate}$.
2.  **Recurring Expenses:** Active recurring rules with `eventType = OPERATING_EXPENSE_PAYMENT` projected according to frequency schedule, deduplicated against existing posted/draft occurrences.

### 2.4 Event Precedence & Deduplication Rules
*   **Active Rules Only:** Rules with `status = ACTIVE` and matching currency are processed. `PAUSED` and `ARCHIVED` rules are excluded.
*   **Occurrence Precedence:** Persisted occurrences in `[asOfDate, horizonEndDate]` take precedence over rule templates:
    *   `SCHEDULED` and `DRAFT_READY` occurrences are projected as expected cash movements on their scheduled date and added to `seenRuleDates`.
    *   `SKIPPED`, `POSTED`, and `REVERSED` occurrences are excluded from future cash flows, but are added to `seenRuleDates` to ensure template expansion never re-creates them.
*   **Obligation Installment Precedence:** Installment schedules absorb settled amounts chronologically. Residual unscheduled exposure is placed on `dueDate`. Missing due dates are surfaced as completeness warnings without fabricated dates.
*   **Cross-Source Overlaps:** Unlinked obligations and recurring rules with similar amounts/dates are never silently merged; both are preserved transparently.

### 2.5 Daily Cash Progression
For each day $i \in [0, H]$:
$$\text{OpeningCash}(i) = \begin{cases} \text{StartingCash}(T_0) & \text{if } i = 0 \\ \text{ClosingCash}(i-1) & \text{if } i > 0 \end{cases}$$
$$\text{NetMovement}(i) = \text{TotalInflows}(i) - \text{TotalOutflows}(i)$$
$$\text{ClosingCash}(i) = \text{OpeningCash}(i) + \text{NetMovement}(i)$$

### 2.6 Deficit Detection & Liquidity Warnings
*   **Deficit Day:** Any day $i$ where $\text{ClosingCash}(i) < 0.00$.
*   **Maximum Expected Shortfall:** $\max(0.00, -\min_{i} \text{ClosingCash}(i))$.
*   **First Deficit Date:** The earliest calendar date where $\text{ClosingCash}(i) < 0.00$.
*   **Payable Concentration Alert:** Triggered if a single day accounts for $> 40\%$ of total horizon outflows.
*   **Overdue Receivables Alert:** Emitted when past-due receivables exist, detailing total exposure excluded from the baseline daily timeline.

---

## 3. Interactive Scenario Simulation

The simulation engine (`POST /api/finance/forecast/scenario-preview`) allows users to stress-test their liquidity position under hypothetical adverse conditions without modifying any financial actuals:

| Scenario Parameter | Valid Bounds | Default | Mathematical Impact |
| :--- | :--- | :--- | :--- |
| `collectionDelayDays` | $0 \le d \le 60$ | $0$ | Shifts customer receivable collection dates forward by $d$ days. Receipts shifted beyond $T_0 + H$ drop out of the horizon. |
| `collectionPercentage` | $0.00 \le p \le 100.00$ | $100.00$ | Applies a realization haircut: $\text{Inflow}' = \text{Inflow} \times (p / 100)$. |
| `paymentDelayDays` | $0 \le d \le 60$ | $0$ | Shifts supplier payable settlement dates forward by $d$ days. |
| `includeOverdueReceivables`| `true` / `false` | `false` | Distributes past-due receivables linearly across `overdueSpreadDays` starting on $T_0$. |
| `overdueSpreadDays` | $1 \le s \le 90$ | $30$ | Spread window for overdue receivables distribution. |

The API returns both baseline and scenario daily projections alongside a structured variance comparison:
*   `endingCashVariance`: $\text{ScenarioEndingCash} - \text{BaselineEndingCash}$
*   `minimumCashVariance`: $\text{ScenarioMinCash} - \text{BaselineMinCash}$
*   `firstDeficitDateChange`: Describes timing shifts in cash shortfall.

---

## 4. Query Performance & Memory Limits

1.  **Scoped Date-Range Queries:**
    *   Occurrences are queried strictly within the active horizon: `findByBusinessIdAndScheduledDateBetween(businessId, asOfDate, horizonEndDate)`.
    *   Historical occurrences (past months/years) are never loaded into memory.
2.  **Movement Aggregation:**
    *   Account movements up to `asOfDate` are aggregated via indexed database SQL: `findMovementSumsByBusinessIdAndCutoffDate`.
3.  **Bounded Recurrence Preview:**
    *   Template schedule preview uses `RecurrenceCalculator.previewSchedule(..., maxOccurrences = 100)` preventing runaway memory allocation.
4.  **Zero N+1 Queries:**
    *   Total database queries per forecast calculation: exactly 9 indexed queries.
    *   All entity joins and groupings occur in-memory using hash-map and set lookups.

---

## 5. API Endpoints

### 5.1 Daily Cash Forecast
*   **Path:** `GET /api/finance/forecast/daily`
*   **Parameters:**
    *   `currency` (optional, default `"PKR"`)
    *   `horizonDays` (optional, default `30`, allowed: `7`, `30`, `60`, `90`)
    *   `asOfDate` (optional, default current date `YYYY-MM-DD`)
*   **Response:** `DailyCashForecastResponse` (200 OK)

### 5.2 Scenario Preview
*   **Path:** `POST /api/finance/forecast/scenario-preview`
*   **Content-Type:** `application/json`
*   **Body:** `ForecastScenarioRequest`
*   **Response:** `ForecastScenarioResponse` (200 OK)

---

## 6. Verification Evidence Summary

| Suite / Test Tier | Scope | Result | Evidence / Details |
| :--- | :--- | :--- | :--- |
| **Service Unit Tests** | `DailyCashForecastServiceTest` | **11/11 PASSED** | Numerical fixtures A–G, deficit detection, input bounds validation, cross-currency isolation, skipped/reversed occurrence exclusion. |
| **Controller WebMvc Tests** | `CashForecastControllerTest` | **5/5 PASSED** | GET daily 200, GET validation 400, POST scenario 200, POST validation 400, RBAC 403 Forbidden. |
| **PostgreSQL 14.22 IT** | `CashForecastPostgreSqlIT` | **10/10 PASSED** | Real PostgreSQL 14.22 with Flyway V29, 12-table SHA-256 bit-for-bit snapshot invariants, full Spring Security session lifecycle, cross-tenant isolation, skipped occurrence exclusion. |
| **Backend Regression** | Combined regression suite | **45/45 PASSED** | Zero regressions across forecasting, ledger projection (`MonthlyLedgerProjectionServiceTest`), and query facade (`FinancialQueryFacadeServiceTest`). |
| **Frontend Unit & Contract**| Node test runner (`cash-forecast.test.mjs`) | **230/230 PASSED** | 1:1 English/Urdu key symmetry, non-empty copy, disclaimer presence, records tab integration, API client contracts. |
| **TypeScript Compiler** | `npx tsc --noEmit` | **0 ERRORS** | Strict type compliance across all components, DTOs, and API adapters. |
| **ESLint** | `npm run lint` | **0 ERRORS** | Clean lint verification with zero errors (SessionProvider warning preserved). |
| **Production Build** | `npm run build` | **SUCCESS** | Standalone production build with dynamic route `/finance/forecast`. |
| **Edge Browser Acceptance**| `cash-forecast-browser.integration.mjs` | **10/10 PASSED** | Real Microsoft Edge headless browser journeys: KPI cards, horizon switching (7, 30, 60, 90), SVG chart rendering, scenario simulator (-40k variance), item details modal, Urdu RTL layout, mobile 390x844 responsive layout with zero overflow, zero runtime errors. |
