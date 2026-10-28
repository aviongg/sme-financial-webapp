# F06 Phase B — persisted estimates, approval and exact-fit F05 conversion

Explicitly requested bounded slice, developed 28 October 2026 (Asia/Karachi) in temporary worktree `C:/Users/Hp/FYP/wt-f06-persisted-estimates`, local branch `claude/f06-persisted-estimates-phase-b`, base `cae92058bca1063a948e4e67e6c8c2694f87aa58`. Local commit only; nothing pushed; no remote branch. The [Phase A preview contract](F06_IMPLEMENTATION.md) is unchanged and remains authoritative for calculation semantics. Evidence: [state](IMPLEMENTATION_STATE.md) and [dated JSON](verification/2026-10-28-f06-phase-b.json).

Everything here is **PLANNING ONLY**. Saving, approving or converting creates no FinancialTransaction, AccountMovement, cash, revenue/expense recognition, receivable, payable, allocation, F04 invoice/bill, inventory, COGS, actual project figure, MonthlyRecord or score row, and `health-score-v1` is untouched. Estimate approval is not posting. Conversion creates only an F05 DRAFT planning budget (which stays `PLANNED_ONLY`) plus immutable lineage and audit events.

## Precision decision (user-approved) and P10

* Estimates persist the **full Phase A domain** (`f06-preview-v1`): inputs of 1–12 integer and 0–12 fractional digits, exact products and sums, and scale-24 HALF_EVEN **calculation** division. Nothing is narrowed to F05.
* F06 → F05 conversion is **exact-fit only**. A value converts only when F05 (V30 columns and `ProjectDecimals` input patterns) can hold the identical number. There is **no rounding, truncation, HALF_UP/HALF_EVEN adjustment or normalization**. Trailing zeros are not precision (`1.500000000000` is `1.5`); every other digit must survive.
* A failing estimate stays a **valid estimate**; only conversion is refused with `422 conversion_precision_unrepresentable` and `details[]` (`lineNumber`, `field`, `sourceValue`, `reason`, `target{column,precision,scale,maxIntegerDigits}`). The same issues are returned in every revision's `conversionReadiness`, so the UI shows a conversion limitation, not an invalid estimate.
* **P10 (accounting/settlement/currency rounding) remains OPEN.** No rounding policy is claimed or approved.

| Converted value | F05 target | Rule |
|---|---|---|
| line quantity | `project_budget_lines.quantity NUMERIC(15,6)`, ≤ 9 integer digits | scale ≤ 6 and integer digits ≤ 9 |
| line unit cost | `unit_cost NUMERIC(18,6)`, ≤ 12 integer digits | scale ≤ 6 and integer digits ≤ 12 |
| line planned cost | `planned_line_cost NUMERIC(33,12)` | scale ≤ 12, integer digits ≤ 21 |
| planned batch price → planned revenue | `planned_revenue NUMERIC(16,4)`, ≤ 12 integer digits | scale ≤ 4, integer digits ≤ 12; a TARGET_MARGIN price must also satisfy `price × (1 − m) = cost` exactly, otherwise `inexact_division` |
| total cost, contribution | `planned_cost` / `planned_margin NUMERIC(35,12)` | capacity check; F05 derives its own margin and margin % |

Examples: TARGET_MARGIN 30% on cost 1000 gives `1428.571428571428571428571429` (`inexact_division`); quantity `1.0000001` (`scale_exceeded`); unit cost `0.1234567` (`scale_exceeded`); quantity `1000000000` (`integer_digits_exceeded`). Revising to the PROPOSED_PRICE `1428.5714` converts. Inside the conversion transaction the service also asserts that F05 stored exactly the source numbers, and rolls back otherwise.

## Schema: `V33__persisted_estimates_and_conversion.sql`

Sole new migration; V1–V31 untouched; **V32 is absent here** (it belongs to the separate F09 worktree). This is not combined V31 → V32 → V33 acceptance.

| Table | Purpose / key rules |
|---|---|
| `estimates` | UUID `id`, `business_id`, normalized tenant-unique `code`, `name`, optional `description`, `base_currency='PKR'`, `status ACTIVE/ARCHIVED`, optimistic `version`, `created_by_user_id`/`updated_by_user_id`, `archived_by_user_id`/`archived_at`, timestamps; `(id, business_id)` unique |
| `estimate_versions` | revision number, `DRAFT/APPROVED/SUPERSEDED`; canonical inputs (`pricing_mode`, `pricing_value`, `saleable_quantity`); authoritative outputs (`total_planned_cost`, `planned_batch_price`, `planned_contribution`, `planned_margin`, `planned_markup`, `planned_per_unit_price` and their three unavailable reasons); `calculation_version='f06-preview-v1'`; approval/supersession actors and times |
| `estimate_lines` | line number 1–100, `cost_group`, stripped text without C0/C1 control characters, `quantity`, `unit`, `unit_cost`, `planned_cost = quantity × unit_cost` exactly |
| `estimate_project_conversions` | immutable lineage: business, estimate, revision, F05 project and DRAFT budget, actor, time, `f06-f05-exact-v1`, source/target calculation versions |

Capacities are derived from the Phase A bounds (documented in the SQL): inputs `NUMERIC(24,12)`, line cost `(48,24)`, total `(50,24)`, price/contribution `(75,36)`, margin `(63,24)`, markup `(87,24)`, per-unit `(75,24)`. CHECKs verify the identities PostgreSQL can evaluate exactly (proposed price, target markup, contribution, availability reasons, approval state). TARGET_MARGIN's scale-24 quotient is verified by the application calculator, not by PostgreSQL's own division scale.

Composite tenant FKs, partial unique indexes for one DRAFT and one APPROVED revision per estimate, and BEFORE triggers enforce: estimates born ACTIVE; ARCHIVED terminal and only without an open DRAFT; deletion only for ACTIVE estimates without APPROVED/SUPERSEDED history; revisions born DRAFT under an ACTIVE estimate; approval only when stored lines sum exactly to the total; APPROVED immutable except the atomic → SUPERSEDED step; SUPERSEDED final; draft-only line changes; insert-only lineage from an APPROVED revision into a same-business F05 DRAFT budget; TRUNCATE forbidden. Lineage has uniqueness on the source revision and on the target budget. It deliberately holds **no foreign keys into F05**, so F05 keeps its existing right to discard its draft or delete a draft project. `finsight_app` has SELECT/INSERT/UPDATE/DELETE on the three estimate tables and SELECT/INSERT only on lineage; `finsight_backup` is read-only.

## Lifecycles and concurrency

Estimate: `ACTIVE → ARCHIVED` (terminal). Revision: `DRAFT → APPROVED → SUPERSEDED`. Approval locks the estimate row, then all of its revisions in `(revision, id)` order, re-verifies that the stored outputs equal `f06-preview-v1` for the stored inputs, supersedes the current APPROVED revision, approves the DRAFT and increments the estimate version. Every revision mutation bumps the estimate version; stale versions return 409. The next revision is created from the approved inputs and never mutates history.

Conversion order: authenticate → active business → same-business estimate and revision → **replay check** → APPROVED and ACTIVE → same-business F05 project open to planning → no open F05 DRAFT budget → exact-fit gate → lock estimate then revisions → recheck lineage and state → **F05 `ProjectPlanningService.createDraftBudgetFromEstimate`** (F05's own project/budget locks, version, lifecycle and one-draft checks) → exact-copy postcondition → lineage insert → audit. Lock order is always estimate → revisions → project → budgets. One deviation from the requested order is documented: the lineage replay check runs before the F05 draft check. Otherwise a replay would report the draft it created as a conflict. A replay with the same target returns `200 {replayed:true}` with the original lineage. A different target returns `409 revision_already_converted`. Any failure has zero side effects (single transaction).

The only F05 production change is that internal entry point. It delegates to the unchanged creation path and additionally fills the existing V30 `source_estimate_ref` (`F06/<code>/R<n>/<revisionId>`). F05's external API, semantics and invariants are unchanged.

## API (`/api`, closed-schema readers, `Cache-Control: no-store`)

| Method / path | Permission | Notes |
|---|---|---|
| `POST /api/estimates/preview` | FINANCIAL_DATA_READ | Phase A, byte-for-byte unchanged |
| `GET /api/estimates?status=&includeArchived=` | FINANCIAL_DATA_READ | register with approved/draft summaries |
| `POST /api/estimates` | RECORD_CREATE_UPDATE | `{code,name,description,draft?}`; draft field paths match the preview (`lines[0].quantity`) |
| `GET/PUT/DELETE /api/estimates/{id}` | read / write / write | PUT `{code,name,description,expectedVersion}`; DELETE `?expectedVersion=` |
| `POST /api/estimates/{id}/archive` | RECORD_CREATE_UPDATE | `{expectedVersion}`. Added because the requested UI requires archive; not in the original list |
| `GET /api/estimates/{id}/versions[/{versionId}]` | FINANCIAL_DATA_READ | `result` is the stored f06-preview-v1 response; `conversion`, `conversionReadiness` |
| `POST /api/estimates/{id}/versions` | RECORD_CREATE_UPDATE | preview fields + `expectedVersion` (estimate version) |
| `PUT/DELETE /api/estimates/{id}/versions/{versionId}` | RECORD_CREATE_UPDATE | DRAFT only; revision `expectedVersion` |
| `POST /api/estimates/{id}/versions/{versionId}/approve` | RECORD_CREATE_UPDATE | `{expectedVersion}` |
| `POST /api/estimates/{id}/versions/{versionId}/convert-to-project` | RECORD_CREATE_UPDATE | `{targetProjectId, expectedProjectVersion}` only; 201 created / 200 replayed / 409 / 422 |

Business IDs, actor IDs, status, revision numbers, totals, outputs, budget values and lineage are unknown fields. OWNER and ACCOUNTANT can mutate; MANAGER and VIEWER are read-only. No new permissions. Audit: `ESTIMATE_CREATED/UPDATED/DELETED/ARCHIVED` and `ESTIMATE_REVISION_CREATED/UPDATED/DELETED/APPROVED/CONVERTED`, plus F05's own `PROJECT_BUDGET_CREATED` carrying `sourceEstimateRef`.

## Frontend

`/estimates` is now the register. The unchanged Phase A stateless editor moved to `/estimates/preview`, with a "Quick preview (not saved)" link; the Phase A browser harness changed only that URL. `/estimates/new` (requires RECORD_CREATE_UPDATE) and `/estimates/[id]` host the workspace: identity, DRAFT editor reusing the Phase A line/pricing components (extracted to `EstimateInputs.tsx` with identical markup and test IDs), server preview, save, keyboard-operable approval dialog stating that nothing is posted, revision history with Superseded markers, next revision, archive/delete, 409 conflict banner preserving edits, conversion review with eligible F05 projects (Draft/Active, no open draft budget), the exact planning-only warning, success/replay messages, lineage with a project link, and the exact-fit limitation listing line/field/value/allowed precision. `PLANNING ONLY` and `PLANNED` labels are shown. EN/UR copy lives in `estimatePlanning.ts`; RTL keeps codes and decimals LTR; mobile has no horizontal overflow. The browser never calculates or converts money. ModuleRegistry `costing` and F13 project-margin widgets remain unavailable.

## Remaining F06 scope (not implemented)

New-project creation during conversion; re-conversion after F05 discards the converted draft; quotations/customer authority and F04 links; tax, FX, yield/wastage, automatic overhead, inventory cost suggestions; actual-vs-estimate; any accounting/settlement rounding (P10); P05/P13 decisions; ModuleRegistry `costing` availability; combined V32 → V33 acceptance and integration into `dev/fatima`; reciprocal Developer A review; production acceptance.
