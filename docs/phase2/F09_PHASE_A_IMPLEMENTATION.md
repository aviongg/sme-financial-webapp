# F09 Phase A — Sampling and production cost planning

Implementation completed locally 31 October 2026 (Asia/Karachi). Developer B; reciprocal Developer A review remains pending. This is the explicitly authorized planning slice, not completion of full F09 or approval of recognition, inventory valuation, settlement, FX or sample-to-order accounting policy.

## Delivery and reconciliation

Authoritative base: `311de78f1b517aaaf09420914ebe6a32371280e1` (integrated Candidate A canonical resequencing, F04/V34). Existing worktree: `C:/Users/Hp/FYP/wt-f09-sampling-planning`; branch: `codex/f09-sampling-planning-phase-a`. Final delivery is one local commit: `feat(samples): add sampling and production cost planning foundation`. No push, remote branch, dev/fatima integration or worktree/branch cleanup is authorized.

Original base was `34d9cd86b237cc35ec2fad2cc4ef69ec6de0f877`. Work stopped when the fetched remote changed. The user's subsequent instruction explicitly authorized reconciliation onto the new base. All 34 WIP files (3 modified tracked and 31 untracked) were recorded with Git blob/SHA256 hashes and saved in stash `0e8001eaa51b7ecbceefc500a616ca8169f2c692`. Each stash blob was verified before the same local branch was fast-forwarded, with no reset or preservation commit. Stash application restored all files; all 31 new-file blobs matched. Two conflicts were resolved semantically: retain both F04/F09 state entries, and require migration versions 1–19,29,30,31,32. Both audit-event sets survived. V31, commercial source/tests/docs, Invoices/Bills and Projects/Reports navigation were unchanged at the reconciliation checkpoint; `git diff --check` passed. Raw preservation evidence is task-local under `logs/f09/reconcile-*` and is not committed.


On 30 October the user authorized the next remote advance from `cae92058bca1063a948e4e67e6c8c2694f87aa58` to `311de78f1b517aaaf09420914ebe6a32371280e1` and F09 reservation V35. All 58 current WIP files were verified in stash `3525b88428951a92c7c7f7cf5095a53f685e6e27`, then restored after fast-forwarding the same branch. Three conflicts: state documentation retained Candidate A history plus the current F09 checkpoint; CommercialDocumentMigrationPostgreSqlIT and MonthlyIntegrityMigrationPostgreSqlIT retained the canonical base assertions and gained V35 in the full migration history. Only the F09 migration is renamed from V32 to V35; its SQL statements are unchanged, and its descriptive F04 header now names canonical V34. The separate F06 and frontend UX hardening work is excluded. Both preservation stashes remain retained; no remote branch exists.

## Bounded capabilities and authority

SAMPLE and PRODUCTION_COST_ACTIVITY share one activity/workspace model, with optional same-business existing F05 project/job/order context. FREE, PAID and PARTIAL are recovery-intent labels only. Planned cost and recovery revisions can be drafted, edited, approved, superseded and inspected historically. Every financial result is `financialBasis = PLANNED_ONLY`; base currency is PKR (`APPLICATION_FIXED_PKR`).

COMPLETED closes operational planning only; it does not mean paid, settled, actual costs complete, inventory consumed or an invoice collected. ARCHIVED is terminal/read-only. Planned categories directly reuse F05's MATERIAL, LABOUR, OVERHEAD, FREIGHT and OTHER enum. Existing F04, F05, F06, F12B and F13 financial implementations remain unchanged.

## Calculation and recovery

Backend BigDecimal is the sole authority. Inputs are nonnegative plain decimal strings: quantity up to nine integer/six fractional digits; unit cost up to twelve integer/six fractional digits; planned recovery up to twelve integer/four fractional digits. One to 100 lines. No signs, exponents, separators, numeric coercion or silent financial rounding.

`plannedLineCost = quantity * unitCost`, `plannedCost = sum(plannedLineCost)`, and `plannedContribution = plannedRecovery - plannedCost` when recovery is known. Multiplication, summation and subtraction are exact, without currency-minor-unit quantization. Unknown recovery yields null contribution and a stable unavailable reason. Recorded zero stays distinct from unknown. FREE is explicitly zero; PAID/PARTIAL may have unknown or recorded recovery. No optional recovery ratio is introduced.

## Security and API

Existing session, CSRF, MFA, active-business context, permission and atomic audit architecture apply. `FINANCIAL_DATA_READ` permits read; `RECORD_CREATE_UPDATE` permits all mutations. OWNER/ACCOUNTANT read and mutate; MANAGER/VIEWER read only. Actor and business attribution come from authenticated server context. Cross-tenant IDs return not-found, optimistic stale writes return HTTP409, and writes are never automatically replayed.

Closed-schema requests reject businessId, actorUserId, createdBy, updatedBy, calculated totals, financialBasis, plan status and approval/supersession attribution. Activity create/update and transition follow F05 metadata conventions with activity `expectedVersion`; plan creation requires `expectedActivityVersion`, updates `expectedPlanVersion`, approval/deletion both versions. All decimal financial output remains serialized as exact strings. Request size is bounded to 65,536 bytes, duplicate keys/trailing tokens are rejected, and all responses use Cache-Control: no-store. Validation returns activity_invalid with field reason codes; activity_request_too_large returns 413. Stale codes are stale_activity_version and stale_plan_version.

Routes: `/samples`, `/samples/new`, `/samples/[id]`. API root `/api/samples`: create/list/get/update/draft-delete, `/transition`, `/plans` create/list, `/plans/{planId}` get/update/draft-delete, `/plans/{planId}/approve`, `/planning-summary`. No actual-source or invoice-link/conversion endpoints are provided.

## Schema and integration boundary

Only `V35__sample_cost_planning_foundation.sql` is new, containing `sample_activities`, `sample_cost_plan_versions` and `sample_cost_plan_lines`. Existing canonical V1–V17 and V30–V34 are immutable. Fresh history is exactly V1–V17 plus V30–V35; combined populated verification exercises V33 -> V34 -> V35 and preserves seeded F04 documents and both domains' protections. V36 is reserved for separate F06 work and is absent here.

## Lifecycle, history and project context

| State | Allowed next states | Planning edits |
|---|---|---|
| DRAFT | ACTIVE, CANCELLED, ARCHIVED | Yes |
| ACTIVE | COMPLETED, CANCELLED, ARCHIVED | Yes |
| COMPLETED or CANCELLED | ARCHIVED | No |
| ARCHIVED | None | No |

Closing requires approving or discarding an open draft. Deletion is permitted only for a DRAFT activity without APPROVED/SUPERSEDED history; its unapproved draft lines can then be removed. Plans start as DRAFT/version zero. Each plan write increments the parent activity version. Approval locks the activity and its ordered revisions, supersedes the previous APPROVED plan and approves the new draft atomically. PostgreSQL partial unique indexes enforce one DRAFT and one APPROVED. Approved/superseded financial content and superseded rows are immutable; deletion/TRUNCATE cannot erase history. Revisions are numbered from the maximum retained revision plus one.

Recovery intent is snapshotted on each plan and cannot change while any revision exists. Before any approval, discard the sole draft to edit intent; immutable histories require a separate activity for a different intent. FREE omitted/null input becomes explicit zero. PAID/PARTIAL null produces PLANNED_RECOVERY_UNKNOWN and null contribution; explicit zero produces contribution equal to minus planned cost.

Project linking validates the authenticated business and locks the existing project without updating it. Any non-ARCHIVED project may be linked. A newly selected archived project is rejected; an already-linked project that later becomes archived can retain its context. The tenant-scoped FK prevents removal of referenced projects. An additive advice class inside the samples package translates only ProjectController DELETE failures with SQLSTATE 23503 and the exact sample-project constraint into existing F05 code project_delete_forbidden (409). An open activity can explicitly detach its project first; otherwise the project can be archived. Every unrelated database exception retains existing handling. No F05 source changes are required. All sample metadata updates take the business/project/sample locks in compatible order before checking the expected version. The association never adds a sample plan to F05 budgets or actual profitability.

Storage uses UUID keys and business-scoped unique identities/codes/revisions, composite parent/project/replacement FKs, actor FKs to app_users, status/kind/intent/date/equation checks and fixed PKR. Recovery is NUMERIC(16,4); cost/contribution NUMERIC(35,12); quantity NUMERIC(15,6); unit cost NUMERIC(18,6); line product NUMERIC(33,12). Deferred constraint triggers verify every retained plan has 1..100 lines whose exact sum matches its header, and supersession points to a later approved/superseded revision. Runtime has SELECT/INSERT/UPDATE/DELETE, backup SELECT only; no public table privileges or runtime TRUNCATE. Database triggers also protect parent identity, creation provenance, monotonic versions, line parents, closed activities and lifecycle transitions.
## Not implemented

Actual sample/production cost; F08/material usage; actual labour, overhead or freight; inventory/WAC/WIP/COGS; recognized recovery or received cash; invoice linkage, AR/AP, payments/settlement; commissions; FX; commercial-order conversion; actual project profitability; ledger posting; MonthlyRecord/score mutation; persisted F06 estimates; financial F04 integration. No MonthlyRecord/OPEX inference supplies missing actuals. ModuleRegistry production/inventory remain unimplemented.

## Verification — 30–31 October 2026 (Asia/Karachi)

The authorized bounded Phase A is locally implemented and verified. Formal status is **PARTIAL_VERIFIED** pending reciprocal review; full F09 remains incomplete. [Evidence and all 60 changed files](verification/2026-10-31-f09-phase-a.json) records attempts, commands, exclusions and preservation. [Reproduction commands](DEVELOPMENT_SETUP.md#f09-phase-a-sampling--production-cost-planning-acceptance). The containing local commit is the delivery identity (`git log -1 -- docs/phase2/F09_PHASE_A_IMPLEMENTATION.md`); exact SHA is reported externally. No push or integration is performed.

| Gate | Result and qualification |
|---|---|
| F09 unit | **65 passed**: lifecycle 3, exact calculator 26, closed request reader 25, project-delete advice 10, lock order 1 |
| F09 PostgreSQL | **11 passed**: 2 migration and 9 real session/API/integration cases; no failures/skips |
| Shared targeted run | **65 unit + 29 PostgreSQL/security passed**, including F04 migration 2, monthly migration 2, disposable roles 6 and MFA 8 |
| Full backend default | **1,119 run: 1,118 passed, 1 existing Windows symlink skip**, 0 failures/errors |
| Full local PostgreSQL | **36 classes, 184 cases passed**, 0 failures/errors/skips; production-target role 15/TLS 4 cases excluded, not passed |
| Migrations | Fresh exactly **V1–V17, V30–V35**; populated **V33→V34→V35** preserves actuals, F05 budgets and seeded F04 invoice/bill/numbering data; scoped FKs, immutable history, exact deferred sums, one draft/approved and runtime/backup privileges pass |
| Concurrency | Real two-session simultaneous draft creation, approval/replacement, activity edits and plan edits each yield one winner and one 409; no double draft/approval or lost update |
| Frontend | **134 passed**, including 15 F09; TypeScript passed; ESLint 0 errors with 1 existing SessionProvider warning |
| Build | Production Next 16.3.8 `next build --webpack` passed in live mode; existing middleware deprecation/Edge-runtime dependency warnings retained |
| F09 real browser | **20 checks passed**: real login/CSRF, FREE sample/F05 context, five cost categories, immutable approval/replacement, PAID production, PARTIAL unknown/zero recovery, stale 409 draft preservation/reload, metadata/lifecycle/archive, MANAGER/VIEWER, business isolation, EN/UR/RTL/mobile and no overflow |
| Browser regressions | **F04 22, F05 24, F12B 15, F06 14, F13 16, Pilot 11, F02 AI 11** passed; all seven unchanged harnesses, 113 checks; 0 runtime/CSP/hydration errors or unexpected external requests |
| Financial boundaries | **15 complete preexisting table snapshots unchanged**; **zero write statements** on monthly_records, score_results, document_financial_contributions, three project tables and three commercial tables. Number sequences and health-score-v1 unchanged; F06 preview/F12B report equivalent. Profile-language preference changes are intentional. No inventory, COGS, cash, AR/AP, ledger or actual accounting producer introduced |
| Visual review | Five F09 screenshots inspected: desktop English list/history, mobile English editor, desktop/mobile Urdu/RTL history; readable LTR amounts and no clipping/overflow |

Measured environment: Java 26.0.1 compiling release 21, Maven 3.9.16 offline cache, disposable PostgreSQL 14.22, Node 24.22.1, production Next 16.3.8 and Edge/Playwright on Windows. Synthetic accounts and nonempty actual/F04/F05 fixtures are isolated from user databases. These results do not establish Java 21/PostgreSQL 17/TLS, production/provider/client or reciprocal-review acceptance. No paid-provider calls, shared database, deployment or production action occurred.

Failed attempts remain explicit. A prior-base full run had one MFA concurrent-TOTP verification error; unchanged MFA reruns and the new-base full suite passed. Initial frontend lint found 11 memoization errors, fixed with declared stable callback dependencies without disabling rules. F09 browser attempt 1 failed on its new helper expecting contiguous negative digits despite correctly displayed `- PKR 300.00`; the helper now removes currency/separators and asserts exact amount equality. Attempt 2 passed all 20 checks and SQL boundaries without an application change. Raw logs and five screenshots remain locally under `logs/f09/browser-f09-attempt2/` (other logs under `logs/f09/`), excluded from Git.

Codex reached its usage limit after these checks; on 31 October Claude completed only the final documentation and local commit, re-confirming frontend 134/134, TypeScript exit 0 and ESLint 0 errors directly on the exact final tree, with no source change. V35 exists only on this local branch, not on `dev/fatima`.

All 60 changed paths are listed in the evidence manifest. F06 Phase B (`8ac5da2366160c372364a0e879df79a67a924425`), frontend UX hardening (`f22a1ed71e880c7fd11178efbf146c90c547c970`), main (`b51e27696cccc59b94aa9d9c9388ad0faa5b4176`) and dev/suleman (`f2ed9bda671404598bd73729563c6e0ceb2b08d1`) remain untouched. User DOCX hash matches the original; no user tmp writes/cleanup occurred. Both verified WIP stashes remain retained. No remote F09 branch or push is created. Keep this worktree/branch for separately authorized integration.
