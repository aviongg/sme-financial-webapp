# Frontend and OCR verification audit

Audit date: 10 October 2026 (Asia/Karachi). Scope: current `frontend/` source, its test/build configuration, and the current `ai-service/` test suite. Historical copies under `tmp/` were not treated as application source. One pre-existing Python environment under `tmp/` supplied dependencies for current-source tests; this distinction matters.

This is an audit, not an implementation claim. Proposed Phase 2 work is in [EXPERIENCE_PACKAGES.md](../features/EXPERIENCE_PACKAGES.md). The root audit records branch/commit identities and backend/database evidence.

## 1. Actual verification and reproducible commands

| Check | Current result | Evidence boundary |
| --- | --- | --- |
| API transport/helper tests | **36 passed, 0 failed, 0 skipped** | `frontend/tests/phase-one.test.mjs`; actual TS modules transpiled with TypeScript, mocked `fetch`; not Spring/PostgreSQL or browser rendering |
| TypeScript | **Pass**, exit 0 | `node node_modules/typescript/bin/tsc --noEmit --incremental false` from `frontend` |
| ESLint | **Pass with one warning**, 0 errors | `node node_modules/eslint/bin/eslint.js`; `SessionProvider.tsx:204`, cleanup reads `revision.current`, `react-hooks/exhaustive-deps` |
| Production build | **Pass after environment recovery**, exit 0 | Initial sandbox run could not fetch Inter, Noto Nastaliq Urdu and Plus Jakarta Sans. Frozen install restored Next 16.3.8; build with authorized font network access succeeded in live mode with BACKEND_API_URL=http://127.0.0.1:8080. Existing middleware-to-proxy deprecation remains |
| Dependency fidelity | **Restored to committed frozen lockfile** | Initial install was Next 16.3.5 versus declared 16.3.8. Final pnpm frozen install succeeded (356 packages), no source/lockfile edits. All 36 tests, TypeScript and lint rerun successfully on Next 16.3.8 |
| Browser integration | **22 + 10 passed, zero browser/CSP/hydration errors** | Custom `browser.integration.mjs` and `browser-extra.integration.mjs` exist; require a successful current Next build, Edge/Playwright, frontend 3100 and synthetic API fixture 8080. Build prerequisite subsequently passed; suites run against current production build |
| OCR first attempt | **Collection blocked** | Current `ai-service/.venv` is missing `python-multipart`; full `pytest -q` fails importing `app.main`. It is declared in `pyproject.toml` and pinned in `requirements-tested.txt` |
| OCR independent subset | **209 passed** | Current local environment: `test_config.py`, `test_documents.py`, `test_parser.py`, `test_vision_provider.py`; this is not an additional suite to add to the full count |
| OCR complete suite | **248 passed, 2 warnings** | Current-source `ai-service` working directory, interpreter from pre-existing `tmp/main-final-integration/ai-service/.venv`; Starlette/httpx and AnyIO alias deprecations. Test fixtures block external provider calls |
| Real database, deployed stack, SMTP/OCR/WhatsApp providers | **Not verified by these commands** | Must be evaluated separately; no production service/provider was invoked |

The normal documented frontend commands are `pnpm test`, `pnpm exec tsc --noEmit`, `pnpm run lint`, `pnpm run build`. Shell `npm`/`npx` were unavailable, so direct installed Node CLIs were used without modifying source or lockfiles:

```powershell
# Working directory: repository/frontend
node --test tests/phase-one.test.mjs
node node_modules/typescript/bin/tsc --noEmit --incremental false
node node_modules/eslint/bin/eslint.js
node node_modules/next/dist/bin/next build

# Working directory: repository/ai-service
& .venv/Scripts/python.exe -m pytest -q
& .venv/Scripts/python.exe -m pytest -q tests/test_config.py tests/test_documents.py tests/test_parser.py tests/test_vision_provider.py
# Dependency-environment fallback, still testing current ai-service source:
& ../tmp/main-final-integration/ai-service/.venv/Scripts/python.exe -m pytest -q
```

Machine-specific bundled Node was discovered at `C:/Users/Hp/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe`. The bundled Python alone lacks pytest. These paths are diagnostic evidence, not portable development requirements. Do not depend on temporary historical checkouts for normal setup: provision the documented project environment from committed dependency files.

`frontend/README.md` records 36 API checks and 22 + 10 browser assertions on 8 October 2026. Those reported 8 October results are historical; current rerun evidence is recorded separately in IMPLEMENTATION_STATE. The browser scripts run against their own synthetic HTTP handlers and cannot establish PostgreSQL or live-provider correctness even when they pass.

## 2. Stack, routes and navigation

Next App Router, React 19, TypeScript and Tailwind 4 are configured in `frontend/package.json`, `tsconfig.json`, `postcss.config.mjs`, `eslint.config.mjs` and `next.config.ts`. Shared styles are `src/app/globals.css` and `src/styles/tokens.css`. Both npm and pnpm lockfiles exist; the development workflow must choose and verify the canonical installer before upgrading dependencies. No `.github/workflows` directory was present in the inspected root; documented commands/custom scripts are not an automated CI pipeline.

All paths below are relative to `frontend/src/`.

| Route | Live entry point and responsibility | Important qualification |
| --- | --- | --- |
| `/` | `app/page.tsx` -> `components/integration/LiveDashboard.tsx` | Fixed dashboard composed from server score/advice/projection and saved records; no preset engine |
| `/onboarding` | `app/onboarding/page.tsx` -> `LiveOnboardingPage.tsx` alias -> `auth/BusinessSetup.tsx` | Live business creation/selection plus invitations; full multi-step onboarding is demo presentation |
| `/records` | `app/records/page.tsx` | Live monthly list; descending month; responsive scrolling table and writer-only add/edit actions |
| `/records/new` | `app/records/new/page.tsx` -> `records/MonthlyRecordForm.tsx` | Snapshot entry; no transaction posting form |
| `/records/[id]` | `app/records/[id]/page.tsx` -> same form | Live `[id]` is a validated `YYYY-MM`; demo uses mock record identifier. Month is locked for live edits |
| `/health/components` | `LiveHealth.tsx`, `ScoreEvidence.tsx`, `RecommendationActions.tsx` | Saved score history, selected month, evidence, existing recommendation lifecycle |
| `/scan` | `app/scan/page.tsx` -> `LiveDocuments.tsx` | Live path is file upload/list; demo scanning appearance is not proof of live camera capture |
| `/upload` | `LiveDocuments.tsx` | File upload, queue/status, polling and links |
| `/upload/[id]` | `LiveDocumentReview.tsx` | Protected original, machine extraction, reviewed draft, correction history and explicit financial confirmation |
| `/search` | `LiveSearch.tsx` | Typed search and fixed destination mapping; no natural-language financial query tool |
| `/settings` | `LiveSettings.tsx`, `TeamAccess.tsx`, `LanguageControl.tsx` | Business switch/name, language, WhatsApp consent, team/invitations and password |
| `/sharia-zakat` | `LiveZakat.tsx` | Manual/monthly backend preview with declarations, missing-state display and price provenance |
| `/reset-password` | `IntegrationBoundary.tsx` -> `AuthScreen.tsx` | Reset token read from URL fragment and fragment immediately removed |

`components/layout/AppShell.tsx` composes `Sidebar`, `PageHeader`, `MobileNav`, content container and mobile bottom spacing. `lib/api/navigation.ts` defines presentation permissions: records/new -> RECORD_CREATE_UPDATE; scan -> DOCUMENT_UPLOAD; upload -> DOCUMENT_READ; Zakat -> ZAKAT_READ_CALCULATE; settings unrestricted within an authenticated business; ordinary financial routes -> FINANCIAL_DATA_READ. Sidebar and mobile actions filter/disable accordingly. These client checks do not replace server authorization.

Desktop navigation enumerates all current routes. Mobile has home, records, health, settings and a central action sheet; additional search/documents/Zakat links are in mobile settings. Sidebar definitions and mobile definitions are separate arrays today. F02 should introduce one module-aware route registry consumed by both, preserving these permissions.

## 3. Live versus demo boundary

`lib/api/config.ts` enables demo only when `NEXT_PUBLIC_DATA_MODE === "demo"`; live is default. `IntegrationBoundary.tsx` shows an explicit sample-data banner in demo mode. Many route files select `DemoPage` or a live component; records share a component with an explicit mode branch. `lib/api/adapter.ts` contains demo operations, not the live transport. No live-error-to-mock fallback was found in the inspected transport and integrated components; the network-failure test asserts rejection.

`types/financial.ts` explicitly contains presentation/demo types, including older optional `userId`, snake-case score fields and percentage completeness. `lib/api/contracts.ts`, `finance.ts`, `documents.ts`, `team.ts`, `zakat.ts`, and `session.ts` are the actual current wire contracts. Do not introduce Phase 2 DTOs by copying demo models. `score-presentation.ts` converts server bands and completeness for existing visual components without recalculating score weights.

## 4. Session and transport reuse

`auth/SessionProvider.tsx` manages loading, anonymous, challenge, authenticated and error states. It supports login, refresh, logout, business selection and creation. It obtains identity from `/api/auth/me`, `/api/businesses`, `/api/businesses/active`; it removes obsolete localStorage identity selectors. Business transitions invalidate and abort requests before loading new context, increment an epoch and remount the private subtree. Revision guards reject late responses. BroadcastChannel coordinates transitions across tabs; focus revalidation preserves forms when context is unchanged; persisted page restoration refreshes context.

`IntegrationBoundary.tsx` blocks private rendering until fully authenticated and business context is established. `AuthScreen.tsx` handles registration/login/reset, forced password change, MFA challenge/recovery/enrollment and one-time recovery-code display. It clears password/code inputs after actions. This is the existing authentication UX to extend, not replace.

`lib/api/client.ts` accepts only local API paths and prefixes `/api`. Every request uses same-origin credentials, no-store and redirect errors. Unsafe calls bootstrap one shared CSRF promise at `/api/auth/csrf`, require `X-XSRF-TOKEN`, preserve browser multipart boundaries and are never automatically replayed. Password-reset routes are explicitly exempt. It propagates status, safe error code, string field errors and Retry-After. A 403 clears the CSRF cache for the next user action; old-context/aborted responses cannot mutate the current session. Active-business/401 failures close private access.

`next.config.ts` rewrites `/api/:path*` to server-only `BACKEND_API_URL` (default localhost:8080); production ingress can route directly. `middleware.ts` sets nonce CSP, no-store, nosniff and same-origin referrer policy. `TRUST_INGRESS_NONCE=true` is intended only behind ingress that overwrites the nonce. The middleware deprecation needs a future controlled framework migration, not an audit-time rename.

Role definitions mirror current backend roles OWNER, ACCOUNTANT, MANAGER, VIEWER. Owners manage business, memberships and WhatsApp; accountants can write records and confirm/edit/delete documents; managers can read finance, upload/read documents and use Zakat; viewers read financial data only. F10's salesperson role is absent and remains A-owned. Every future widget/query must consume the resulting server scope, not infer unrestricted finance from visibility.

## 5. Live user workflows and reusable contracts

**Business setup.** `BusinessSetup.tsx` creates a name and one of trade/manufacturing/services/retail, language, optional payment timing/registration declarations, WhatsApp consent and E.164 phone. It supports selecting active memberships and responding to invitations. Existing business type is read-only in settings; module activation, operating answers, dependency resolution, project-oriented profile and terminology overrides are absent. Extend this model additively for F02; do not reclassify old businesses silently.

**Monthly input.** `MonthlyRecordForm.tsx`, `CoreVitalsSection.tsx`, `PrecisionBoostersSection.tsx`, `MonthSelector.tsx`, `CurrencyInput.tsx` supply validation, five required nonnegative fields (zero allowed) and six optional numeric fields (blank remains null), financing type, save lock, large-outflow review and field errors. New month creation first checks for an existing month and links to review/edit; underlying POST is an upsert, so this client check is not a concurrency guarantee. `phase-one.ts` allowlists transmitted fields and strips identities. Live edits preserve original month. Phase 2 must consume A's source-mode/locked-derived-month contract before disabling manual edits; a new ledger UI must not overwrite derived monthly fields.

**Dashboard.** `LiveDashboard.tsx` makes two parallel calls: dashboard and monthly records. Existing reusable cards include HealthScoreHero, HealthComponents, ScoreCompleteness, ScoreEvidence, NextStep and RecommendationActions. It requires advice businessId/month/sourceComputedAt to match the saved score. CashFlowTrend renders recorded months with real calendar gaps, exact-record disclosure, period selection and localized money. It currently sums inflow/outflow client-side for display; it is not a ledger cash-balance authority. Projection comes from the backend. There is no widget registry, layout persistence, four-preset library, resizing/reordering or workspace batch contract yet.

**Health/action reuse.** `LiveHealth.tsx` reads `/scores/history`; selected month loads score, insights and recommendations, and hides mismatched advice. Evidence displays saved methodology, calculated/self-declared basis, missing components, available history and movement. `RecommendationActions.tsx` uses existing NEW/VIEWED/DONE/DISMISSED transitions. Extend these in a future action center; never clone score storage or let an LLM generate score values.

**Documents.** `LiveDocuments.tsx` permits PDF/JPEG/PNG selection and a 10 MiB client check, submits multipart, polls pending/processing every five seconds only when visible and routes to the returned document UUID. `LiveDocumentReview.tsx` distinguishes original OCR and reviewed data, preserves confidence, displays correction audit history, edits extracted/needs_review drafts, supports failed retry and deletes only unconfirmed drafts. Confirmation requires explicit reviewed amount, month, optional date/party, independent revenue/expense/COGS classification and cash-flow impact, with initial month balance if required. `documents.ts` constructs UUID-only protected file paths and ignores arbitrary stored file URLs. F12B must preserve this lifecycle; an invoice-shaped document is not an invoice entity, obligation or ledger posting.

**Settings/team.** `LiveSettings.tsx` loads profile and WhatsApp delivery history, supports owner rename, role display, business switch, password change, language and owner-only consent/phone. `TeamAccess.tsx` invites an existing-account email, changes ACCOUNTANT/MANAGER/VIEWER roles, suspends/reactivates/removes nonowners and accepts/declines invitations. No owner assignment is available in this UI. `team.ts` sends explicit input fields with UUID references and trims email/name. Scheduled WhatsApp delivery history exists; inbound financial conversations/copilot do not.

**Search/Zakat.** `LiveSearch.tsx` cancels prior requests, filters supported types and uses `searchDestination` to disregard server-supplied arbitrary href. The current `transaction` label resolves monthly records, not Phase 2 transaction entities. `LiveZakat.tsx` sends explicit assessed date, silver-price source/timestamp, haul and asset/liability declarations to backend preview endpoints. Null result means undetermined, not zero. It hardcodes PKR and +05:00 price timestamp construction today; any FX/timezone expansion needs the appropriate backend policy, not UI arithmetic.

## 6. Internationalization, accessibility and responsive evidence

`lib/i18n/context.tsx` selects typed `en.ts`/`ur.ts` dictionaries, persists interface locale under `finsight_locale`, updates document lang/dir and synchronizes storage events. `translations/live.ts`, `labels.ts` and `errors.ts` cover integrated feature copy and safe error translation. Initial server document is English/LTR before hydration. `LanguageControl.tsx` updates business language for owners and local interface language for other roles, then refreshes session. It does not itself initialize the local interface from a newly selected business's stored language; decide and test desired precedence in F02/C12.

`formatPKR` preserves Western digits and emits PKR or Urdu rupees; null/NaN becomes an em dash. `FinancialValue` and `CurrencyInput` are reusable visual/input primitives but existing values are JavaScript numbers. Future decimal-string/currency DTOs need an additive formatter/input adapter, not silent Number conversion or changing legacy contracts. Chart chronology remains LTR in Urdu; bidirectional fragments use `bdi`/`dir=ltr` in several live views. Layout changes sidebar side for RTL, uses logical padding/text-start utilities and responsive grids.

Source confirms `md` desktop sidebar versus mobile bottom nav, safe-area padding, wrapping action groups, max-width containers and horizontal table/chart scrolling. Current synthetic-browser suite exercises mobile/Urdu layout against the rebuilt frontend. This does not substitute for each new feature's actual-backend desktop/mobile EN/UR/RTL acceptance.

Concrete maintainability/accessibility findings to address during relevant slices:

1. `Dialog.tsx`/`Sheet.tsx` implement Escape/backdrop close and scroll locking but no explicit focus trap, initial focus or focus restoration; Dialog uses fixed title IDs, and Sheet lacks title association. Add accessible focus behavior before expanding their use for dashboard customization and financial confirmation.
2. Some shared UI labels remain English, e.g. sidebar/navigation ARIA labels, close-dialog/close-sheet controls and MonthlyRecordForm read-only message. C12 should require translated visible and assistive copy.
3. Sidebar collapsed width changes locally to 72px while AppShell desktop content margin remains 256px. Share layout state if collapse behavior is retained in F02/F13.
4. Navigation arrays, page-specific API forms and compressed JSX make some integrated screens harder to extend. Extract module registry and cohesive feature components when implementing those features; avoid a broad rewrite of stable auth/transport.
5. `LiveDocuments`, document review and Zakat initialize hooks before permission-based render returns. Server denies unauthorized data; future pages should also avoid unnecessary forbidden requests when scope is known.
6. Current DTO success bodies are TypeScript casts, not comprehensive runtime schema validation. Add bounded validation at new v2/AI contracts, preserving legacy behavior until independently tested.

## 7. Loading, failure and missing-data semantics

Reusable `useLiveResource` supplies abort-on-unmount, stale-result guard, localized failure and retry; `LivePage` supplies role=status/alert and retry shell. Records have geometric skeletons and explicit not-found/profile/error/empty views; generic app `loading.tsx`, `error.tsx`, `not-found.tsx` exist. UI primitives include Button, Input, CurrencyInput, Select, Checkbox, Switch, Card, Badge, StatusBadge, AlertBanner, Container, Section, Divider, Skeleton, EmptyState, ErrorState, Toast, Tooltip, Dialog and Sheet.

Live dashboards preserve absent score/advice/projection; optional score components stay null and missing history is not manufactured. Search differentiates not searched, empty result and failure. Documents distinguish pending/processing/failed and retain reviewed/original provenance. Most mutation paths disable duplicate submission and show localized errors. Use these states as the minimum Phase 2 pattern; future widget responses additionally need unavailable/restricted/stale/partial/source-mode states with evidence and no financial zero fallback.

## 8. Phase 2 reuse and prerequisites

| Package | Reuse | Missing foundation |
| --- | --- | --- |
| F02 (B) | BusinessSetup, LiveSettings, SessionProvider, business/profile DTOs, navigation permissions, i18n context | C03 operating configuration/module resolver, backend enforcement, dependency graph, safe legacy defaults, shared route registry, onboarding preset preview |
| F13 (B) | LiveDashboard and existing score/cash/evidence/action cards, LivePage, Card/Container and responsive tokens | C10 allowed-widget registry, batch workspace, four presets, per-business/user overrides/versioning, keyboard customization and authorization precedence |
| F12B (B) | Documents API/live review/corrections, protected retrieval, recommendation/history display | Calendar event adapters, typed entity links, export/report services and permissions, scheduling/idempotency, source-mode document dispatch via A-owned posting |
| B intelligence | Saved score/history/advice, existing status transitions, existing dashboard DTO | Budgets/goals/break-even and attribution-aware analysis services, action aggregation, evidence-grounded AI/provider tools and read-only inbound WhatsApp |

The existing `dashboard/service/DashboardService.java`, `profile/service/BusinessProfileService.java`, identity authorization, score/cashflow services and current DTOs are extension points, not proof that Phase 2 modules exist. Proposed `/api/v2` decimal-string contracts must coexist with numeric legacy `/api` DTOs and use the same session/CSRF transport. C03/C10/C12 are B-owned; C11 is A-owned; C08 commission and salesperson rules remain A-owned.

Before a future feature is marked complete: verify exact dependencies and a successful current build, run its real backend/PostgreSQL flow, add meaningful contract and browser coverage, check role/business switching and suspended membership, test EN/UR/mobile and missing-data states, and record deployed/provider gates separately. A mocked widget, historical build or doc assertion is not completion evidence.
