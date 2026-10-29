# Financial Risk Intelligence (A-W6-FINANCIAL-RISK-INTELLIGENCE-V1)

**Version:** `1.0`<br />
**Date:** 29 October 2026<br />
**Status:** `INTEGRATION_VERIFIED` (Developer A Full-Stack Scope)<br />
**Flyway Migration Reservation:** `0 new migrations` (Flyway head remains `V29__create_recurring_transactions.sql`; `V30` reserved for Developer B / Fatima F05)<br />
**Contract Version:** `1.0.0`<br />

---

## 1. Feature Overview & Architecture

`A-W6-FINANCIAL-RISK-INTELLIGENCE-V1` provides small and medium-sized enterprises (SMEs) with an explainable, deterministic financial risk detection engine. Rather than producing opaque machine learning scores or hallucinated LLM advice, FinSight Risk Intelligence evaluates authoritative ledger actuals, rolling cash projections, aging schedules, single-customer concentrations, fixed overheads, and data hygiene indicators against explicit, documented financial thresholds.

### Key Architectural Invariants & Boundary Disclaimers
1.  **Deterministic & Rule-Based (Not Machine Learning):** All risk insights are evaluated via exact `BigDecimal` arithmetic against explicit policy thresholds (`RiskThresholdPolicy.java`). No probabilistic scoring, stochastic guessing, or LLM-generated numbers are used.
2.  **Every Insight Has Traceable Evidence:** Every risk finding provides explicit financial evidence references (`RiskEvidenceReference`), linking directly to underlying transactions, obligations, recurring rules, forecast rows, or unreviewed drafts.
3.  **Strict Read-Only Non-Mutating Execution:** The engine runs under `@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)`. Zero database mutations or side effects occur. Verified via bit-for-bit SHA-256 table snapshots across 12 database tables.
4.  **Zero Flyway Migrations:** Runs on top of Flyway `V29` schema. `V30` is preserved for Developer B / Fatima (`F05 Project Planning & Budgeting (Phase A)`).
5.  **Multi-Tenant Isolation & Dual-Layer RBAC:**
    *   Layer 1: Cookie/JDBC-backed session (`FINSIGHT_SESSION`) and CSRF validation (`XSRF-TOKEN`).
    *   Layer 2: Server-side `ActiveBusinessContext` resolution with RBAC checks (`FINANCIAL_DATA_READ`, `FINANCE_READ`). Cross-tenant access is strictly denied.
6.  **Scope Boundaries:**
    *   Financing simulator remains a separate feature.
    *   C02 Phase B live scoring cutover remains pending.
    *   Future integration feeds for Fatima's F05 (project budgets) and F08 (COGS / inventory) are omitted until authoritative feeds are integrated.

---

## 2. Implemented Risk Categories & Financial Math

FinSight evaluates 8 distinct risk categories:

| Category Code | Category Enum | Severity | Evaluation Math & Policy |
| :--- | :--- | :--- | :--- |
| **R01** | `LIQUIDITY_FORECAST` | `CRITICAL` | Consumes `DailyCashForecastService`. If $\min(\text{ProjectedClosingCash}) < 0$, flags the first deficit date $T_{\text{first}}$ and maximum shortfall amount $\lvert\min(\text{Closing})\rvert$. |
| **R02** | `UPCOMING_PAYMENTS` | `CRITICAL` / `HIGH` | Analyzes 7-day and 30-day payables vs available cash + expected collections. Gap: $\text{Payables}_{7d} - (\text{Cash} + \text{Receivables}_{7d})$. If gap $> 0 \implies \text{CRITICAL}$, else $\text{HIGH}$ if $\text{Payables}_{30d} > \text{Cash}$. No double counting of parent/installments. |
| **R03** | `RECEIVABLES_COLLECTION` | `CRITICAL` / `HIGH` / `MEDIUM` | Consumes canonical `ObligationAgingService`. Overdue ratio: $\text{Overdue} / \text{TotalReceivables}$. Critical $\ge 50\%$, High $\ge 25\%$, Medium $\ge 10\%$. Bucket breakdown ($1\text{--}30$, $31\text{--}60$, $61\text{--}90$, $90+$). |
| **R04** | `COUNTERPARTY_CONCENTRATION` | `CRITICAL` / `HIGH` / `MEDIUM` | Counterparty concentration ratio: $\max_{c}(\text{Receivables}_c) / \text{TotalReceivables}$. Critical $\ge 60\%$, High $\ge 40\%$, Medium $\ge 25\%$. Handles division by zero safely when receivables = 0. |
| **R05** | `RECURRING_OPERATIONS` | `HIGH` / `MEDIUM` | Evaluates active recurring expense commitments over horizon ($H \in \{7, 30, 60, 90\}$ days) vs available cash. Recurring ratio: $\text{RecurringExpenses} / \text{Cash}$. High $\ge 70\%$, Medium $\ge 40\%$. Excludes paused, archived, and skipped occurrences. |
| **R06** | `CASH_POSITION` | `CRITICAL` | Detects any financial account where actual posted ledger balance $< 0$. Strictly distinguished from forecast deficit. Formatted with exact currency separation. |
| **R07** | `DATA_INTEGRITY` | `HIGH` / `MEDIUM` | Detects unscheduled obligations (missing `dueDate`) and pending unreviewed OCR drafts (`status = DRAFT`). Flags `DATA_LIMITED` status: missing data never produces a false `LOW` risk. |
| **R08** | `BANK_RECONCILIATION` | `HIGH` / `MEDIUM` / `NOT_ASSESSED` | Detects unmatched bank statement entries. High $\ge 10$ unmatched or $\ge \text{PKR } 100,000$. Marked `NOT_ASSESSED` when zero bank accounts or zero statement entries exist. |

---

## 3. Backend REST Endpoints

All endpoints require authenticated session cookies and resolve active business context server-side:

### 3.1 `GET /api/finance/risk/assessment`
Returns the comprehensive financial risk assessment:
*   `query parameters`: `asOfDate` (optional, ISO date), `horizonDays` (optional, 7/30/60/90, default 30), `currency` (optional, default "PKR"), `category` (optional, `RiskCategory`), `severity` (optional, `RiskSeverity`).
*   `response`: `RiskAssessmentResponse` containing contract version, businessId, summary KPIs, category assessments (all 8 categories), and filtered insights sorted by severity (`CRITICAL` first).

### 3.2 `GET /api/finance/risk/insights`
Returns active `FinancialRiskInsight[]` matching filters, with full explainability, "Why It Matters", evidence references, and actionable recommendations.

### 3.3 `GET /api/finance/risk/summary`
Returns high-level `RiskSummary` with severity counts, assessed category coverage, and data completeness flags.

---

## 4. Frontend Risk Dashboard & Navigation

*   **Dedicated Route:** `/finance/risks` (`frontend/src/app/finance/risks/page.tsx`).
*   **Embedded View in Records:** `/records?view=risks` (`frontend/src/app/records/page.tsx`).
*   **Bilingual Coverage (EN/UR):** Complete 1:1 symmetry across all labels, severities, urgencies, and category descriptions. Supports RTL layout direction.
*   **Component Structure:**
    *   `FinancialRiskWorkspace.tsx`: Main dashboard coordinator.
    *   `RiskSummaryOverview.tsx`: Overall severity badge, counts (Critical, High, Medium, Low), coverage pill, and data completeness banner.
    *   `RiskFilterBar.tsx`: Category selector, severity selector, and horizon pill selector (7d, 30d, 60d, 90d).
    *   `RiskInsightCard.tsx`: Accessible severity badge, headline, financial impact callout, why it matters, evidence drawer, and recommendation action button.
    *   `RiskEvidenceList.tsx`: Drill-down list of supporting financial documents.
    *   `RiskCategoryBreakdown.tsx`: Complete coverage table of all 8 categories with diagnostic notes.

---

## 5. Verification Evidence & Quality Assurance

### 5.1 Backend Verification Suite (25/25 Passed)
*   **Unit Tests (`FinancialRiskAssessmentServiceTest`):** 11/11 passed in 0.92s.
*   **WebMvc Controller Tests (`FinancialRiskControllerTest`):** 5/5 passed in 4.23s.
*   **PostgreSQL 14.22 Integration Tests (`FinancialRiskPostgreSqlIT`):** 9/9 passed in 21.43s.
    *   *Fixture A:* Forecast liquidity shortage detected with CRITICAL severity.
    *   *Fixture B:* Overdue receivables aged calculation with bucket breakdown.
    *   *Fixture C:* Single-customer credit concentration with safe division by zero handling.
    *   *Fixture D:* Upcoming 7-day payment pressure calculation.
    *   *Fixture E:* Recurring commitments (active vs paused/skipped).
    *   *Fixture F:* Currency separation without cross-currency pooling.
    *   *Fixture G:* Incomplete data generates explicit `DATA_LIMITED` status.
    *   *Fixture H:* 12-table SHA-256 database invariants prove bit-for-bit zero mutations.
    *   *Fixture I:* Real Spring Security HTTP session authentication, RBAC, and cross-tenant isolation.
*   **Dev A Regression Suite:** All 27 regression tests across `DailyCashForecastServiceTest`, `CashForecastControllerTest`, `MonthlyLedgerProjectionServiceTest`, and `FinancialQueryServiceTest` passed with 0 failures.

### 5.2 Frontend Verification Suite (236/236 Passed)
*   **Unit & Contract Tests:** 236/236 passed via `npm test` in 21.8s.
*   **TypeScript:** `npx tsc --noEmit` passed with 0 errors.
*   **ESLint:** `npm run lint` passed with 0 errors.
*   **Production Build:** `next build` compiled `/finance/risks` successfully.

### 5.3 Automated Microsoft Edge Browser Acceptance (12/12 Passed)
*   *Check 1:* Dedicated `/finance/risks` loads authorized heading and workspace.
*   *Check 2:* Summary KPI cards render overall severity badge and count metrics.
*   *Check 3:* Data completeness constraint warning banner renders.
*   *Check 4:* Active risk insight cards render across multiple categories.
*   *Check 5:* Supporting financial evidence drill-down expands with source item trace.
*   *Check 6:* Action recommendation box renders with urgency badge and target link.
*   *Check 7:* Category filtering dynamically restricts active alert cards.
*   *Check 8:* Category breakdown table displays all 8 categories and diagnostic notes.
*   *Check 9:* Embedded tab in `/records?view=risks` mounts `FinancialRiskWorkspace`.
*   *Check 10:* Bilingual Urdu switch updates layout to RTL and renders translated copy.
*   *Check 11:* Mobile responsive layout (390x844 viewport) fits with zero horizontal overflow.
*   *Check 12:* Zero browser runtime errors, CSP violations, or hydration mismatches.
*   *Artifacts captured:* `risk-desktop.png`, `risk-urdu.png`, `risk-mobile.png`, `results.json`.
