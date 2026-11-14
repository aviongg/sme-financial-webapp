# FinSight frontend

Current integrated V52 build/CI instructions: [release verification](../docs/release/PRODUCTION_CLOSURE.md).
Use Node 22 and pinned pnpm 10.28.2 with `pnpm install --frozen-lockfile`; pnpm is
the sole lockfile authority for CI and Docker.

This is the live Next.js frontend for the integrated V52 FinSight application. The production-closure baseline is the exact published commit recorded in the release verification document; it is not inferred from `main`. Live mode uses authenticated sessions, never a browser-supplied profile UUID.

The earlier UI and security integration history remains documented in the [consolidation record](../docs/CONSOLIDATED_APP_BRANCH.md). Historical development branches remain available and were left untouched by final integration. See the [main integration handoff](../docs/MAIN_INTEGRATION_HANDOFF.md) for exact source revisions, merge status and current validation evidence.

## Run locally

Use Node 22, pnpm 10.28.2 and the backend's documented PostgreSQL setup. Do not reuse production data for acceptance tests.

```powershell
cd frontend
pnpm install --frozen-lockfile
Copy-Item .env.example .env.local
pnpm dev
```

The pnpm workspace explicitly disables the optional `unrs-resolver` install script; the locked platform package supplies the resolver.

Open http://localhost:3000. Register, then sign in. Complete any required password change or MFA step, and create/select a business. Existing users sign in with their account; a profile UUID cannot open a session.

`BACKEND_API_URL` is the server-only Spring origin (default `http://localhost:8080`, without `/api`). Browser calls remain same-origin at `/api/*`. Set the target before building because Next rewrites are baked into the build. Production Nginx routes `/api/` directly to Spring.

`NEXT_PUBLIC_DATA_MODE=live` is the default. `demo` is an explicit build-time preview with a visible sample-data banner; live failures never fall back to sample data. Do not distribute a demo build as the live product. Google Fonts access is needed for the existing `next/font/google` build.

## Integrated behavior

- Cookie sessions, CSRF bootstrap, registration/sign-in, forced password change, MFA challenge/recovery/mandatory enrollment, reset-link confirmation and sign-out.
- Business creation/selection and role-aware navigation/actions. Business transitions discard private data and cancel old requests across tabs. Authorization is always enforced by the backend too.
- Monthly records derive from daily financial activity, with source maps and explicit recalculation. Manual monthly totals are retired; supplemental financing is separate. Zero and unknown values remain distinct. No caller-supplied user/business identity in finance payloads.
- Dashboard and historical health scores/advice use canonical backend fields and nullable values. Cash-flow disclosure preserves real calendar gaps and shows exact records; projection comes from the backend.
- Score history, methodology version, structured component evidence and recommendation actions (`NEW`, `VIEWED`, `DONE`, `DISMISSED`) use persisted backend values. Score formulas, weights and bands remain server-owned and unchanged.
- Search maps supported result types to fixed local destinations. Settings covers language, WhatsApp consent/phone, delivery history and password changes.
- Documents support upload/list/status, protected original retrieval, draft correction, review, explicit one-time confirmation, retry and draft deletion. Extraction never automatically adds financial data.
- Team settings supports existing-account invitations, acceptance/decline, role changes, suspension/reactivation and removal. Business names are authoritative and owner-editable; membership and financial permissions remain server-enforced.
- Zakat uses backend manual/monthly previews and explicit assessment inputs, including price provenance, haul, inventory, receivables and identified liabilities. Missing information stays unknown; no fixed market price or invented assessment is supplied.

The English/Urdu interface and RTL layout include typed shared copy for authentication, onboarding, score evidence, search, documents, team, settings and Zakat. Financial numerals remain Western digits. Desktop/mobile and Urdu browser checks are included below.

## Security and production configuration

API calls use same-origin cookies, no-store requests and CSRF headers. No authentication token or profile identity is stored in localStorage. Unsafe operations are not automatically retried. Expired sessions and unresolved business context close private views; responses from an old context are discarded.

Production CSP uses a nonce and omits `unsafe-eval`. By default Next generates its own nonce and ignores caller `x-nonce`. Only the private production Compose service enables `TRUST_INGRESS_NONCE=true`, because Nginx overwrites the header and supplies the matching edge CSP. Do not enable this on a directly accessible Next server.

The frontend container uses Next standalone output, runs `node server.js` as UID 10001, and includes static/public assets. Run the matched integrated backend with Flyway V1–V52. Container configuration is source-reviewed; a complete production Docker/TLS deployment and provider acceptance remain separate gates in the [production runbook](../docs/PRODUCTION_RUNBOOK.md).

## Verification

```powershell
pnpm test
pnpm exec tsc --noEmit
pnpm run lint
pnpm build:release
```

`tests/phase-one.test.mjs` tests the actual API modules: cookie/CSRF requests, explicit payloads, errors, cancellation/races, roles, document confirmation and Zakat contracts.

The supported release browser gate is `pnpm test:browser:smoke` after the Maven classpath export, production build and `pnpm browser:install` in [release verification](../docs/release/PRODUCTION_CLOSURE.md). It starts actual Spring/disposable PostgreSQL and production Next with mock AI. `pnpm test:browser:product` runs the additional product scenarios.

Legacy `tests/browser.integration.mjs` and `tests/browser-extra.integration.mjs` remain synthetic HTTP contract fixtures, not Spring/PostgreSQL. They use system Edge and their own API on 8080 with a separately started Next on 3100. Do not run them against an existing backend or confuse their historical counts with release acceptance.

Final integration checks on 8 October 2026: **36 API contract tests**, **22 primary browser assertions** and **10 additional browser assertions** passed. TypeScript, ESLint and the production build passed. ESLint retains one existing SessionProvider effect-cleanup ref warning; Next reports its existing middleware-convention deprecation warning. No browser exceptions, CSP violations or hydration errors were reported. The live-component import traversal covered 57 local modules with no `mockApi` references; shared record routes select demo behavior only through the explicit build-time mode.

The [main integration handoff](../docs/MAIN_INTEGRATION_HANDOFF.md) and [earlier frontend handoff](../docs/frontend-security-integration-2026-09-27.md) are historical evidence. Current integrated accounting, derived months and Copilot are described in the product-completion handoff; external providers still require release acceptance. Browser fixtures alone do not establish deployed-stack or provider acceptance.
