# Candidate v2 historical browser reconciliation

Application baseline: **`71ac4e0e7e74cbfe2f08fe3a049d37cd26b0c7c3`**.
Separate branch: `test/finsight-v2-historical-browser-reconciliation`.
Isolated worktree: `tmp/finsight-v2-browser-reconciliation`.
Only tests, fixture helpers, a test workflow and handoff/evidence changed.
Production frontend/backend, dependencies, V1–V52 and runtime architecture are unchanged.
No merge, deployment, paid/live provider call, demo/customer database or document storage was used.

## Measured results and classifications

All five original failures were reproduced before semantic repairs. The original
journeys/assertions were retained for that reproduction; only synthetic server
ports, runner ownership and risk's local Playwright resolution were adapted.
The original installed Edge channel was retained. Repaired runs use the declared
`playwright@1.62.0` Chromium installation. This is **production Next + synthetic
API contract fixture evidence**, not real backend/provider acceptance.

| Driver | Original | Classification and current-journey repair | Repaired local result |
|---|---|---|---|
| `browser-landing.integration.mjs` | 19/20, no-business transition | Broken session fixture transition plus obsolete remembered-session CTA/probe assumptions. Changing the server's mode does not log a browser in. Enter `/onboarding` to probe identity, then verify no-business setup; remembered sessions show **Open FinSight**. Anonymous public pages still make zero API requests; remembered landing may revalidate identity but must never fetch finance. A stale hint cannot authorize private content | **20/20** |
| `browser.integration.mjs` | Initial anonymous-root sign-in timeout | Obsolete entry: `/` is public; click Sign In. Monthly records moved to `/records/history` and are derived/read-only. Replace retired total editing with source evidence and the supported supplemental-financing PUT journey. Original unknown `cashInflow` in a monthly record was an invalid synthetic response: V1 `NOT NULL`, entity and current TS contract require it. Unknown-cash tests now target nullable cash-history/summary fields; optional unknown COGS remains asserted in monthly sources | **24/24** |
| `browser-phase-two.integration.mjs` | Initial Email timeout | Obsolete root entry, pre-product module registry, empty-workspace assumption and `/ai/explain` chat journey. Current registry implements all 15 modules; never-configured defaults expose all effective modules. Use `/copilot/status` and `/copilot/ask`, current route guards, source evidence, rolling history and current limit response | **19 passed, 1 genuine failure (DEF-08)** |
| `browser-extra.integration.mjs` | Initial sign-in timeout | Obsolete root entry and old document confirmation labels. Current labels describe **reviewed evidence**, explicitly without monthly financial changes | **10/10** |
| `financial-risk-browser.integration.mjs` | Title passes; severity badge times out | Broken synthetic wire DTO: it supplied the frontend display model. Use Java `version/evaluatedAt`, `highestSeverity/totalAssessedCategories`, insight `id/title/summary/completeness`, evidence `referenceId/eventDate`, category `categoryName/reason` | **12/12** |

The strict aggregate remains **RED**. Original failure diagnostics, log SHA-256
hashes, result counts and the DEF-08 request/response/display evidence are retained
in [historical-browser-results.json](evidence/historical-browser-results.json)
and [historical-browser-def08.json](evidence/historical-browser-def08.json).
Original report [CANDIDATE_V2_VERIFICATION.md](CANDIDATE_V2_VERIFICATION.md) is untouched.
Raw local logs/screenshots remain in ignored `tmp/historical-original/`,
`tmp/historical-repaired-*`, `tmp/historical-final/`,
`tmp/historical-phase-final/` and `tmp/historical-risk-final/`.

## Coverage retained and current contract differences

- Public/private separation, no UUID identity in browser storage, rejected login,
  cookie/CSRF transport, MFA, expired session, invitation acceptance, role controls,
  tenant-free write payloads, business switching, CSP, EN/UR and mobile assertions remain.
- Monthly source evidence is read-only. Financing saves once, sends null unknown
  interest and an expected version, survives reload, updates with version 0, and
  does not mutate financial observations. VIEWER gets no financing/recalculation
  controls. Fixture-level tests also reject viewer/stale writes and retired total writes;
  these tests do not certify backend authorization or projection calculations.
- The dashboard's nullable cash history/summary remains neutral when net is
  unknown. Mandatory monthly observations stay valid. No malformed response is
  used to claim a production accounting defect. The historical history table does
  subtract its mandatory amounts directly; invalid-null input hardening is not a
  proven current API-path defect and was not changed.
- Current empty-business dashboards can show the financial-overview widget;
  assert the absent health score is never fabricated, rather than requiring the
  retired empty-only layout. Unimplemented financial-summary endpoints in these
  narrowly scoped synthetic fixtures deliberately render unavailable evidence;
  they do not establish provider/financial-query correctness.
- Disabled document UI routes now show the module guard (DEF-04), rather than
  rendering history inside that route. The test separately checks the retained
  authorized history fixture and absence of upload controls. The synthetic API
  response is not evidence of real PostgreSQL retention/authorization.
- `/ask-finsight` now renders `CopilotChat`, not the legacy explanation chat.
  Current source identifiers/values retain exact decimals, unknown readiness and
  a labelled planned budget. The old rich `ai-explanation-v1` snapshot/plan chips,
  simulated-GPT banner and terminal conversation-limit UI are not this journey.
  Copilot uses a rolling **8-turn / 1000-character** request history; current rate
  limiting returns a reason-coded answer, rather than the old chat's HTTP 429.
  The rate-limit question stays visible in the conversation. These contract
  differences do not assert closure of legacy UI/product policy questions.
- Fixture registry/catalog now advertises implemented inventory/stock, ledger,
  planning and reporting widgets with correct dependencies. Their extra cards
  remain hidden preferences in these chosen fixture layouts, not falsely
  marked unimplemented. Existing required-core and owner-only settings tests remain.
- OCR original extraction and confidence survive append-only corrections. Evidence
  confirmation still requires explicit review, exactly one matching payload,
  persisted confirmation and no repeated confirm button; monthly observations and
  monthly POST counts remain unchanged. Zakat unknown/null and timestamp assertions remain.
- Risk assertions retain all four insights, exact exposures, source references,
  categories and missing-data semantics. Added exact critical/high/medium counts,
  negative evidence amount/date, recommendation destination, actual category
  exclusion, and required Urdu interaction (removed the old conditional fake-pass
  claiming a component test when the language button was absent). The real adapter
  parses this same wire fixture in a unit regression and rejects display-model aliases.

One harness race was exposed during the full run: locale UI changed before its
PATCH finished, and an immediate reload restored Urdu while the driver expected
English Sign out. The test now waits for the actual successful language response
and LTR before reloading; no timeout widening, retries or assertion suppression.
The changed phase-two driver was then rerun and reached the genuine provenance failure.

## Genuine current application failure — P1 DEF-08

Stop the **application fix** here; no production code was edited.
This is evidence for the existing provider-disclosure DEF ID, not a speculative
new accounting or backend defect.

1. A disposable `/copilot/status` fixture reports `mode: openai`.
2. Submit one question. The contract-valid `copilot-v1` response reports
   `mode: deterministic`, `reasonCode: provider_unavailable`, `model: null`,
   and evidence-backed fallback text after successful read-only tools.
3. The answer renders under **AI assistant**, with no deterministic/fallback/provider
   failure disclosure. The answer text contains no such disclosure itself.
4. The hard assertion fails with `DEF-08`; response and rendered text are saved.

Source corroboration: `CopilotService.run` can return this response after provider
failure; `OperationsViews.tsx` derives its badge from **status**, and renders only
three reason codes, omitting `provider_unavailable` and per-answer `mode`.
The fixture does not call or enable OpenAI. Real provider outage/credentials and
all other provider-reason combinations remain unverified here. The test keeps
the provenance requirement; it does not label this failure expected-green.

## Reproducible commands and CI

From a clean clone of this test branch, using supported Node 22 and the declared
pnpm version (PowerShell uses `$env:NAME='value'` for the three environment variables):

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm exec playwright install --with-deps chromium
pnpm test
pnpm typecheck
pnpm lint
export NEXT_PUBLIC_DATA_MODE=live
export BACKEND_API_URL=http://127.0.0.1:18080
export FRONTEND_TEST_ORIGIN=http://127.0.0.1:13100
pnpm build:release
node tests/run-historical-browser-reconciliation.mjs
```

The fixture backend defaults to port 18080; override
`FINSIGHT_BROWSER_BACKEND_PORT` and **rebuild** with the matching backend URL if
needed. The runner refuses occupied fixture/frontend ports, non-loopback origins,
a mismatched build rewrite, and filtered `LANDING_ONLY` runs. It starts/stops its
own Next child, runs every selected driver sequentially and fails on any failure.
All five drivers resolve the declared local Playwright package by default and
use its installed Chromium. An explicit `FINSIGHT_BROWSER_CHANNEL=msedge` is optional,
not required. Risk now uses the same runner-owned frontend as the other drivers.
No listener on port 8080 was contacted or stopped.

Local validation: **468/468 frontend tests, zero skipped; TypeScript passed;
ESLint zero errors / 51 pre-existing application warnings; production webpack
build passed**. Host: Node 26.13.0 (within the declared engine range), pnpm 10.28.2,
Playwright 1.62.0. No application rebuild was needed for later test-only edits.
Build logs and test logs are retained under ignored `tmp/`.

Separate workflow: **Historical browser reconciliation / historical-browser**
in `.github/workflows/historical-browser.yml`, triggered by this test branch or
manual dispatch. It uses Node 22, frozen pnpm lock, declared Chromium, all frontend
checks and the five-driver strict aggregate; logs/results/screenshots persist
for 90 days on failure. No continue-on-error, automatic retry or skipped DEF-08.
Hosted status and the final published SHA must be checked on GitHub; local
success is not represented as hosted success. Existing v2 release CI is unchanged.

## Unverified and handoff

All **five assigned drivers** were exercised. Phase-two's 19 independent checks
complete before its final failing provenance check; the suite is not green.
The others are green with their current fixtures. Older standalone commercial,
FX, commission, project, funding, accounts-live, unified-live and the eleven
`*BrowserAcceptance` wrappers were **not rerun** in this assignment. The separate
cash-forecast historical driver was not rerun (v2's prior 11 checks are prior evidence).
Real Spring/PostgreSQL regression, Docker/PG17, recovery/operator drills, live
OCR/OpenAI/Meta/SMTP, VPS/TLS and deployment remain outside this fixture-only work.
No claim is made about historical data recovery, live accounting, real tenant
isolation or role enforcement beyond the frozen candidate's prior evidence.

Independent QA can retest the exact published test branch. DEF-08 needs its
application owner and review before closure. No production change, V53 proposal,
DEF-22 reopening, merge into candidate v2/main or deployment is authorized here.
