# Funding and Dashboard Integration — 10 November 2026

**CODEX — FUNDING COMPLETE, DASHBOARD PARTIAL — READY FOR INTEGRATION REVIEW**

Funding and the available core financial-dashboard slice are implemented and verified. Full frontend release readiness remains blocked by inherited configured-lint failures and the separately owned C02/C04/project-module integration limits below. No production certification or complete accounting coverage is claimed.

## Workspace reconciliation and preservation

- Original: `C:\Users\PC\Projects\sme-financial-webapp`, branch `dev/suleman`.
- Original HEAD, cached remote-tracking ref and actual `git ls-remote origin refs/heads/dev/suleman` all verified as `b585dda3bfe9f116cad0bd9609199ca16f748f0b`. Original HEAD was rechecked at completion and remained that value.
- Previous funding files were already committed; the route's last feature commit was `d2a8441ebc30902e0487b4b90891da536b9193db`. No recovery copy, reset, stash, deletion or overwrite was necessary. Existing editors, results, timeline, copy and route were reused.
- Proposed sibling `C:\Users\PC\Projects\sme-financial-dashboard-codex` did not exist. The app-managed worktree tool created and attached `C:\Users\PC\.codex\worktrees\finsight-dashboard-codex\sme-financial-webapp` instead. This is the actual Codex workspace. It starts detached at the published baseline; no new branch was created and the original branch was not switched.
- Managed creation was delayed by an ignored-file scan; attachment was successfully completed and the clean baseline verified before edits. No frontend source was edited in the original checkout.
- Antigravity's original uncommitted C04/C02 work, private scratch paths and `backend/.cp.txt` remain untouched. No shared navigation/translation files were copied over another contributor's changes.
- Installed dependencies were reused through an ignored worktree-local `frontend/node_modules` junction. No installation, dependency refresh, manifest/lockfile change or environment-file edit. SHA256 of package.json and pnpm-lock.yaml matched the originals.

## Implemented and verified: C06 funding

Actual endpoint: **POST /api/finance/funding/scenarios/compare**. Document: `docs/integration/dev-a/f07/C06_FUNDING_SCENARIO_API.md` version **1.0.0**. Actual producer reviewed: FundingScenarioController, request/response DTOs, FundingScenarioService and FundingScenarioCalculator. All financial calculations remain in Java. The controller enforces FINANCIAL_DATA_READ with ordinary sessions and CSRF; the calculation is read-only.

The previous adapter was connected but permissive. It inferred financial categories from cash direction, accepted speculative field aliases and replaced missing result values with zero or a current timestamp. This slice:

- Removes those fallbacks and category guesses; explicitly allowlists the request fields.
- Sends exact canonical decimal strings, PKR only, horizon 7–90, nonnegative manual opening and positive planned events.
- Requests an explicit alternative even when it has no events; an empty alternative is not secretly copied from the baseline.
- Validates source, precision/sign syntax, echoed assumptions, full continuous date coverage, nullable milestones, comparison alignment and calculation provenance before showing numbers.
- Corrects the UI horizon to the producer's inclusive `[asOfDate, asOfDate + horizonDays]`, including the final day (31 positions for a 30-day interval).
- Keeps business/session cancellation, late-response rejection, explicit reruns and no mock fallback. Simulation POSTs still use the established apiClient cookie/CSRF transport.
- Adds peak-deficit date and clear daily-netting/omitted-source explanations. Original server notes remain available in an explicitly English evidence disclosure alongside bilingual primary UI.
- Preserves exact string formatting and the existing accessible SVG/numerical timeline. Only presentation coordinates use bounded numeric ratios; no funding calculations occur in TypeScript.

### Authoritative numerical results

Test as-of date: 2026-11-01, opening manual liquidity 0.00 PKR. Day 1 = 2026-11-02; Day 5 = 2026-11-06; Day 10 = 2026-11-11.

| Result | Baseline | Alternative |
| --- | ---: | ---: |
| Planned payment on Day 1 | 60000.00 | 30000.00 |
| Planned receipt on Day 5 | 20000.00 | 20000.00 |
| Planned receipt on Day 10 | 50000.00 | 50000.00 |
| Day 1 position | -60000.00 | -30000.00 |
| Day 5 position | -40000.00 | -10000.00 |
| Closing position | 10000.00 | 40000.00 |
| Peak funding requirement | 60000.00 | 30000.00 |
| Recovery date | 2026-11-11 | 2026-11-11 |

Backend comparison: peak-funding difference **-30000.00**, closing-liquidity difference **30000.00**. UI cards, negative timeline values and chart reflected the real response. A separate real 100.00 outflow/no-receipts case returned recoveryDate=null and displayed “No recovery within this horizon.” Ledger-sourced opening was verified at **105000.00** for Apex and **50000.00** for Zenith. A third account-free business produced the actual 400 missing-ledger-evidence response; UI showed unavailable evidence rather than zero.

### Contract documentation correction required

The published document's timeline names differ from actual Java JSON. The implementation validates the actual producer:

- Daily position: `openingPosition`, `plannedInflows`, `plannedOutflows`, `netPlannedMovement`, `closingPosition`, `cumulativeProjectedPosition`, `deficit`, `isShortfall`, `eventsOnDay`; no dayIndex.
- Daily comparison: `baselineClosingPosition`, `alternativeClosingPosition`, `positionDifference`, `baselineDeficit`, `alternativeDeficit`, `deficitDifference`; no dayIndex.
- Horizon end is inclusive as above.

Antigravity should reconcile the document with these DTOs. No backend or contract file was edited, and no financial policy was approved by this frontend work.

## Implemented and verified: Dashboard financial integration

Extended F13's **existing financial-summary widget**, not its registry, presets or persistence contract. Existing key/order/size/hide/preferences, optimistic versions, configuration gates and preset previews remain owned by the current F13 architecture. Subcards are part of that existing widget, not independently persisted widgets. Layout preview sends no new financial requests.

- Posted account cash stays separated by declared currency and scale; no FX conversion or combined cross-currency total.
- Last 30 Karachi calendar dates show authoritative cash received/paid/net movement, explicitly not accounting profit.
- Outstanding receivables/payables consume the aging API; obligations are not labeled completed cash payments.
- Forecast closing cash, maximum expected shortfall and first deficit are explicitly FORECAST, not posted actuals. Funding link opens the working C06 route; no scenario is fabricated on the dashboard.
- Deterministic financial-risk summary uses the existing validated adapter and bilingual severity labels.
- Historical monthly trends remain in their own disclosure; health-score, explanation and recommendation widgets retain snapshot provenance and their existing logic.
- Fixed the empty-monthly-record guard: an available financial-summary widget can render real ledger evidence even without any MonthlyRecord or historical score.
- Independent loading/error/unavailable states preserve other cards when one API fails. Partial cash evidence carries explicit completeness metadata. Unknown recognized costs/profit remain unavailable, not zero.
- Selected-project financial overview and costing readers are typed and implemented behind the existing projects module availability gate. Actual cash attribution, outstanding commitments and unposted commercial bills remain separate. This project branch was not live-accepted because the baseline's module registry marks projects unavailable; no frontend module override was introduced.
- C02 scoring cutover remains an explicit unavailable boundary. New C04 recognition route/forms/adapters were not invented or copied from Antigravity's active checkout. A bilingual informational notice prepares discoverability without a dead active link.

### Existing APIs consumed

GET `/workspace` remains the historical dashboard/F13 aggregate. New live reads inside the existing financial-summary widget:

- `/finance/query/cash-position`
- `/finance/transactions/summary?startDate=...&endDate=...`
- `/finance/obligations/aging`
- `/finance/forecast/daily?currency=PKR&horizonDays=30`
- `/finance/risk/summary?currency=PKR&horizonDays=30`

When permitted by existing configuration and a project is selected: `/projects`, `/finance/projects/{id}/financial-overview`, `/finance/projects/{id}/costing-summary`. Existing project DTOs contain legacy JSON-number fields; the new view formats them without monetary arithmetic and refuses missing/invalid values. That wire precision limitation is not repaired by pretending they are exact decimal-string contracts.

## Fresh verification and actual runtime

| Tier / command | Actual result |
| --- | --- |
| `pnpm exec node --test tests/funding-scenarios.test.mjs` | 17 targeted tests passed |
| `pnpm exec node --test tests/dashboard-finance.test.mjs` | 8 targeted tests passed |
| `pnpm test` | **429 passed**, 0 failed/skipped; exit 0; targeted tests are included, not additive |
| `pnpm exec tsc --noEmit --incremental false` | exit 0 |
| ESLint over every changed frontend/test path | 0 errors/warnings; exit 0 |
| Configured `pnpm run lint` | exit 1; **40 errors / 35 warnings** in unchanged baseline feature files; see inherited-lint-paths.txt |
| `pnpm run build --webpack` with process-only live mode / backend 8085 | exit 0; **50 displayed routes**, including funding (51 manifest entries including internal routes); shared installed dependencies reused, own build output |
| Existing published backend `mvnw.cmd -o test -Dtest=FundingScenarioPostgreSqlIT` in this worktree | **5 real PostgreSQL tests passed**, no failures/errors/skips; exit 0 |
| `pnpm exec node tests/funding-browser.integration.mjs` | final **18 grouped real full-stack checks passed**, exit 0; zero browser errors |
| `pnpm exec node --check tests/funding-browser.integration.mjs` | exit 0, including final failure-artifact safeguard |
| Mocked browser suites | 0 executed; fetch fixtures are isolated frontend contract tests, not live evidence |

The explicit backend test selector genuinely discovered five tests: rbacRoleMatrixAndUnauthenticatedRejection, ledgerCashDynamicPostgresIntegration, strictReadOnlyDatabaseInvariant, crossTenantIsolationFailsClosed, section6NumericalFixtureWithManualAssumption. These use Spring MockMvc plus real PostgreSQL, separately from the Edge count; non-owner Edge sessions are not claimed.

Real live environment: Microsoft Edge, production Next 3581, transparent gateway 3580 forwarding **all** requests to Next (including API rewrites), Spring 8085, a fresh disposable loopback PostgreSQL database `postgres` migrated through **V44**. Runtime identity and dynamic database port are recorded in runtime-identity.txt. The old S4B 5434 / backend 8081 / frontend 3100 were neither used nor restarted. Backend source was compiled only under the isolated worktree's ignored target output; no backend source edit.

The harness registered/signed in through real REST authentication and reused its legitimate cookie session in Edge. No authentication/MFA/CSRF bypass setting and no intercepted financial success responses. Browser source/error/tenant checks reach the real database-backed API. The original synthetic registration fixture did not require an MFA challenge under its actual development policy; forced-MFA browser acceptance is not claimed.

### Browser evidence

Final 18 grouped checks cover route/form access, manual configuration, canonical peak/recovery/closing results, non-identical alternative comparison, dialog Escape preserving edits, authorized ledger opening, financial provenance, CSRF/session rejection, tenant isolation, EN/UR/RTL/mobile, F13 cash/forecast evidence, dashboard tenant transitions, real validation/unavailable states, no-recovery and zero financial writes. Some are individual assertions within shared workflows, not 18 unrelated business workflows.

- Dashboard real cash: Apex **105000.00**, Zenith **50000.00** after a real active-business change; old Apex cash absent in the new context.
- UI selected display labels preserve current source distinctions; no F13 preference mutation was sent during dashboard acceptance.
- Mobile **390×844** overflow assertions passed for funding and dashboard; English/Urdu switching and RTL passed. Screenshots visually inspected. Dialog Escape/focus behavior exercised; exhaustive screen-reader/accessibility certification is not claimed.
- Financial table counts before/after: transactions **0/0**, movements **0/0**, accounts **2/2**, monthly records **0/0**, score results **0/0**, obligations **0/0**, counterparties **0/0**. Fixture account/business creation occurred before the invariant checkpoint. No shared reference fixtures were altered.
- First two debug attempts failed on test selectors/redundant language toggling, not financial results. Their logs are preserved separately; their passes are not added to the final count. A later successful pre-completeness run is also retained separately.
- Task-owned browser, gateway, Next, Spring and disposable database were shut down after acceptance. Tested URL was `http://127.0.0.1:3580/finance/funding`; it is not advertised as still running.

Artifacts under `frontend/test-artifacts/`:

- `funding-browser/results.json`, `actual-c06-response.json`, `dashboard-api-evidence.json`, `runtime-identity.txt`.
- `funding-browser/funding-manual-comparison.png`, `funding-desktop.png`, `funding-urdu-mobile.png`, `dashboard-desktop-en.png`, `dashboard-mobile-ur.png`.
- `funding-dashboard/live-final.log`, `unit-final.log`, `changed-lint-final.json`, `lint-final.json`, `inherited-lint-paths.txt`, `check-exits.json`, historical attempt logs.

## Blocked or incomplete / remaining release risks

1. Full configured ESLint is not green. All 40 errors are in unchanged commissions, FX and project-actuals source paths; no rule disable or mass type weakening was used to hide them. Fix in a separately scoped frontend cleanup before a green release checkpoint. Exact files/messages are in lint-final.json.
2. Project dashboard live acceptance depends on the existing module registry enabling projects. The typed readers and missing-data handling are implemented; this branch's actual attribution/commitment figures are not claimed live-verified.
3. Approved C02 ledger-based scoring cutover and the new C04 recognition workspace are not integrated into this isolated baseline. Historical scoring is preserved. Integrate Antigravity's published contracts/route in the final shared-file window; do not turn the notice into an active route until that route exists.
4. Reconcile C06 documentation/DTO names. No backend calculation defect was observed in the exercised cases.
5. Legacy project monetary JSON-number precision remains a producer-contract limitation; new C06 values remain exact strings throughout.
6. F13 subcards share the existing financial-summary preference. Individually persisted widgets need Developer B's registry/API decision, not a parallel client registry.
7. Production security, TLS, forced MFA, backup/recovery, external providers and full accounting coverage remain separate acceptance gates.

## Exact changed-file inventory and integration ownership

All paths below are relative to the actual Codex worktree root:

- frontend/src/components/finance/funding/FundingResults.tsx
- frontend/src/components/finance/funding/FundingWorkspace.tsx
- frontend/src/components/finance/funding/funding-input.ts
- frontend/src/components/workspace/DashboardWorkspace.tsx
- frontend/src/components/workspace/WidgetRenderer.tsx
- frontend/src/components/workspace/FinancialOverview.tsx (new)
- frontend/src/lib/api/dev-a/funding.ts
- frontend/src/lib/api/dashboard-finance.ts (new)
- frontend/src/lib/i18n/translations/funding.ts
- frontend/src/lib/i18n/translations/workspace.ts
- frontend/src/lib/i18n/translations/dashboard-finance.ts (new)
- frontend/tests/funding-browser.integration.mjs
- frontend/tests/funding-scenarios.test.mjs
- frontend/tests/dashboard-finance.test.mjs (new)
- docs/integration/codex/FUNDING_DASHBOARD_COMPLETION.md (new consolidated report)

Shared final-integration review: DashboardWorkspace empty-state guard, WidgetRenderer existing financial-summary case, and workspace translation label. Funding navigation was already committed and remains intact; the dashboard adds a working contextual funding link. No other routes, landing pages, authentication/transport, registry or preference schema were changed.

Worktree is intentionally dirty with the above frontend/report changes, detached at b585dda. Original checkout remains separately dirty with Antigravity's work. Only authorized managed worktree creation/attachment altered Git bookkeeping. No staging, commits, branch switch, merge, rebase, reset, restore, clean, stash, fetch, push or other remote write. Files are ready for the combined integration review; no changes have been copied back automatically.

**Editing paused.**

Functional browser-tested build ID: tLl-g36Bz8kDKKu1GdE8W. Final disposable runtime: LIVE_IDENTITY:postgres:60690:127.0.0.1:V44.


Post-test housekeeping: removed only this task's generated tmp/funding-live-test and tmp/isolated-frontend-funding directories after services stopped, unlinking their dependency/public junctions first. Original dependency targets remain intact; source harness recreates these runtime helpers. Acceptance logs/screenshots and isolated compiled target evidence are preserved.



Final copy-only refinement: dashboard notices now use plain English/Urdu rather than frontend/contract terminology. Its 8 targeted tests and production build passed after the 18/18 functional live run; that unchanged financial workflow was not unnecessarily rerun for wording. Latest build ID: nVQZuNvEmshxiF_mKtjcZ. Build validation used the supported --webpack option; the default Turbopack variant was not executed. The route manifest contains 51 entries, including internal framework entries.


## Authorized local checkpoint handoff

This section supersedes the earlier pre-checkpoint Git-state description. Authorization: CODEX-FUNDING-DASHBOARD-CHECKPOINT. Checkpoint branch: `task/codex-funding-dashboard`; parent: `b585dda3bfe9f116cad0bd9609199ca16f748f0b`. The exact resulting commit SHA is provided in the final handoff and resolves locally via `git rev-parse task/codex-funding-dashboard` (a commit cannot embed its own final hash).

Exactly the 14 implementation/test files in the inventory above plus this report are included. No new feature work or test reruns were needed; saved evidence was inspected: 429 frontend tests, 18 live grouped checks, 5 PostgreSQL tests, TypeScript, changed-file lint and Webpack build passed. Configured lint remains 40 inherited errors / 35 warnings. Its complete unchanged evidence remains at `C:\Users\PC\.codex\worktrees\finsight-dashboard-codex\sme-financial-webapp\frontend\test-artifacts\funding-dashboard\lint-final.json`, excluded from Git with all other logs/build artifacts.

Final integration dependencies:
- C06: published `/api/finance/funding/scenarios/compare`, FINANCIAL_DATA_READ, ordinary cookies/CSRF; preserve actual producer DTO names. Reconcile the v1.0.0 timeline/comparison aliases in its documentation.
- Dashboard: existing `/workspace`, C11 cash position, transaction summary, obligation aging, daily forecast and risk-summary APIs. Project selectors/readers depend on existing F13/F02 project-module availability and the published project financial-overview/costing-summary endpoints.
- Shared files requiring contextual reconciliation: DashboardWorkspace.tsx empty-state guard, WidgetRenderer.tsx financial-summary extension and translations/workspace.ts label. Funding navigation already exists; no replacement registry or preference schema.
- C02: approved daily-ledger scoring cutover is not integrated; retain historical monthly score provenance.
- C04: recognition route/forms/adapters remain separately owned; replace the informational unavailable notice only after that route/contracts are integrated.
- Keep inherited commissions/FX/project-actuals lint cleanup separate. No such modules were changed in this checkpoint.

Only the verified Codex worktree is branched/staged/committed under this authorization. Antigravity's original checkout and its active uncommitted work are not modified. No push, merge, cherry-pick or rebase is authorized or performed. Final integration remains with the integration owner.
