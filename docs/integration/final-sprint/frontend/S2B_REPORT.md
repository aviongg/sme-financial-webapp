# FINAL-S2B — unified frontend workflows

Date: 6 November 2026 (Asia/Karachi). Branch: `dev/suleman`. Starting/final HEAD: `2fa20c3b6ca59d04504290542fdfef21ef7e1da2`.

Status: **FRONTEND IMPLEMENTED / ISOLATED CHECKS PASSED; LIVE ACCEPTANCE BLOCKED ON RUNTIME HANDOFF**. This is not S2B live verification or final integration acceptance.

Root AGENTS v2.0, frontend AGENTS v1.1 and MERGE_BASE_REPORT.md inspected. MERGE_BASE_READY confirmed. Existing source/contracts and package scripts govern implementation. The user released the complete frontend editing scope. No nested instructions found under the affected app/components paths. Initial status contained backend/.cp.txt, docs/audits/ and docs/integration/ only. Concurrent backend AI/evidence changes were observed later and preserved.

## Implemented behavior

- Records tab resolution gives an explicit recognized view priority over a retained draftId. Tab links clear transient add/detail/draft/focus requests while preserving independent query filters and pagination. Receivables and Payables use distinct keys so React cannot retain the wrong initial direction. All existing views preserved: Activity, Money accounts, Contacts, Receivables, Payables, Advances, Calendar, Reconciliation, Recurring, Forecast, Risks, To review and historical summaries. Spreadsheet review/drafts is directly reachable with return context. Horizontal scrolling is confined to the tab strip rather than widening the page.
- Financial destinations are shared between desktop and mobile through FinancialWorkspaceLinks and a typed registry of actual routes. The mobile disclosure starts collapsed rather than displaying thirteen extra links immediately. The mobile action sheet is available to read-only users too; posting still uses OWNER/ACCOUNTANT checks and server authorization. Existing Developer B commercial/planning links retained. Nested Records/finance routes highlight on mobile; query-only Add record is not highlighted just because pathname is /records. Icon-only desktop links have names; active links expose aria-current. Navigation titles/collapse controls are bilingual.
- Ask FinSight remains reachable when its configured module is available even if the provider status is temporarily unavailable; its own page/status handling remains responsible for honest unavailable states. Existing configuration/module policies are consumed; no new module keys or capability resolver invented. Historical reads remain accessible under the established contracts.
- Existing routine session revalidation behavior is retained and its security-boundary predicate extracted/tested. Name/language updates preserve private forms; user/auth-stage/status/password/platform-role/business/membership/role/type/active changes invalidate the boundary. Late sign-out success/failure cannot overwrite a newer frontend security transition. Existing cookies, CSRF transport, cancellation and no-automatic-replay conventions preserved.
- The dashboard explicitly labels its health score/trends as saved historical monthly summaries, separate from posted account balances, obligations and future forecasts. Historical trend links now open /records/history. C02 Phase A remains a read-only bridge, with no ledger score cutover implemented or implied.
- PKR presentation of new string amounts avoids parseFloat/Number entirely; old numeric DTOs remain supported as display input without performing financial arithmetic. Missing/invalid amounts display an em dash, distinct from real zero. Obligation and aging components no longer use their duplicated lossy formatters. Missing aging buckets/summary counts are not fabricated as zero; no financial total is recomputed. An action row now wraps on narrow screens.
- Sheet/Dialog share actual focus containment, Escape ownership for nested dialogs, restored opener focus, preserved form autofocus, body-scroll restoration and unique accessible IDs. Nested effects are ordered by DOM containment. Titles/descriptions and close controls are bilingual; dialog content is viewport-bounded.
- Existing commercial/planning boundaries are preserved, not redesigned: invoices/bills NONE, projects/samples PLANNED_ONLY, estimates draft planning, inventory catalog/WAC sandbox. No COGS defaults, recognition rules or posting APIs were added.

## Exact frontend paths changed

- `frontend/src/app/records/page.tsx`
- `frontend/src/components/auth/SessionProvider.tsx`
- `frontend/src/components/integration/LiveDashboard.tsx`
- `frontend/src/components/layout/FinancialWorkspaceLinks.tsx`
- `frontend/src/components/layout/MobileNav.tsx`
- `frontend/src/components/layout/Sidebar.tsx`
- `frontend/src/components/records/obligations/ObligationAgingCard.tsx`
- `frontend/src/components/records/obligations/ObligationsWorkspace.tsx`
- `frontend/src/components/records/workspace-routing.ts`
- `frontend/src/components/ui/Dialog.tsx`
- `frontend/src/components/ui/Sheet.tsx`
- `frontend/src/components/ui/useModalFocus.ts`
- `frontend/src/components/workspace/RecordedTrends.tsx`
- `frontend/src/lib/api/navigation.ts`
- `frontend/src/lib/api/session.ts`
- `frontend/src/lib/i18n/translations/unified.ts`
- `frontend/src/lib/navigation/workspaces.ts`
- `frontend/src/lib/utils/decimal-display.ts`
- `frontend/tests/commercial.test.mjs`
- `frontend/tests/unified-live.integration.mjs`
- `frontend/tests/unified-workflows.test.mjs`
- `frontend/tests/ux-hardening.test.mjs`

This report is the only new frontend evidence document. No existing backend report/shared register modified. No manifests, lockfiles, dependency/environment files, backend, migration, instruction or Git configuration edits.

## Current checks

Existing installed dependencies only; process-only `pnpm_config_verify_deps_before_run=false` for all pnpm commands.

| Check | Actual result |
|---|---|
| `pnpm test` | Exit 0, **368 passed / 0 failed / 0 skipped**. Includes 10 new focused tests. Full suite also exercises existing account/posting/import/draft/commercial/project/estimate/sample/inventory/configuration financial boundary tests with controlled fixtures. |
| `pnpm exec node --test tests/unified-workflows.test.mjs` | Exit 0, **10/10**, rerun after the test-only CommonJS lint correction. |
| `pnpm exec tsc --noEmit --incremental false` | Exit 0. |
| `pnpm run lint` | Final full-tree run: **exit 0, 0 errors / 9 pre-existing warnings** (SessionProvider ref cleanup, existing financial-risk/obligation browser-script unused variables). No new warnings. A test-only reserved-variable error was corrected and its affected tests rerun. |
| `pnpm run build` | Exit 0, final live production build **S1nw70PS7aZFloQLUgpro**, all 45 routes retained. Process-only NEXT_PUBLIC_DATA_MODE=live and BACKEND_API_URL=http://127.0.0.1:8280; no .env overwrite. Existing middleware deprecation only. |
| `node --check tests/unified-live.integration.mjs` | Exit 0; syntax validation, not browser execution. |
| `pnpm exec node tests/unified-live.integration.mjs` | Exit **1** at explicit environment preflight: LIVE_ENVIRONMENT_REQUIRED. No tests silently skipped/passed. |
| Source hygiene / dependency diff | `git diff --check -- frontend` and dependency-file diff checks exit 0. |

The 368-test suite ran before a test-only variable rename; the affected 10-test file was rerun successfully afterward. No application source changed after the final build.

## Verification tiers and live dependency

Unit/contract tests use controlled responses and test fixtures, and the modal test uses a controlled DOM/hook boundary. These are not live browser evidence. No mocked browser suite was run for this task. No screenshots or traces from this changed build exist yet. No real browser acceptance, new backend test run, real SQL fingerprint, mobile geometry or screen-reader acceptance is claimed. Source/isolated EN/UR coverage passes; actual EN/UR/RTL, 390x844, focus and browser-back acceptance remain pending.

At inspection no established frontend/backend review ports were listening, and the backend handoff directory contained MERGE_BASE_REPORT.md only. Antigravity is editing backend evidence integration concurrently. Do not compile/restart its classes or infer a usable V37 runtime from historical build reports. An asynchronous request for the live/build handoff was sent through the user coordination channel. Frontend builds used this task's exclusive frontend ownership and did not touch Maven outputs or start/stop services.

### Required handoff from Antigravity

1. A coordinated live window with current compiled Spring and **disposable PostgreSQL through V37**, using the same verified backend fixtures. Preserve other review databases/processes.
2. A private fixture manifest outside the repository: `disposable: true`, `migrationHead: 37`, `frontendUrl` (loopback live Next), `email`, `password`, `businessAId`, `businessBId`, `businessAName`. Use synthetic OWNER fixtures in two businesses with existing supported MFA/session procedures; do not disable MFA/security. Never commit this manifest or print its credentials. If MFA is required, complete the approved procedure and adapt the runner explicitly rather than bypassing it.
3. The frontend URL must serve the current live build with the established real Spring rewrite/gateway. No financial API intercepts.

Run from frontend with auto-install disabled:

```powershell
$env:pnpm_config_verify_deps_before_run='false'
$env:FINSIGHT_LIVE_FIXTURE='<private approved fixture JSON path>'
pnpm exec node tests/unified-live.integration.mjs
```

The runner uses existing Playwright and installed Edge. It fails when prerequisites are absent. It never starts a mock server or intercepts financial requests. It writes ignored artifacts under frontend/test-artifacts/unified-live (or BROWSER_ARTIFACT_DIR). The manifest migration head is reported as fixture-owner evidence, not independently proven by browser HTTP.

### Prepared live coverage; all currently UNEXECUTED

| Journey | Prepared assertions |
|---|---|
| A | Protected navigation and normal sign-in; MFA challenges are not disabled. |
| B | Actual active-business switch clears private source; foreign account ID is real API 404; same-business language change preserves input. |
| C/D | Real account creation form; receipt/expense forms; persisted account/history. Independent expected balance: 100000 + 20000 - 5000 = 115000 PKR. |
| E | Real forecast/risk endpoints and corresponding rendered pages. |
| F | Create/finalize commercial invoice through real HTTP; browser inspects it; account balances, monthly records and score history unchanged. This is explicitly HTTP finalization plus browser inspection, not a browser finalization claim. |
| G | Projects, estimates, samples, inventory and Ask FinSight accessible live routes. No create/conversion/stock-valuation claim. |
| H | English/Urdu, RTL, 390x844 page bounds, mobile financial disclosure, Sheet keyboard containment and screenshots. |

The prepared runner has passed syntax checks only. Its selectors/expectations and all actual endpoint responses still need verification in the coordinated runtime. No endpoint defect was established by this preflight; exact blocker is environment/fixture availability, not a claim that contracts or endpoints are absent.

## Pending / deferred

- Execute all real journeys, including actual CSRF/session/role/tenant requests and independently verified backend fixtures; fix any failures within frontend ownership.
- Verify financial table fingerprints through Antigravity's fixture/database evidence. Browser API balances/monthly/scores alone do not prove every protected table is unchanged.
- Genuine transfers/reversals, obligations creation/settlement, import-to-draft persistence and recurring occurrence lifecycle need the broader combined live suite; existing isolated tests do not prove those journeys.
- C11/AI source wiring is Antigravity's concurrent backend responsibility. Existing source-mode contracts are not frozen/approved by this frontend task.
- No full accounting, ledger score cutover, production readiness or external provider acceptance claimed.

**EDITING_PAUSED pending coordinated live fixture handoff. Do not mark CODEX S2B FRONTEND VERIFIED yet.**
