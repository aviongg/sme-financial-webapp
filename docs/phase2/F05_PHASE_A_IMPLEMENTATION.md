# F05 — Project, Job and Order Finance: Phase A Implementation Contract (Project Planning + Budgeting Foundation)

* **Date:** 25 October 2026 (Asia/Karachi)
* **Feature / Slice:** F05 — Project, Job and Order Finance (**PHASE A ONLY: Project Planning + Budgeting Foundation**)
* **Feature Lead / Reviewer:** Developer B (full-stack lead) / Developer A (cross-domain financial reviewer — pending)
* **Authoritative Base:** `dev/fatima` at `ea8e812f1d891c07362883a4374754e48485c884`
* **Isolated Worktree / Branch:** `C:/Users/Hp/FYP/wt-f05-phase-a` / `codex/f05-project-planning-phase-a`
* **Migration:** `V30__project_planning_foundation.sql` (V1–V19 and V29 untouched; V20–V28 remain externally reserved)
* **Formal Status:** **PARTIAL_VERIFIED (Phase A Planning + Budgeting Foundation complete; Phase B actual revenue/cost attribution and C05/C01/C04/C08 conversion remain out of scope / blocked on cross-domain dependencies)**

---

## 1. Implemented Phase A Capabilities

1. **Tenant-Scoped UUID Persistence (`V30__project_planning_foundation.sql`):**
   * `projects`: UUID primary key (`gen_random_uuid()`), composite tenant-scoped key `UNIQUE (business_id, id)`, business-scoped uppercase project code `UNIQUE (business_id, code)`, terminology `kind` (`PROJECT`, `JOB`, `ORDER`), lifecycle `status` (`DRAFT`, `ACTIVE`, `COMPLETED`, `CANCELLED`, `ARCHIVED`), fixed currency `'PKR'`, optional `description`, optional `start_date` and `target_end_date` (`target_end_date >= start_date`), optimistic `version`, and human actor `created_by` / `updated_by` UUIDs.
   * `project_budget_versions`: UUID primary key, composite tenant-scoped FK `(business_id, project_id) -> projects(business_id, id) ON DELETE RESTRICT`, monotonic `revision` (`UNIQUE (business_id, project_id, revision)`), status (`DRAFT`, `APPROVED`, `SUPERSEDED`), partial unique indexes enforcing at most **one** `DRAFT` (`uq_project_budget_versions_one_draft`) and at most **one** `APPROVED` (`uq_project_budget_versions_one_approved`) version per project, exact `NUMERIC` fields (`planned_revenue NUMERIC(16,4)`, `planned_cost NUMERIC(35,12)`, `planned_margin NUMERIC(35,12)`, `planned_margin_pct NUMERIC(39,12)`), nullable `source_estimate_ref VARCHAR(120)` (no FK to non-existent estimates table), optimistic `version`, and approval/supersession provenance (`approved_by`, `approved_at`, `superseded_by_budget_id`, `superseded_at`).
   * `project_budget_lines`: UUID primary key, composite tenant-scoped FK `(business_id, project_id, budget_version_id) -> project_budget_versions(business_id, project_id, id) ON DELETE CASCADE`, `line_number` (`1..100`), `category` (`MATERIAL`, `LABOUR`, `OVERHEAD`, `FREIGHT`, `OTHER`), `description`, `quantity NUMERIC(15,6)`, `unit`, `unit_cost NUMERIC(18,6)`, and server-calculated `planned_line_cost NUMERIC(33,12)`.
   * **Database Immutability Triggers & Grants:** `trg_project_budget_versions_immutability`, `trg_project_budget_lines_immutability`, and `trg_project_budget_lines_no_truncate` prevent any financial mutation, deletion, or `TRUNCATE` of `APPROVED` or `SUPERSEDED` budget versions and their lines, while allowing the atomic `APPROVED -> SUPERSEDED` status transition during new revision approval. Least-privilege grants are applied to `finsight_app` (`SELECT, INSERT, UPDATE, DELETE`) and `finsight_backup` (`SELECT`).

2. **Authoritative Backend Calculations & Concurrency (`com.app.sme_health_backend.projects`):**
   * `ProjectBudgetCalculator` & `ProjectDecimals`: Exact `BigDecimal` calculations (`plannedLineCost = quantity * unitCost`, `plannedCost = sum(plannedLineCost)`, `plannedMargin = plannedRevenue - plannedCost`, `plannedMarginPct = plannedMargin / plannedRevenue` at 12-scale `HALF_EVEN`, or `null` with `plannedMarginPctUnavailableReason = "ZERO_PLANNED_REVENUE"` when `plannedRevenue == 0`).
   * `ProjectRequestReader`: Closed-schema Jackson parser (64 KB payload cap, `STRICT_DUPLICATE_DETECTION`, `FAIL_ON_TRAILING_TOKENS`, no numeric-to-string coercion).
   * `ProjectPlanningService`: Enforces parent business row lock (`SELECT id FROM businesses WHERE id = ? AND status = 'ACTIVE' FOR UPDATE`), project row lock (`FOR UPDATE`), deterministic budget revision locking (`ORDER BY revision ASC, id ASC FOR UPDATE`), optimistic `expectedVersion` / `expectedProjectVersion` / `expectedBudgetVersion` checks returning HTTP `409 Conflict` (`stale_project_version`, `stale_budget_version`, `duplicate_project_code`, `draft_budget_already_exists`, `open_draft_budget_exists`, `budget_immutable`, `invalid_project_state`, `invalid_project_transition`, `invalid_budget_transition`, `project_delete_forbidden`), draft-only project deletion when no `APPROVED` or `SUPERSEDED` history exists, and mandatory `SecurityAuditService` events (`PROJECT_CREATED`, `PROJECT_UPDATED`, `PROJECT_DELETED`, `PROJECT_STATUS_TRANSITIONED`, `PROJECT_BUDGET_CREATED`, `PROJECT_BUDGET_UPDATED`, `PROJECT_BUDGET_DELETED`, `PROJECT_BUDGET_APPROVED`) with `actorUserId = context.userId()` and `businessId = context.businessId()`.

3. **REST API Contract (`/api/projects`):**
   * `GET /api/projects` (`FINANCIAL_DATA_READ`)
   * `POST /api/projects` (`RECORD_CREATE_UPDATE`)
   * `GET /api/projects/{projectId}` (`FINANCIAL_DATA_READ`)
   * `PUT /api/projects/{projectId}` (`RECORD_CREATE_UPDATE`)
   * `DELETE /api/projects/{projectId}?expectedVersion={v}` (`RECORD_CREATE_UPDATE`)
   * `POST /api/projects/{projectId}/transition` (`RECORD_CREATE_UPDATE`)
   * `GET /api/projects/{projectId}/budgets` (`FINANCIAL_DATA_READ`)
   * `POST /api/projects/{projectId}/budgets` (`RECORD_CREATE_UPDATE`)
   * `PUT /api/projects/{projectId}/budgets/{budgetId}` (`RECORD_CREATE_UPDATE`)
   * `DELETE /api/projects/{projectId}/budgets/{budgetId}?expectedBudgetVersion={v}` (`RECORD_CREATE_UPDATE`)
   * `POST /api/projects/{projectId}/budgets/{budgetId}/approve` (`RECORD_CREATE_UPDATE`)
   * `GET /api/projects/{projectId}/planning-summary` (`FINANCIAL_DATA_READ`)

4. **Frontend Routes & Bilingual EN/UR/RTL Experience:**
   * `/projects` (`ProjectList.tsx`): Filterable by kind (`PROJECT`, `JOB`, `ORDER`) and lifecycle status, showing approved planned revenue/cost/margin/margin% or explicit unapproved/draft status, with role-gated create action (`OWNER`/`ACCOUNTANT` write; `MANAGER`/`VIEWER` read-only).
   * `/projects/new` (`ProjectForm.tsx`): Create project with code, name, kind, description, start date, and target end date; read-only roles see an explicit read-only state without mutation controls.
   * `/projects/[id]` (`ProjectDetailWorkspace.tsx`): Full workspace for metadata editing, draft project deletion (when eligible), lifecycle status transitions, open draft budget line editor (`1..100` lines across `MATERIAL`, `LABOUR`, `OVERHEAD`, `FREIGHT`, `OTHER`), clearly labeled `DRAFT PREVIEW` vs server-authoritative `PLANNED` summary cards, budget approval confirmation dialog, immutable revision history inspection (`APPROVED` and `SUPERSEDED`), and `409 Conflict` reload banners.
   * Navigation (`navigation.ts`, `Sidebar.tsx`, `MobileNav.tsx`) exposes `/projects` in live mode and requires `RECORD_CREATE_UPDATE` for `/projects/new`.

---

## 2. Explicit Non-Implemented Scope (Deferred to Phase B / Cross-Domain Dependencies)

* **No actual revenue or actual cost attribution:** No `recognizedRevenue`, `recognizedCost`, `cashReceived`, `cashPaid`, `budgetVariance`, or `project_attributions` table.
* **No C01/C02/C04/C06/C07/C08/C09 integration:** No ledger posting, monthly projection writes, counterparty/invoice/bill linkage, funding-gap series, inventory COGS issues, salesperson commissions, or FX/tax calculations.
* **No `MonthlyRecord` or `ScoringService` (`health-score-v1`) mutation:** Creating, editing, approving, transitioning, or deleting project planning records never reads from or writes to `monthly_records` or `score_results`.
* **No F06 source modification or conversion:** `estimates/` backend and frontend files are untouched.
* **No `ModuleRegistry` or F13 `WidgetRegistry` activation:** `projects` remains `future("projects", "core_finance")` in `ModuleRegistry` and dashboard `projects`/`budgets`/`funding-gap` widgets remain `implemented = false` until Phase B actual project profitability is delivered.


## Integration provenance and measured verification

Source commit `0323948acfb824c4be9d3328cabcb7ac9069a113` was integrated onto F12B `ba2e3bc5f1884c00ebdf98070b17df3ed26a80cd`, recovering the existing no-commit cherry-pick and semantically preserving both navigation entries and documentation. The containing commit identifies the delivery; the external final handoff records the exact remote SHA. [Combined state](IMPLEMENTATION_STATE.md) and [dated evidence](verification/2026-10-28-f05-integration.json) supersede isolated-development evidence for integration readiness.

Combined results:42 F05 unit and11 PostgreSQL cases,25 F12B unit and9 PostgreSQL cases;1,039 default backend passes plus one existing Windows symlink skip;168 local Failsafe passes;110 frontend unit passes; TypeScript/ESLint/production webpack build pass. Nineteen production-target cases in two explicitly excluded classes remain unaccepted. True F05 browser acceptance passed24 checks; F12B/Pilot/F02/F13/F06 passed15/11/11/16/14. Four F05 desktop/mobile EN/UR/RTL screenshots were visually inspected. All browser suites reported zero runtime/CSP/hydration errors and unexpected external requests.

The V30 review added an UPDATE-only OLD-parent guard to prevent moving budget lines out of immutable approved/superseded history; regression assertions cover both states and retained history/draft cleanup. Exact migration versions are1–19,29,30. Schema descriptions above match actual precision, scoped keys, index names and deletion rules. No actual-finance functionality was added.

### Reproduce the real F05 browser journey

Use a production frontend built with `BACKEND_API_URL=http://127.0.0.1:8080` and `NEXT_PUBLIC_DATA_MODE=live`: from `frontend`, run `node node_modules/next/dist/bin/next build --webpack`, then `node node_modules/next/dist/bin/next start --hostname 127.0.0.1 --port 3100`. Use free ports3100/8080 and preserve any unrelated servers. Set `FINSIGHT_BROWSER_NODE` to the Node executable, `PLAYWRIGHT_MODULE` to the installed Playwright package, `FINSIGHT_BROWSER_BASE_URL=http://127.0.0.1:3100`, and `BROWSER_ARTIFACT_DIR` to a new disposable evidence directory outside user `tmp/`. From `backend`, run `mvn -o -B -Dtest=F05ProjectsBrowserAcceptance test` (or the repository Maven wrapper with its populated dependency cache). The Java harness starts real Spring on loopback8080 using `DisposablePostgres`; do not supply a developer/shared/production database. Edge is launched through Playwright. Stop only the Next server created for this run.

The24-check journey authenticates through UI, creates PROJECT/JOB/ORDER, reloads persisted lists, edits five budget categories, verifies backend1000/800/200 then1200/960/240/20% planned totals, approves two revisions and checks superseded history, tests genuine backend validation and VIEWER denial, switches language and checks mobile RTL/overflow. A per-run handshake captures nine full financial/configuration/document table snapshots after synthetic actual-record fixtures exist; statement triggers detect even same-value INSERT/UPDATE/DELETE/TRUNCATE on monthly_records and score_results. The final run proved zero such statements. Business profile language changes are intentional. Stored score comparison uses `/api/scores/latest`; unavailable F13 metadata uses `/api/workspace/widgets`, avoiding unrelated full-dashboard refresh behavior. No success response is mocked. The harness emits `results.json`, `database-boundaries.json`, diagnostics and four screenshots only after actual checks.

Review remains bounded Phase A; reciprocal review and deployed/provider/client acceptance are not claimed. Previous failed attempts and exact commands are retained in the dated evidence.
