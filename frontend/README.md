# FinSight frontend

The existing Next.js interface has been brought from `origin/dev/suleman` (`4202dc1`) into `dev/fatima`. Live API integration currently covers profile creation/reopening, profile language, and monthly record create/list/read/edit. The remaining screens are preserved in explicit demo mode.

**Backend compatibility:** this slice uses the standalone Fatima development API at `e3ccd65` (unchanged in frontend commit `f4b738a`). Suleman's latest `0273286` adds cookie-session authentication, CSRF and active-business APIs, replacing the UUID routes below. The current live frontend cannot be used with that backend until its auth, business selection and API adapters are migrated. Keep the frontend PR in draft pending that integration; main is unchanged.

See [the review and integration workflow](../docs/frontend-phase-1-workflow.md) for backend ownership, unresolved integration issues and the next slices.

## Run locally

Use Node 20.9+ and npm or pnpm. Run the Spring backend with its documented PostgreSQL configuration first. Do not change an existing database's Flyway history without following the backend handoff.

```powershell
cd frontend
npm ci
Copy-Item .env.example .env.local
npm run dev
```

Alternatively, `pnpm install --frozen-lockfile` and `pnpm dev` use the imported pnpm lockfile. Dependency versions are preserved from the original npm lockfile. The pnpm configuration skips the optional resolver install script; its platform package is installed normally.

Open http://localhost:3000/onboarding. Create a profile or reopen a saved UUID, then enter monthly figures. Settings shows the development profile reference; browser storage remembers the selected profile. This is a development selector, not authentication or authorization. Do not expose the application publicly as a multi-user authenticated product.

`BACKEND_API_URL` is the Spring origin, default `http://localhost:8080`, without an `/api` suffix. The Next server proxies same-origin `/api/*` requests to it. Restart the development server after changing the environment. For production, set the target before building because rewrites are included in the build.

`NEXT_PUBLIC_DATA_MODE=live` is the default. Set it to `demo` and restart/rebuild to view the original full dashboard, onboarding wizard, uploads, search and health screens. A banner identifies sample data. Live mode never falls back to demo data when a request fails. Unconnected live routes show an unavailable state.

The existing typography uses `next/font/google`, so production builds need access to Google Fonts. Local development may use fallback fonts without access.

## Implemented API behavior (standalone Fatima backend)

- Profile: `POST /api/profile`, `GET /api/profile/{userId}`.
- Language: `PATCH /api/profile/{userId}/language` with `languagePreference`.
- Records: `GET /api/records/monthly/{userId}` and `GET /api/records/monthly/{userId}/{month}`.
- Both create and edit use `POST /api/records/monthly`: the backend upserts by user and month. Live edit links use the month; edit mode locks it. New-entry mode checks for an existing month and directs the user to review it before replacement. This browser check is not a database concurrency guarantee.
- Required zero values remain zero; unknown optional amounts remain null. The standalone Fatima validator still requires COGS, so its message-only error is displayed at that field. Suleman's hardened records service allows null COGS; no frontend zero substitution is made.
- No sample score is shown after a record save. Scoring, aggregated dashboard, advice, cash-flow API, Zakat and document workflow connection are subsequent slices.

Wire contracts are in `src/lib/api/contracts.ts`. Existing `src/types/financial.ts` describes presentation/demo models, not the complete HTTP contract. Do not cast the canonical scoring/dashboard responses into those legacy models without mapping field names, nullable values and completeness units.

## Teammate UI changes

- The entire WhatsApp opt-in label/card toggles the phone entry field, with native keyboard support.
- Business selection no longer displays a tick.
- Cash-flow summary has a chevron disclosure, time-period selector, chart and exact monthly table. Summary inflow/outflow/net are totals for the selected period; ending cash is the latest recorded balance. Gaps are not converted to zero.
- Add Monthly Record sits above the table at the right; Return to Dashboard sits in the page header.
- Health component weight appears below the score/status.

The records changes apply in both modes. The other screens remain in demo mode until their backend slices are connected.

## Verification

```powershell
npm test
npx tsc --noEmit
npm run lint
npm run build
```

Verification on 24 September 2026:

- 15 API/session contract tests passed, covering zero/null payloads, POST upsert, errors, invalid inputs, explicit demo selection and profile retry identity.
- TypeScript, ESLint and production build passed. Google Fonts required network access for the build.
- 11 direct HTTP checks passed against current Fatima code with temporary embedded PostgreSQL: profile create/read, Urdu/English PATCH, zero/null record persistence, same-month replacement with stable ID, read/list, expected null-COGS 400, and absent-score advice returning empty arrays.
- Browser checks exercised profile creation, real save, inline COGS rejection and correction, reload persistence, edit and Urdu switching through the Next proxy. The current application database was not used.
- Desktop/mobile visual checks covered record controls and Urdu layout. Demo checks confirmed cash-flow expand/filter/table behavior, weight placement, business selection without a tick, and WhatsApp checkbox disclosure with keyboard Space.

The temporary backend and smoke evidence live in ignored `backend/target/frontend-smoke/`. They are local QA infrastructure, not a committed service or production database. The separate backend integration handoff documents the reproducible combined scoring/advice checks.

A later fetch on 24 September found Suleman's new authenticated contract at `0273286`. Its scoring/projection fixes passed 31 targeted upstream unit tests, but the old combined integration harness stopped at its dashboard hash guard before running any suites. These results do not validate the current frontend against that new backend. See the workflow's current-status section for the endpoint migration and acceptance gates.
